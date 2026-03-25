package com.trustmebro.crypto

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object APKParser {

    data class CertInfo(
        val entryName: String,
        val subjectDN: String,
        val issuerDN: String,
        val serialNumber: String,
        val sigAlgorithm: String,
        val validFrom: Date,
        val validUntil: Date,
        val fingerprintSha256: String,
        val isExpired: Boolean
    )

    data class APKAnalysis(
        val certificates: List<CertInfo>,
        val hasV2SigningBlock: Boolean,
        val hasV3SigningBlock: Boolean,
        val errorMessage: String? = null
    )

    fun analyzeApk(context: Context, uri: Uri): APKAnalysis {
        val certificates = mutableListOf<CertInfo>()
        var zipError: String? = null

        // ── 1. Stream through ZIP entries (no full file in RAM) ──────────────
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.startsWith("META-INF/")) {
                            val entryBytes = zip.readBytes() // META-INF entries are tiny (KB)
                            if (name.endsWith(".RSA", ignoreCase = true) ||
                                name.endsWith(".DSA", ignoreCase = true) ||
                                name.endsWith(".EC",  ignoreCase = true)) {
                                val cert = extractCertFromPkcs7(entryBytes)
                                if (cert != null) certificates.add(buildCertInfo(name, cert))
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return APKAnalysis(emptyList(), false, false, "Impossible d'ouvrir l'APK")
        } catch (e: Exception) {
            zipError = "Erreur ZIP: ${e.message}"
        }

        // ── 2. Detect signing block — read only file tail via FileChannel ─────
        val (hasV2, hasV3) = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    detectSigningBlock(channel)
                }
            } ?: (false to false)
        } catch (_: Exception) { false to false }

        return APKAnalysis(certificates, hasV2, hasV3, zipError)
    }

    // ── Signing block detection (reads only last ~65KB + signing block area) ──

    private fun detectSigningBlock(channel: java.nio.channels.FileChannel): Pair<Boolean, Boolean> {
        val fileSize = channel.size()
        if (fileSize < 22) return false to false

        // Read last 65557 bytes to find EOCD
        val searchSize = minOf(65557L + 22, fileSize).toInt()
        val tailBuf = ByteBuffer.allocate(searchSize).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(fileSize - searchSize)
        channel.read(tailBuf)
        tailBuf.flip()
        val tail = ByteArray(tailBuf.remaining()).also { tailBuf.get(it) }

        // Find EOCD signature (0x06054b50) scanning from the end
        val eocdSig = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
        var eocdInTail = -1
        for (i in tail.size - 22 downTo 0) {
            if (tail[i] == eocdSig[0] && tail[i+1] == eocdSig[1] &&
                tail[i+2] == eocdSig[2] && tail[i+3] == eocdSig[3]) {
                eocdInTail = i
                break
            }
        }
        if (eocdInTail < 0) return false to false

        // CD offset is at EOCD+16 (4 bytes LE)
        val cdOffset = readInt32LE(tail, eocdInTail + 16).toLong() and 0xFFFFFFFFL
        if (cdOffset <= 0 || cdOffset >= fileSize) return false to false

        // Magic "APK Sig Block 42" is 16 bytes just before the CD
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        val magicFileOffset = cdOffset - magic.size
        if (magicFileOffset < 0) return false to false

        // Read the 16 bytes at magicFileOffset
        val magicBuf = ByteBuffer.allocate(magic.size)
        channel.position(magicFileOffset)
        channel.read(magicBuf)
        magicBuf.flip()
        val foundMagic = ByteArray(magic.size).also { magicBuf.get(it) }
        if (!foundMagic.contentEquals(magic)) return false to false

        // Read block size (8 bytes LE) just before magic
        val blockSizeOffset = magicFileOffset - 8
        if (blockSizeOffset < 0) return true to false

        val sizeBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(blockSizeOffset)
        channel.read(sizeBuf)
        sizeBuf.flip()
        val blockSize = sizeBuf.getLong()

        val blockStart = cdOffset - 8 - blockSize - 8
        if (blockStart < 0 || blockSize > 32 * 1024 * 1024) return true to false

        // Read the signing block to check block IDs (v2=0x7109871a, v3=0xf05368c0)
        val blockDataSize = minOf(blockSize.toInt(), 1 * 1024 * 1024) // cap at 1MB
        val blockBuf = ByteBuffer.allocate(blockDataSize).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(blockStart)
        channel.read(blockBuf)
        blockBuf.flip()
        val blockData = ByteArray(blockBuf.remaining()).also { blockBuf.get(it) }

        var hasV2 = false
        var hasV3 = false
        try {
            var pos = 0
            while (pos < blockData.size - 12) {
                val pairSize = readInt64LE(blockData, pos)
                pos += 8
                if (pairSize <= 4 || pos + pairSize > blockData.size) break
                val id = readInt32LE(blockData, pos)
                when (id) {
                    0x7109871a -> hasV2 = true
                    0xf05368c0.toInt() -> hasV3 = true
                }
                pos += pairSize.toInt()
            }
        } catch (_: Exception) { hasV2 = true }

        return hasV2 to hasV3
    }

    // ── Certificate extraction ────────────────────────────────────────────────

    private fun extractCertFromPkcs7(der: ByteArray): X509Certificate? {
        val cf = CertificateFactory.getInstance("X.509")
        return try {
            cf.generateCertificate(ByteArrayInputStream(der)) as? X509Certificate
        } catch (_: Exception) {
            extractCertFromAsn1(der, cf)
        }
    }

    private fun extractCertFromAsn1(data: ByteArray, cf: CertificateFactory): X509Certificate? {
        for (i in 0 until data.size - 4) {
            if (data[i] == 0x30.toByte()) {
                try {
                    val cert = cf.generateCertificate(ByteArrayInputStream(data, i, data.size - i))
                            as? X509Certificate
                    if (cert != null) return cert
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun buildCertInfo(entryName: String, cert: X509Certificate): CertInfo {
        val encoded = cert.encoded
        return CertInfo(
            entryName = entryName,
            subjectDN = cert.subjectX500Principal.name,
            issuerDN = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16).uppercase(),
            sigAlgorithm = cert.sigAlgName,
            validFrom = cert.notBefore,
            validUntil = cert.notAfter,
            fingerprintSha256 = fingerprint(encoded, "SHA-256"),
            isExpired = Date().after(cert.notAfter)
        )
    }

    private fun fingerprint(data: ByteArray, algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(data).joinToString(":") { "%02X".format(it) }

    // ── Low-level readers ─────────────────────────────────────────────────────

    private fun readInt32LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun readInt64LE(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
        ((data[offset + 1].toLong() and 0xFF) shl 8) or
        ((data[offset + 2].toLong() and 0xFF) shl 16) or
        ((data[offset + 3].toLong() and 0xFF) shl 24) or
        ((data[offset + 4].toLong() and 0xFF) shl 32) or
        ((data[offset + 5].toLong() and 0xFF) shl 40) or
        ((data[offset + 6].toLong() and 0xFF) shl 48) or
        ((data[offset + 7].toLong() and 0xFF) shl 56)
}

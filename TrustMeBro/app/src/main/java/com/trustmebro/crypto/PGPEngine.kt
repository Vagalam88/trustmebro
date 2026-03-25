package com.trustmebro.crypto

import android.content.Context
import android.net.Uri
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Date

/**
 * PGP verification engine using Bouncy Castle.
 * Supports cleartext signed messages and detached signatures.
 */
object PGPEngine {

    data class SignerInfo(
        val keyId: String,
        val fingerprint: String,
        val userId: String,
        val algorithm: String,
        val signatureDate: Date?,
        val isValid: Boolean?, // null = no key provided (unverifiable)
        val errorMessage: String? = null
    )

    data class VerificationResult(
        val isValid: Boolean,
        val signers: List<SignerInfo>,
        val errorMessage: String? = null
    )

    /**
     * Verify a cleartext signed message (-----BEGIN PGP SIGNED MESSAGE-----).
     *
     * Bug fix: the previous implementation only read the first line of the body
     * because readInputLine() always returns -1 for LF/CRLF files, so the loop
     * was never entered. We now parse the raw text directly.
     */
    fun verifyCleartextMessage(
        signedMessageBytes: ByteArray,
        publicKeyBytes: ByteArray?
    ): VerificationResult {
        return try {
            val fullText = signedMessageBytes.toString(Charsets.UTF_8)

            // Locate the signature block
            val sigMarker = "-----BEGIN PGP SIGNATURE-----"
            val sigIdx = fullText.indexOf(sigMarker)
                .takeIf { it >= 0 }
                ?: return VerificationResult(false, emptyList(), "Aucune signature PGP trouvée dans le message")

            // Locate body start: blank line after the PGP header lines (Hash: etc.)
            // The structure is: armor header, Hash: line(s), blank line, body content, sig marker
            val headerMarker = "-----BEGIN PGP SIGNED MESSAGE-----"
            val headerEnd = fullText.indexOf(headerMarker)
                .takeIf { it >= 0 }
                ?: return VerificationResult(false, emptyList(), "Format PGP invalide")

            // Find the blank line that separates headers from body
            val afterHeader = fullText.indexOf('\n', headerEnd) + 1
            val bodyStart = findBodyStart(fullText, afterHeader)

            // Raw body: everything between body start and the signature marker
            val rawBody = fullText.substring(bodyStart, sigIdx)

            // Canonicalize per RFC 4880 §7.1:
            // - strip trailing whitespace per line
            // - un-escape dash-escaped lines ("- " prefix)
            // - lines separated by CRLF
            // - body ends with CRLF (GnuPG includes the trailing newline)
            val canonBody = canonicalizeCleartextBody(rawBody)

            // Parse the signature from the armored block
            val sigPart = fullText.substring(sigIdx).toByteArray(Charsets.UTF_8)
            val armoredSigIn = ArmoredInputStream(ByteArrayInputStream(sigPart))
            val pgpFact = PGPObjectFactory(armoredSigIn, BcKeyFingerprintCalculator())
            val sigList = pgpFact.nextObject() as? PGPSignatureList
                ?: return VerificationResult(false, emptyList(), "Impossible de parser la signature PGP")

            val signerInfos = mutableListOf<SignerInfo>()

            for (i in 0 until sigList.size()) {
                val sig = sigList[i]
                val keyId = "%016X".format(sig.keyID)

                if (publicKeyBytes == null) {
                    signerInfos.add(SignerInfo(
                        keyId = keyId,
                        fingerprint = "—",
                        userId = "Clé publique non fournie",
                        algorithm = getAlgorithmName(sig.keyAlgorithm),
                        signatureDate = sig.creationTime,
                        isValid = null,
                        errorMessage = "Ajouter la clé publique pour vérifier"
                    ))
                    continue
                }

                val publicKey = findPublicKey(publicKeyBytes, sig.keyID)
                if (publicKey == null) {
                    signerInfos.add(SignerInfo(
                        keyId = keyId,
                        fingerprint = "—",
                        userId = "Clé 0x$keyId introuvable",
                        algorithm = getAlgorithmName(sig.keyAlgorithm),
                        signatureDate = sig.creationTime,
                        isValid = null,
                        errorMessage = "Ajouter la clé publique du signataire"
                    ))
                    continue
                }

                sig.init(BcPGPContentVerifierBuilderProvider(), publicKey)
                sig.update(canonBody)

                val isValid = try { sig.verify() } catch (e: PGPException) { false }

                signerInfos.add(SignerInfo(
                    keyId = keyId,
                    fingerprint = formatFingerprint(publicKey.fingerprint),
                    userId = getUserId(publicKey),
                    algorithm = getAlgorithmName(sig.keyAlgorithm),
                    signatureDate = sig.creationTime,
                    isValid = isValid,
                    errorMessage = if (isValid == false) "Signature invalide" else null
                ))
            }

            val allValid = signerInfos.isNotEmpty() && signerInfos.all { it.isValid == true }
            VerificationResult(allValid, signerInfos)
        } catch (e: Exception) {
            VerificationResult(false, emptyList(), "Erreur: ${e.message}")
        }
    }

    /**
     * Find the start of the body content (after the blank line following PGP headers).
     */
    private fun findBodyStart(text: String, searchFrom: Int): Int {
        var i = searchFrom
        while (i < text.length) {
            // Look for a blank line (LF+LF or CRLF+CRLF or LF after a line with only whitespace)
            if (text[i] == '\n') {
                val next = i + 1
                if (next < text.length && (text[next] == '\n' || text[next] == '\r')) {
                    // Blank line found — body starts after it
                    return if (text[next] == '\r' && next + 1 < text.length && text[next + 1] == '\n')
                        next + 2 else next + 1
                }
                // Check if the current line (before this \n) has only whitespace → it's a blank line
                var lineStart = i - 1
                while (lineStart >= searchFrom && text[lineStart] != '\n') lineStart--
                val currentLine = text.substring(lineStart + 1, i)
                if (currentLine.isBlank()) return i + 1
            }
            i++
        }
        return searchFrom
    }

    /**
     * Canonicalize cleartext body per RFC 4880 §7.1:
     * - strip trailing whitespace per line
     * - un-escape dash-escaped lines
     * - use CRLF line endings
     */
    private fun canonicalizeCleartextBody(rawBody: String): ByteArray {
        val lines = rawBody.split(Regex("\r\n|\r|\n"))
        // Drop trailing empty lines (artifact of how we split before the sig marker)
        val trimmed = lines.dropLastWhile { it.isBlank() }
        val out = StringBuilder()
        for (line in trimmed) {
            val unescaped = if (line.startsWith("- ")) line.substring(2) else line
            out.append(unescaped.trimEnd())
            out.append("\r\n")
        }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Verify a detached signature against the original data using streaming (no full file in RAM).
     * @param dataStream the original file as InputStream (will be closed by caller)
     * @param signatureBytes the detached signature bytes (.sig, .sign, .asc)
     * @param publicKeyBytes optional PGP public key
     */
    fun verifyDetachedSignature(
        dataStream: InputStream,
        signatureBytes: ByteArray,
        publicKeyBytes: ByteArray?,
        fileSize: Long = -1L,
        onProgress: ((Float) -> Unit)? = null
    ): VerificationResult {
        return try {
            val sigInputStream: InputStream = try {
                PGPUtil.getDecoderStream(ByteArrayInputStream(signatureBytes))
            } catch (e: Exception) {
                ByteArrayInputStream(signatureBytes)
            }

            val pgpFact = PGPObjectFactory(sigInputStream, BcKeyFingerprintCalculator())
            var obj = pgpFact.nextObject()

            if (obj is PGPCompressedData) {
                val compFact = PGPObjectFactory(obj.dataStream, BcKeyFingerprintCalculator())
                obj = compFact.nextObject()
            }

            if (obj is PGPLiteralData) {
                obj = pgpFact.nextObject()
            }

            val sigList = obj as? PGPSignatureList
                ?: return VerificationResult(false, emptyList(), "Aucune signature détachée trouvée")

            // Collect all sigs first (we need to stream data once per sig, but
            // for multiple sigs we buffer — in practice there is always 1 sig)
            val sigs = (0 until sigList.size()).map { sigList[it] }

            // Initialize all signatures before streaming data
            val activeSigs = sigs.mapNotNull { sig ->
                val key = if (publicKeyBytes != null) findPublicKey(publicKeyBytes, sig.keyID) else null
                if (key != null) {
                    sig.init(BcPGPContentVerifierBuilderProvider(), key)
                    Pair(sig, key)
                } else {
                    null
                }
            }

            // Stream the data once, feeding all active signatures
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            while (dataStream.read(buffer).also { bytesRead = it } != -1) {
                for ((sig, _) in activeSigs) {
                    sig.update(buffer, 0, bytesRead)
                }
                totalRead += bytesRead
                if (fileSize > 0) onProgress?.invoke(totalRead.toFloat() / fileSize.toFloat())
            }

            val signerInfos = mutableListOf<SignerInfo>()

            for (sig in sigs) {
                val keyId = "%016X".format(sig.keyID)
                val activePair = activeSigs.find { it.first === sig }

                if (publicKeyBytes == null) {
                    signerInfos.add(SignerInfo(
                        keyId = keyId,
                        fingerprint = "—",
                        userId = "Clé publique non fournie",
                        algorithm = getAlgorithmName(sig.keyAlgorithm),
                        signatureDate = sig.creationTime,
                        isValid = null,
                        errorMessage = "Ajouter la clé publique pour vérifier"
                    ))
                    continue
                }

                if (activePair == null) {
                    signerInfos.add(SignerInfo(
                        keyId = keyId,
                        fingerprint = "—",
                        userId = "Clé 0x$keyId introuvable",
                        algorithm = getAlgorithmName(sig.keyAlgorithm),
                        signatureDate = sig.creationTime,
                        isValid = null,
                        errorMessage = "Ajouter la clé publique du signataire"
                    ))
                    continue
                }

                val (initSig, publicKey) = activePair
                val isValid = try { initSig.verify() } catch (e: PGPException) { false }

                signerInfos.add(SignerInfo(
                    keyId = keyId,
                    fingerprint = formatFingerprint(publicKey.fingerprint),
                    userId = getUserId(publicKey),
                    algorithm = getAlgorithmName(sig.keyAlgorithm),
                    signatureDate = sig.creationTime,
                    isValid = isValid,
                    errorMessage = if (isValid == false) "Signature invalide" else null
                ))
            }

            val allValid = signerInfos.isNotEmpty() && signerInfos.all { it.isValid == true }
            VerificationResult(allValid, signerInfos)
        } catch (e: Exception) {
            VerificationResult(false, emptyList(), "Erreur de vérification: ${e.message}")
        }
    }

    /**
     * Parse public key info without verifying any signature.
     */
    fun parsePublicKey(keyBytes: ByteArray): List<SignerInfo> {
        return try {
            val keyIn = PGPUtil.getDecoderStream(ByteArrayInputStream(keyBytes))
            val keyRingCollection = PGPPublicKeyRingCollection(keyIn, BcKeyFingerprintCalculator())

            val result = mutableListOf<SignerInfo>()
            for (keyRing in keyRingCollection) {
                for (key in keyRing) {
                    if (key.isEncryptionKey) continue
                    result.add(
                        SignerInfo(
                            keyId = "%016X".format(key.keyID),
                            fingerprint = formatFingerprint(key.fingerprint),
                            userId = getUserId(key),
                            algorithm = getAlgorithmName(key.algorithm),
                            signatureDate = key.creationTime,
                            isValid = true
                        )
                    )
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun findPublicKey(keyBytes: ByteArray, keyId: Long): PGPPublicKey? {
        return try {
            val keyIn = PGPUtil.getDecoderStream(ByteArrayInputStream(keyBytes))
            val keyRingCollection = PGPPublicKeyRingCollection(keyIn, BcKeyFingerprintCalculator())
            keyRingCollection.getPublicKey(keyId)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatFingerprint(fp: ByteArray): String {
        return fp.joinToString(" ") { "%02X".format(it) }
            .chunked(5)
            .joinToString(" ")
    }

    private fun getUserId(key: PGPPublicKey): String {
        val ids = key.userIDs
        return if (ids.hasNext()) ids.next() else "ID inconnu"
    }

    private fun getAlgorithmName(algorithm: Int): String = when (algorithm) {
        1 -> "RSA"
        2 -> "RSA Encrypt"
        3 -> "RSA Sign"
        16 -> "ElGamal"
        17 -> "DSA"
        18 -> "ECDH"
        19 -> "ECDSA"
        22 -> "EdDSA"
        else -> "Algo #$algorithm"
    }

}

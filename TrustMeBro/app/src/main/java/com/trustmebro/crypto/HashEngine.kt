package com.trustmebro.crypto

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/**
 * Streaming hash computation engine using MessageDigest.
 * Supports large files (100MB+) via chunked reading with progress callbacks.
 */
object HashEngine {

    enum class Algorithm(val digestName: String, val displayName: String) {
        SHA256("SHA-256", "SHA-256")
    }

    data class HashResult(
        val algorithm: Algorithm,
        val hex: String
    )

    /**
     * Compute hash of a file identified by URI using SAF.
     * @param onProgress callback with value 0.0..1.0
     */
    fun computeHash(
        context: Context,
        uri: Uri,
        algorithm: Algorithm,
        onProgress: ((Float) -> Unit)? = null
    ): HashResult {
        val digest = MessageDigest.getInstance(algorithm.digestName)
        val buffer = ByteArray(8192)
        var totalRead = 0L

        val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            fd.statSize
        } ?: -1L

        context.contentResolver.openInputStream(uri)?.use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (fileSize > 0) {
                    onProgress?.invoke(totalRead.toFloat() / fileSize.toFloat())
                }
            }
        } ?: throw IllegalStateException("Impossible d'ouvrir le fichier")

        val hex = digest.digest().toHexString()
        return HashResult(algorithm, hex)
    }

    /**
     * Parse a checksum file content.
     * Supports:
     * - Standard GNU coreutils format: "<hash>  <filename>" or "<hash> *<filename>"
     * - Single hash on a line
     * - PGP cleartext signed messages (extracts body between headers)
     *
     * Returns a list of (hash, filename?) pairs.
     */
    fun parseChecksumFile(content: String): List<Pair<String, String?>> {
        val body = extractPgpBody(content) ?: content
        val results = mutableListOf<Pair<String, String?>>()

        for (line in body.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue

            // Match: <hex>  <filename> or <hex> *<filename> or <hex>\t<filename>
            val parts = trimmed.split(Regex("\\s+\\*?|\\t\\*?"), limit = 2)
            if (parts.size == 2) {
                val hash = parts[0].trim()
                val filename = parts[1].trim().removePrefix("*").trim()
                if (isValidHash(hash)) {
                    results.add(hash to filename.ifEmpty { null })
                    continue
                }
            }

            // Single hash on a line
            if (parts.size == 1 && isValidHash(trimmed)) {
                results.add(trimmed to null)
            }
        }
        return results
    }

    /**
     * Extract the body from a PGP cleartext signed message.
     * Returns null if not a PGP cleartext message.
     */
    fun extractPgpBody(content: String): String? {
        val beginMarker = "-----BEGIN PGP SIGNED MESSAGE-----"
        val beginSig = "-----BEGIN PGP SIGNATURE-----"

        val start = content.indexOf(beginMarker)
        if (start == -1) return null

        // Skip headers (lines until blank line after BEGIN PGP SIGNED MESSAGE)
        val afterHeader = content.indexOf("\n\n", start)
        if (afterHeader == -1) return null

        val bodyStart = afterHeader + 2
        val bodyEnd = content.indexOf(beginSig)
        if (bodyEnd == -1) return null

        return content.substring(bodyStart, bodyEnd).trim()
    }

    private fun isValidHash(s: String): Boolean {
        return s.length == 64 &&
                s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}

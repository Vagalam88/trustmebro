package com.trustmebro.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileClassifier {

    enum class FileType(val label: String, val emoji: String) {
        APK("Fichier APK",               "📱"),
        EXECUTABLE("Exécutable",          "💾"),
        DETACHED_SIGNATURE("Signature détachée",       "🔏"),
        SIGNED_CHECKSUM("Somme de contrôle signée",    "🔏"),
        PUBLIC_KEY("Clé publique PGP",   "🔑"),
        CHECKSUM_FILE("Fichier de hachage",             "#️⃣"),
        UNKNOWN("Fichier inconnu",        "❓")
    }

    data class ClassifiedFile(
        val uri: Uri,
        val fileName: String,
        val fileSize: Long,
        val type: FileType
    )

    private const val MAX_TEXT_SNIFF_SIZE = 5 * 1024 * 1024L

    fun classify(context: Context, uri: Uri): ClassifiedFile {
        val fileName = getFileName(context, uri)
        val fileSize = getFileSize(context, uri)
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // Step 1: binary executables by extension
        when (extension) {
            "apk", "aab", "xapk" ->
                return ClassifiedFile(uri, fileName, fileSize, FileType.APK)

            "exe", "msi", "msix", "appx", "appxbundle",
            "dmg", "pkg",
            "deb", "rpm", "snap", "run", "bin",
            "appimage",
            "jar", "war",
            "zip", "tar", "7z", "rar",
            "flatpak" ->
                return ClassifiedFile(uri, fileName, fileSize, FileType.EXECUTABLE)
        }

        // Compound extensions: .tar.gz, .tar.bz2, .tar.xz, .tar.zst
        val lowerName = fileName.lowercase()
        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tar.bz2") ||
            lowerName.endsWith(".tar.xz") || lowerName.endsWith(".tar.zst") ||
            lowerName.endsWith(".tar.lz4")) {
            return ClassifiedFile(uri, fileName, fileSize, FileType.EXECUTABLE)
        }

        // Step 2: large unknown files → binary guess
        if (fileSize > MAX_TEXT_SNIFF_SIZE) {
            return ClassifiedFile(uri, fileName, fileSize, guessTypeByExtension(extension, lowerName))
        }

        // Step 3: content sniffing for text/PGP files
        val headerBytes = readHeader(context, uri, 512)
        val headerText = headerBytes?.toString(Charsets.UTF_8)?.trim() ?: ""

        when {
            headerText.startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----") ->
                return ClassifiedFile(uri, fileName, fileSize, FileType.PUBLIC_KEY)
            headerText.startsWith("-----BEGIN PGP SIGNED MESSAGE-----") ->
                return ClassifiedFile(uri, fileName, fileSize, FileType.SIGNED_CHECKSUM)
            headerText.startsWith("-----BEGIN PGP SIGNATURE-----") ->
                return ClassifiedFile(uri, fileName, fileSize, FileType.DETACHED_SIGNATURE)
        }

        // Magic bytes detection for binary executables
        if (headerBytes != null && isBinaryExecutable(headerBytes)) {
            return ClassifiedFile(uri, fileName, fileSize, FileType.EXECUTABLE)
        }

        // Step 4: checksum file detection (full text read)
        val fullText = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.toString(Charsets.UTF_8) ?: ""
        } catch (_: Exception) { "" }

        if (looksLikeChecksumFile(fullText)) {
            if (extension == "asc" || lowerName.contains("sha256") ||
                lowerName.contains("checksum") || lowerName.contains("sums")) {
                return ClassifiedFile(uri, fileName, fileSize, FileType.SIGNED_CHECKSUM)
            }
            return ClassifiedFile(uri, fileName, fileSize, FileType.CHECKSUM_FILE)
        }

        // Step 5: extension fallback
        return ClassifiedFile(uri, fileName, fileSize, guessTypeByExtension(extension, lowerName))
    }

    /** Returns true if the file is an executable/installer (APK or EXECUTABLE). */
    fun ClassifiedFile.isExecutable(): Boolean =
        type == FileType.APK || type == FileType.EXECUTABLE

    private fun isBinaryExecutable(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val b = header
        return when {
            // ELF (Linux binaries, AppImage)
            b[0] == 0x7F.toByte() && b[1] == 'E'.code.toByte() &&
            b[2] == 'L'.code.toByte() && b[3] == 'F'.code.toByte() -> true
            // ZIP / APK / JAR / APPX
            b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() -> true
            // Windows PE (EXE/DLL/MSI)
            b[0] == 0x4D.toByte() && b[1] == 0x5A.toByte() -> true
            // Mach-O (macOS DMG/PKG fat binary)
            b[0] == 0xCA.toByte() && b[1] == 0xFE.toByte() &&
            b[2] == 0xBA.toByte() && b[3] == 0xBE.toByte() -> true
            // RPM
            b[0] == 0xED.toByte() && b[1] == 0xAB.toByte() &&
            b[2] == 0xEE.toByte() && b[3] == 0xDB.toByte() -> true
            // Debian ar archive (.deb)
            header.size >= 7 && header.copyOf(7).contentEquals("!<arch>".toByteArray()) -> true
            // gzip
            b[0] == 0x1F.toByte() && b[1] == 0x8B.toByte() -> true
            // bzip2
            b[0] == 0x42.toByte() && b[1] == 0x5A.toByte() && b[2] == 0x68.toByte() -> true
            // 7-zip
            b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() &&
            b[2] == 0xBC.toByte() && b[3] == 0xAF.toByte() -> true
            // XZ
            b[0] == 0xFD.toByte() && b[1] == 0x37.toByte() &&
            b[2] == 0x7A.toByte() && b[3] == 0x58.toByte() -> true
            else -> false
        }
    }

    private fun guessTypeByExtension(extension: String, lowerName: String): FileType {
        return when (extension) {
            "asc" -> if (lowerName.contains("sha256") || lowerName.contains("checksum") ||
                         lowerName.contains("sums") || lowerName.contains("hash"))
                         FileType.SIGNED_CHECKSUM else FileType.DETACHED_SIGNATURE
            "sig", "sign", "gpg" -> FileType.DETACHED_SIGNATURE
            "sha256" -> FileType.CHECKSUM_FILE
            "pub", "key" -> FileType.PUBLIC_KEY
            "sh", "bash" -> FileType.EXECUTABLE
            else -> FileType.UNKNOWN
        }
    }

    private fun looksLikeChecksumFile(content: String): Boolean {
        if (content.isBlank()) return false
        val lines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return false
        val hashLineCount = lines.count { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            parts.size >= 1 && isHexHash(parts[0].trim())
        }
        return hashLineCount.toFloat() / lines.size.toFloat() >= 0.5f
    }

    private fun isHexHash(s: String): Boolean =
        s.length == 64 &&
        s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    private fun readHeader(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(maxBytes)
                val read = stream.read(buffer)
                if (read > 0) buffer.copyOf(read) else null
            }
        } catch (_: Exception) { null }
    }

    fun getFileName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString().substringAfterLast('/')
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        } catch (_: Exception) {}
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) { -1L }
    }
}

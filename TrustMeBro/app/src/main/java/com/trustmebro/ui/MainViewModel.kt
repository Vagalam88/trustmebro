package com.trustmebro.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustmebro.crypto.APKParser
import com.trustmebro.crypto.HashEngine
import com.trustmebro.crypto.PGPEngine
import com.trustmebro.utils.FileClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

// ── UI State Models ──────────────────────────────────────────────────────────

data class HashVerificationResult(
    val algorithm: String,
    val computedHash: String,
    val expectedHash: String?,
    val expectedFrom: String?,
    val isMatch: Boolean?
)

data class PGPVerificationResult(
    val type: String, // "cleartext" | "detached"
    val isValid: Boolean?, // null = no key provided (unverifiable), true/false = verified
    val signerIdentity: String,
    val keyId: String,
    val fingerprint: String,
    val algorithm: String,
    val signatureDate: String?,
    val errorMessage: String?
)

data class APKAnalysisResult(
    val subjectDN: String,
    val issuerDN: String,
    val serialNumber: String,
    val sigAlgorithm: String,
    val validFrom: String,
    val validUntil: String,
    val fingerprintSha256: String,
    val isExpired: Boolean,
    val hasV2Block: Boolean,
    val hasV3Block: Boolean
)

data class TrustMeBroState(
    val classifiedFiles: List<FileClassifier.ClassifiedFile> = emptyList(),
    val isVerifying: Boolean = false,
    val verificationProgress: Float = 0f,
    val progressLabel: String = "",

    // Results
    val hashResults: List<HashVerificationResult> = emptyList(),
    val pgpResults: List<PGPVerificationResult> = emptyList(),
    val apkResults: List<APKAnalysisResult> = emptyList(),
    val errors: List<String> = emptyList(),

    val hasResults: Boolean = false,

    // Capabilities detected
    val canVerifyChecksum: Boolean = false,
    val canVerifyPGP: Boolean = false,
    val canAnalyzeAPK: Boolean = false,
    val hasPublicKey: Boolean = false,

    // Manual hash input
    val manualExpectedHash: String = "",

    // Warnings
    val hasDuplicateTypes: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(TrustMeBroState())
    val state: StateFlow<TrustMeBroState> = _state.asStateFlow()

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    fun onFilesSelected(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val classified = withContext(Dispatchers.IO) {
                uris.map { FileClassifier.classify(context, it) }
            }

            val hasExecutable = classified.any {
                it.type == FileClassifier.FileType.APK ||
                it.type == FileClassifier.FileType.EXECUTABLE
            }
            val hasApk = classified.any { it.type == FileClassifier.FileType.APK }
            val hasChecksum = classified.any {
                it.type == FileClassifier.FileType.CHECKSUM_FILE ||
                it.type == FileClassifier.FileType.SIGNED_CHECKSUM
            }
            val hasSignature = classified.any {
                it.type == FileClassifier.FileType.DETACHED_SIGNATURE ||
                it.type == FileClassifier.FileType.SIGNED_CHECKSUM
            }
            val hasKey = classified.any { it.type == FileClassifier.FileType.PUBLIC_KEY }
            val hasDuplicates = hasDuplicateTypes(classified)

            _state.update {
                it.copy(
                    classifiedFiles = classified,
                    canVerifyChecksum = hasExecutable && hasChecksum,
                    canVerifyPGP = hasSignature,
                    canAnalyzeAPK = hasApk,
                    hasPublicKey = hasKey,
                    hasDuplicateTypes = hasDuplicates,
                    hashResults = emptyList(),
                    pgpResults = emptyList(),
                    apkResults = emptyList(),
                    errors = emptyList(),
                    hasResults = false
                )
            }
        }
    }

    fun onManualHashChanged(hash: String) {
        _state.update { it.copy(manualExpectedHash = hash) }
    }

    fun removeFile(uri: Uri) {
        _state.update { state ->
            val newFiles = state.classifiedFiles.filter { it.uri != uri }
            state.copy(
                classifiedFiles = newFiles,
                canVerifyChecksum = newFiles.any {
                    it.type == FileClassifier.FileType.APK ||
                    it.type == FileClassifier.FileType.EXECUTABLE
                } && newFiles.any {
                    it.type == FileClassifier.FileType.CHECKSUM_FILE ||
                    it.type == FileClassifier.FileType.SIGNED_CHECKSUM
                },
                canVerifyPGP = newFiles.any {
                    it.type == FileClassifier.FileType.DETACHED_SIGNATURE ||
                    it.type == FileClassifier.FileType.SIGNED_CHECKSUM
                },
                canAnalyzeAPK = newFiles.any { it.type == FileClassifier.FileType.APK },
                hasPublicKey = newFiles.any { it.type == FileClassifier.FileType.PUBLIC_KEY },
                hasDuplicateTypes = hasDuplicateTypes(newFiles)
            )
        }
    }

    fun clearFiles() {
        _state.update { TrustMeBroState() }
    }

    fun runVerification(context: Context) {
        val currentState = _state.value
        if (currentState.isVerifying) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isVerifying = true,
                    verificationProgress = 0f,
                    progressLabel = "Initialisation...",
                    hashResults = emptyList(),
                    pgpResults = emptyList(),
                    apkResults = emptyList(),
                    errors = emptyList(),
                    hasResults = false
                )
            }

            val files = currentState.classifiedFiles
            val errors = mutableListOf<String>()
            val hashResults = mutableListOf<HashVerificationResult>()
            val pgpResults = mutableListOf<PGPVerificationResult>()
            val apkResults = mutableListOf<APKAnalysisResult>()

            val targetFile = files.firstOrNull {
                it.type == FileClassifier.FileType.APK ||
                it.type == FileClassifier.FileType.EXECUTABLE
            }
            val apkFile = files.firstOrNull { it.type == FileClassifier.FileType.APK }
            val checksumFile = files.firstOrNull {
                it.type == FileClassifier.FileType.CHECKSUM_FILE ||
                it.type == FileClassifier.FileType.SIGNED_CHECKSUM
            }
            val sigFile = files.firstOrNull {
                it.type == FileClassifier.FileType.DETACHED_SIGNATURE
            }
            val signedChecksumFile = files.firstOrNull {
                it.type == FileClassifier.FileType.SIGNED_CHECKSUM
            }
            val publicKeyFile = files.firstOrNull { it.type == FileClassifier.FileType.PUBLIC_KEY }

            withContext(Dispatchers.IO) {

                // Pre-compute progress ranges based on what will actually run
                val willDoDetached = sigFile != null && targetFile != null
                val willDoCleartext = signedChecksumFile != null
                val willDoAPK = apkFile != null
                val hashEnd = if (willDoDetached) 0.42f else 0.78f
                val cleartextEnd = hashEnd + if (willDoCleartext) 0.08f else 0f
                val detachedEnd = cleartextEnd + if (willDoDetached) 0.40f else 0f

                // ── 1. Checksum Verification ─────────────────────────────────
                if (targetFile != null && (checksumFile != null || currentState.manualExpectedHash.isNotBlank())) {
                    updateProgress(0.02f, "Calcul des empreintes cryptographiques...")

                    try {
                        val hashResult = HashEngine.computeHash(
                            context, targetFile.uri, HashEngine.Algorithm.SHA256
                        ) { progress ->
                            updateProgress(0.02f + progress * (hashEnd - 0.02f), "Calcul SHA-256...")
                        }

                        // Parse expected hashes from checksum file
                        val expectedHashes = if (checksumFile != null) {
                            try {
                                val content = context.contentResolver
                                    .openInputStream(checksumFile.uri)?.use { it.readBytes() }
                                    ?.toString(Charsets.UTF_8) ?: ""
                                HashEngine.parseChecksumFile(content)
                            } catch (e: Exception) {
                                errors.add("Erreur lecture fichier de hachage: ${e.message}")
                                emptyList()
                            }
                        } else emptyList()

                        val expectedEntry =
                            expectedHashes.firstOrNull { (hash, filename) ->
                                hash.length == hashResult.hex.length &&
                                filename != null &&
                                filename.equals(targetFile.fileName, ignoreCase = true)
                            } ?: expectedHashes.firstOrNull { (hash, _) ->
                                hash.length == hashResult.hex.length
                            }
                        val manualHash = if (currentState.manualExpectedHash.isNotBlank() &&
                            currentState.manualExpectedHash.length == hashResult.hex.length) {
                            currentState.manualExpectedHash.lowercase()
                        } else null

                        val expected = expectedEntry?.first?.lowercase() ?: manualHash
                        val expectedFrom = when {
                            expectedEntry != null -> checksumFile?.fileName ?: "fichier de hachage"
                            manualHash != null -> "saisie manuelle"
                            else -> null
                        }

                        hashResults.add(
                            HashVerificationResult(
                                algorithm = hashResult.algorithm.displayName,
                                computedHash = hashResult.hex,
                                expectedHash = expected,
                                expectedFrom = expectedFrom,
                                isMatch = expected?.let { it.equals(hashResult.hex, ignoreCase = true) }
                            )
                        )
                    } catch (e: Exception) {
                        errors.add("Erreur de calcul de hachage: ${e.message}")
                    }
                } else if (targetFile != null) {
                    // Compute hashes for display even without expected
                    updateProgress(0.02f, "Calcul des empreintes cryptographiques...")
                    try {
                        val hr = HashEngine.computeHash(
                            context, targetFile.uri, HashEngine.Algorithm.SHA256
                        ) { progress ->
                            updateProgress(0.02f + progress * (hashEnd - 0.02f), "Calcul des hachages...")
                        }
                        hashResults.add(
                            HashVerificationResult(
                                algorithm = hr.algorithm.displayName,
                                computedHash = hr.hex,
                                expectedHash = null,
                                expectedFrom = null,
                                isMatch = null
                            )
                        )
                    } catch (e: Exception) {
                        errors.add("Erreur de calcul de hachage: ${e.message}")
                    }
                }

                // ── 2. PGP Cleartext Signed Message Verification ─────────────
                if (signedChecksumFile != null) {
                    updateProgress(hashEnd + 0.02f, "Vérification signature PGP...")
                    try {
                        val signedBytes = context.contentResolver
                            .openInputStream(signedChecksumFile.uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Impossible de lire le fichier signé")

                        val publicKeyBytes = publicKeyFile?.let {
                            context.contentResolver.openInputStream(it.uri)?.use { s -> s.readBytes() }
                        }

                        val result = PGPEngine.verifyCleartextMessage(signedBytes, publicKeyBytes)

                        for (signer in result.signers) {
                            pgpResults.add(
                                PGPVerificationResult(
                                    type = "cleartext",
                                    isValid = signer.isValid,
                                    signerIdentity = signer.userId,
                                    keyId = signer.keyId,
                                    fingerprint = signer.fingerprint,
                                    algorithm = signer.algorithm,
                                    signatureDate = signer.signatureDate?.let { dateFormatter.format(it) },
                                    errorMessage = signer.errorMessage
                                )
                            )
                        }

                        if (result.signers.isEmpty() && result.errorMessage != null) {
                            errors.add("PGP cleartext: ${result.errorMessage}")
                        }
                    } catch (e: Exception) {
                        errors.add("Erreur vérification PGP cleartext: ${e.message}")
                    }
                }

                // ── 3. PGP Detached Signature Verification ───────────────────
                if (sigFile != null && targetFile != null) {
                    updateProgress(cleartextEnd + 0.01f, "Vérification signature détachée PGP...")
                    try {
                        val sigBytes = context.contentResolver
                            .openInputStream(sigFile.uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Impossible de lire la signature")

                        val publicKeyBytes = publicKeyFile?.let {
                            context.contentResolver.openInputStream(it.uri)?.use { s -> s.readBytes() }
                        }

                        // If there's a checksum file, the detached signature signs that file.
                        // Otherwise, the signature signs the executable directly.
                        val signedFile = checksumFile ?: targetFile
                        val signedFileSize = context.contentResolver
                            .openFileDescriptor(signedFile.uri, "r")?.use { it.statSize } ?: -1L
                        val dataStream = context.contentResolver.openInputStream(signedFile.uri)
                            ?: throw IllegalStateException("Impossible de lire le fichier")

                        val result = dataStream.use {
                            PGPEngine.verifyDetachedSignature(
                                it, sigBytes, publicKeyBytes,
                                fileSize = signedFileSize,
                                onProgress = { p ->
                                    updateProgress(cleartextEnd + p * (detachedEnd - cleartextEnd), "Vérification signature détachée...")
                                }
                            )
                        }

                        for (signer in result.signers) {
                            pgpResults.add(
                                PGPVerificationResult(
                                    type = "detached",
                                    isValid = signer.isValid,
                                    signerIdentity = signer.userId,
                                    keyId = signer.keyId,
                                    fingerprint = signer.fingerprint,
                                    algorithm = signer.algorithm,
                                    signatureDate = signer.signatureDate?.let { dateFormatter.format(it) },
                                    errorMessage = signer.errorMessage
                                )
                            )
                        }

                        if (result.signers.isEmpty() && result.errorMessage != null) {
                            errors.add("PGP détaché: ${result.errorMessage}")
                        }
                    } catch (e: Exception) {
                        errors.add("Erreur vérification signature détachée: ${e.message}")
                    }
                }

                // ── 4. APK Certificate Analysis ──────────────────────────────
                if (apkFile != null) {
                    updateProgress(detachedEnd + 0.02f, "Analyse du certificat APK...")
                    try {
                        val analysis = APKParser.analyzeApk(context, apkFile.uri)

                        for (cert in analysis.certificates) {
                            apkResults.add(
                                APKAnalysisResult(
                                    subjectDN = cert.subjectDN,
                                    issuerDN = cert.issuerDN,
                                    serialNumber = cert.serialNumber,
                                    sigAlgorithm = cert.sigAlgorithm,
                                    validFrom = dateFormatter.format(cert.validFrom),
                                    validUntil = dateFormatter.format(cert.validUntil),
                                    fingerprintSha256 = cert.fingerprintSha256,
                                    isExpired = cert.isExpired,
                                    hasV2Block = analysis.hasV2SigningBlock,
                                    hasV3Block = analysis.hasV3SigningBlock
                                )
                            )
                        }

                        if (analysis.errorMessage != null) {
                            errors.add("APK: ${analysis.errorMessage}")
                        }
                    } catch (e: Exception) {
                        errors.add("Erreur analyse APK: ${e.message}")
                    }
                }
            }

            _state.update {
                it.copy(
                    isVerifying = false,
                    verificationProgress = 1f,
                    progressLabel = "Terminé",
                    hashResults = hashResults,
                    pgpResults = pgpResults,
                    apkResults = apkResults,
                    errors = errors,
                    hasResults = hashResults.isNotEmpty() || pgpResults.isNotEmpty() ||
                                 apkResults.isNotEmpty() || errors.isNotEmpty()
                )
            }
        }
    }

    private fun updateProgress(value: Float, label: String) {
        _state.update { it.copy(verificationProgress = value, progressLabel = label) }
    }

    private fun hasDuplicateTypes(files: List<FileClassifier.ClassifiedFile>): Boolean {
        val counted = setOf(
            FileClassifier.FileType.APK,
            FileClassifier.FileType.EXECUTABLE,
            FileClassifier.FileType.DETACHED_SIGNATURE,
            FileClassifier.FileType.SIGNED_CHECKSUM,
            FileClassifier.FileType.PUBLIC_KEY,
            FileClassifier.FileType.CHECKSUM_FILE
        )
        return files.groupBy { it.type }.any { (type, list) -> type in counted && list.size > 1 }
    }
}

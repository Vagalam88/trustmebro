package com.trustmebro.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trustmebro.ui.MainViewModel
import com.trustmebro.ui.components.APKCertCard
import com.trustmebro.ui.components.CapabilitiesRow
import com.trustmebro.ui.components.ErrorCard
import com.trustmebro.ui.components.FileCard
import com.trustmebro.ui.components.HashResultCard
import com.trustmebro.ui.components.PGPResultCard
import com.trustmebro.ui.components.SectionHeader
import com.trustmebro.ui.components.ShieldHeader
import com.trustmebro.ui.components.TmbProgressBar
import com.trustmebro.ui.theme.MonospaceFamily
import com.trustmebro.ui.theme.TmbAccent
import com.trustmebro.ui.theme.TmbAccentDim
import com.trustmebro.ui.theme.TmbBackground
import com.trustmebro.ui.theme.TmbBorder
import com.trustmebro.ui.theme.TmbDanger
import com.trustmebro.ui.theme.TmbWarning
import com.trustmebro.ui.theme.TmbOnBackground
import com.trustmebro.ui.theme.TmbOnSurface
import com.trustmebro.ui.theme.TmbSubtle
import com.trustmebro.ui.theme.TmbSurface

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // SAF file picker — multiple files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(context, uris)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TmbBackground)
    ) {
        // Subtle grid background
        GridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            ShieldHeader()

            // ── File Picker Section ───────────────────────────────────────────
            if (state.classifiedFiles.isEmpty()) {
                EmptyDropZone(onPickFiles = {
                    filePicker.launch(arrayOf("*/*"))
                })
            } else {
                // File list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.classifiedFiles.forEach { file ->
                        FileCard(
                            file = file,
                            onRemove = { viewModel.removeFile(file.uri) }
                        )
                    }
                }

                if (state.hasDuplicateTypes) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = TmbWarning.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "⚠ Plusieurs fichiers du même type — seul le premier sera utilisé",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TmbWarning
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Add more files / clear buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Ajouter des fichiers", color = TmbAccent)
                    }
                    TextButton(
                        onClick = { viewModel.clearFiles() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tout effacer", color = TmbSubtle)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Capabilities ──────────────────────────────────────────────
                CapabilitiesRow(
                    canVerifyChecksum = state.canVerifyChecksum,
                    canVerifyPGP = state.canVerifyPGP,
                    canAnalyzeAPK = state.canAnalyzeAPK,
                    hasPublicKey = state.hasPublicKey
                )

                Spacer(Modifier.height(12.dp))

                // ── Manual hash input ─────────────────────────────────────────
                val hashInput = state.manualExpectedHash
                val hashValid = hashInput.isEmpty() ||
                    (hashInput.length == 64 && hashInput.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
                val hashBorderColor = when {
                    hashInput.isEmpty() -> TmbBorder
                    hashValid -> TmbOnBackground
                    else -> TmbDanger
                }

                OutlinedTextField(
                    value = hashInput,
                    onValueChange = { viewModel.onManualHashChanged(it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (!hashValid && hashInput.isNotEmpty()) "Hash invalide (64 caractères hex attendus)"
                            else "Hash attendu (optionnel — coller depuis GitHub)",
                            color = if (!hashValid && hashInput.isNotEmpty()) TmbDanger else TmbSubtle
                        )
                    },
                    placeholder = {
                        Text(
                            "ex: a1b2c3d4e5f6...",
                            color = TmbSubtle.copy(alpha = 0.5f),
                            fontFamily = MonospaceFamily,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = hashBorderColor,
                        unfocusedBorderColor = hashBorderColor,
                        focusedTextColor = if (hashValid) TmbOnBackground else TmbDanger,
                        unfocusedTextColor = if (hashValid) TmbOnSurface else TmbDanger,
                        cursorColor = hashBorderColor
                    ),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonospaceFamily,
                        fontSize = 13.sp,
                        color = if (hashValid) TmbOnBackground else TmbDanger
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(16.dp))

                // ── Progress ──────────────────────────────────────────────────
                if (state.isVerifying) {
                    TmbProgressBar(
                        progress = state.verificationProgress,
                        label = state.progressLabel
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ── Verify Button ─────────────────────────────────────────────
                VerifyButton(
                    enabled = !state.isVerifying && state.classifiedFiles.isNotEmpty(),
                    isVerifying = state.isVerifying,
                    onClick = { viewModel.runVerification(context) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Results ────────────────────────────────────────────────────────
            if (state.hasResults) {
                ResultsSection(state, viewModel)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun EmptyDropZone(onPickFiles: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        color = TmbSurface,
        onClick = onPickFiles
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📂", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Appuyez pour sélectionner des fichiers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TmbOnSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "APK, signature PGP, fichier de hachage, clé publique",
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbSubtle,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VerifyButton(
    enabled: Boolean,
    isVerifying: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = TmbSurface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled)
                        Brush.horizontalGradient(listOf(TmbAccent, TmbAccentDim))
                    else
                        Brush.horizontalGradient(listOf(TmbSubtle, TmbSubtle)),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isVerifying) "Vérification en cours..." else "🔍 Lancer la vérification",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (enabled) Color.White else TmbSubtle.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ResultsSection(
    state: com.trustmebro.ui.TrustMeBroState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Overall summary ───────────────────────────────────────────────
        val hashesWithExpected = state.hashResults.filter { it.isMatch != null }
        val allHashesOk = hashesWithExpected.isNotEmpty() && hashesWithExpected.all { it.isMatch == true }
        val anyHashFailed = state.hashResults.any { it.isMatch == false }
        val allPgpOk = state.pgpResults.isNotEmpty() && state.pgpResults.all { it.isValid == true }
        val anyPgpFailed = state.pgpResults.any { it.isValid == false }

        val overallColor = when {
            anyHashFailed || anyPgpFailed -> TmbDanger
            allHashesOk || allPgpOk -> TmbOnBackground
            else -> TmbSubtle
        }
        val overallText = when {
            anyHashFailed || anyPgpFailed -> "⚠ Vérification échouée — NE PAS FAIRE CONFIANCE"
            allHashesOk && allPgpOk -> "✓ Tout vérifié — fichier authentique"
            allHashesOk -> "✓ SHA-256 vérifié — empreinte conforme"
            allPgpOk -> "✓ Signature PGP valide"
            state.hashResults.isEmpty() && state.pgpResults.isEmpty() -> "📋 Analyse terminée"
            else -> "⚠ Vérification partielle — clé publique manquante ?"
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = overallColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = overallText,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.titleMedium,
                color = overallColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // ── Hash results ──────────────────────────────────────────────────
        if (state.hashResults.isNotEmpty()) {
            SectionHeader(title = "Empreintes cryptographiques", emoji = "#️⃣")
            state.hashResults.forEach { result ->
                HashResultCard(result)
            }
        }

        // ── PGP results ───────────────────────────────────────────────────
        if (state.pgpResults.isNotEmpty()) {
            SectionHeader(title = "Signatures PGP", emoji = "🔏")
            state.pgpResults.forEach { result ->
                PGPResultCard(result)
            }
        }

        // ── APK cert results ──────────────────────────────────────────────
        if (state.apkResults.isNotEmpty()) {
            SectionHeader(title = "Certificat de signature APK", emoji = "📦")
            state.apkResults.forEach { result ->
                APKCertCard(result)
            }
        }

        // ── Errors ────────────────────────────────────────────────────────
        if (state.errors.isNotEmpty()) {
            SectionHeader(title = "Erreurs", emoji = "⚠")
            state.errors.forEach { error ->
                ErrorCard(error)
            }
        }
    }
}

// ── Subtle grid background ────────────────────────────────────────────────────

@Composable
fun GridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridSpacing = 40.dp.toPx()
        val lineColor = Color(0xFF1A1A1A).copy(alpha = 0.8f)

        var x = 0f
        while (x <= size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            x += gridSpacing
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            y += gridSpacing
        }
    }
}

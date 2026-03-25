package com.trustmebro.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustmebro.ui.APKAnalysisResult
import com.trustmebro.ui.HashVerificationResult
import com.trustmebro.ui.PGPVerificationResult
import com.trustmebro.ui.theme.MonospaceFamily
import com.trustmebro.ui.theme.TmbAccent
import com.trustmebro.ui.theme.TmbBackground
import com.trustmebro.ui.theme.TmbBorder
import com.trustmebro.ui.theme.TmbDanger
import com.trustmebro.ui.theme.TmbInfo
import com.trustmebro.ui.theme.TmbOnBackground
import com.trustmebro.ui.theme.TmbOnSurface
import com.trustmebro.ui.theme.TmbSubtle
import com.trustmebro.ui.theme.TmbSurface
import com.trustmebro.ui.theme.TmbSurfaceVariant
import com.trustmebro.ui.theme.TmbWarning
import com.trustmebro.utils.FileClassifier
import android.content.ClipData
import android.content.ClipboardManager as SystemClipboardManager
import kotlinx.coroutines.delay

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatHashGroups(hash: String): String = hash.chunked(8).joinToString(" ")

// ── Animated Shield Header ────────────────────────────────────────────────────

@Composable
fun ShieldHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "🛡️",
                fontSize = 56.sp,
                modifier = Modifier.blur(12.dp),
                color = TmbAccent.copy(alpha = glowAlpha * 0.5f)
            )
            Text(text = "🛡️", fontSize = 56.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Trust",
                style = MaterialTheme.typography.headlineLarge,
                color = TmbOnBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Me",
                style = MaterialTheme.typography.headlineLarge,
                color = TmbAccent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Bro",
                style = MaterialTheme.typography.headlineLarge,
                color = TmbOnBackground,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Don't trust. Verify.",
            style = MaterialTheme.typography.bodyMedium,
            color = TmbSubtle,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── File Card ─────────────────────────────────────────────────────────────────

@Composable
fun FileCard(
    file: FileClassifier.ClassifiedFile,
    onRemove: () -> Unit
) {
    val sizeText = when {
        file.fileSize < 0 -> "taille inconnue"
        file.fileSize < 1024 -> "${file.fileSize} o"
        file.fileSize < 1024 * 1024 -> "${"%.1f".format(file.fileSize / 1024.0)} Ko"
        else -> "${"%.1f".format(file.fileSize / (1024.0 * 1024.0))} Mo"
    }

    val borderColor = when (file.type) {
        FileClassifier.FileType.APK,
        FileClassifier.FileType.EXECUTABLE -> TmbOnSurface.copy(alpha = 0.3f)
        FileClassifier.FileType.PUBLIC_KEY -> TmbInfo.copy(alpha = 0.4f)
        FileClassifier.FileType.SIGNED_CHECKSUM,
        FileClassifier.FileType.DETACHED_SIGNATURE -> TmbInfo.copy(alpha = 0.3f)
        FileClassifier.FileType.CHECKSUM_FILE -> TmbOnSurface.copy(alpha = 0.2f)
        else -> TmbBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TmbSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = file.type.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TmbOnSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${file.type.label} • $sizeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbSubtle
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Retirer",
                    tint = TmbSubtle
                )
            }
        }
    }
}

// ── Capabilities Chips ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilitiesRow(
    canVerifyChecksum: Boolean,
    canVerifyPGP: Boolean,
    canAnalyzeAPK: Boolean,
    hasPublicKey: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (canVerifyChecksum) CapabilityChip("✓ Vérification hachage", TmbOnSurface)
        if (canVerifyPGP) CapabilityChip("✓ Vérification PGP", TmbInfo)
        if (canAnalyzeAPK) CapabilityChip("📦 Certificat APK", TmbWarning)
        if (hasPublicKey) CapabilityChip("🔑 Clé publique fournie", TmbOnSurface)
        if (!canVerifyChecksum && !canVerifyPGP && !canAnalyzeAPK) {
            CapabilityChip("⚠ Ajoutez un fichier pour commencer", TmbWarning)
        }
    }
}

@Composable
fun CapabilityChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Progress Bar ──────────────────────────────────────────────────────────────

@Composable
fun TmbProgressBar(progress: Float, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TmbSubtle
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = TmbOnSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = TmbAccent,
            trackColor = TmbSurfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

// ── Status Badge ──────────────────────────────────────────────────────────────

@Composable
fun StatusBadge(isMatch: Boolean?, modifier: Modifier = Modifier) {
    when (isMatch) {
        true -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TmbOnBackground.copy(alpha = 0.12f),
                modifier = modifier.border(1.dp, TmbOnBackground.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = "✓ VALIDE",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbOnBackground,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFamily
                )
            }
        }
        false -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TmbDanger.copy(alpha = 0.15f),
                modifier = modifier.border(1.dp, TmbDanger.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = "✗ INVALIDE",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbDanger,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFamily
                )
            }
        }
        null -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = TmbSubtle.copy(alpha = 0.15f),
                modifier = modifier.border(1.dp, TmbSubtle.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = "SANS CLÉ",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbSubtle,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

// ── Hash Display Card ─────────────────────────────────────────────────────────

@Composable
fun HashResultCard(result: HashVerificationResult) {
    val borderColor = when (result.isMatch) {
        true -> TmbOnBackground.copy(alpha = 0.3f)
        false -> TmbDanger.copy(alpha = 0.4f)
        null -> TmbBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TmbSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.algorithm,
                    style = MaterialTheme.typography.titleMedium,
                    color = TmbOnSurface,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(result.isMatch)
            }

            Spacer(Modifier.height(10.dp))

            HashLine(label = "Calculé", value = result.computedHash)

            if (result.expectedHash != null) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = TmbBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))
                HashLine(
                    label = "Attendu (${result.expectedFrom ?: "?"})",
                    value = result.expectedHash,
                    highlight = result.isMatch == false
                )
            }
        }
    }
}

@Composable
fun HashLine(label: String, value: String, highlight: Boolean = false) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TmbSubtle
            )
            if (copied) {
                Text(
                    text = "✓ Copié",
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbOnBackground,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatHashGroups(value),
            fontFamily = MonospaceFamily,
            fontSize = 11.sp,
            color = if (highlight) TmbDanger else TmbOnSurface,
            letterSpacing = 0.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.clickable {
                copied = true
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as SystemClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("", value))
            }
        )
    }
}

// ── PGP Result Card ───────────────────────────────────────────────────────────

@Composable
fun PGPResultCard(result: PGPVerificationResult) {
    val borderColor = when (result.isValid) {
        true -> TmbOnBackground.copy(alpha = 0.3f)
        false -> TmbDanger.copy(alpha = 0.4f)
        null -> TmbSubtle.copy(alpha = 0.4f)
    }
    val typeLabel = if (result.type == "cleartext") "Signé PGP" else "Signature PGP détachée"
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var fpCopied by remember { mutableStateOf(false) }

    LaunchedEffect(fpCopied) {
        if (fpCopied) { delay(2000); fpCopied = false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TmbSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TmbOnSurface,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(result.isValid)
            }

            Spacer(Modifier.height(10.dp))

            if (result.signerIdentity.isNotBlank() && result.signerIdentity != "Inconnu") {
                InfoRow("Signataire", result.signerIdentity)
            }
            if (result.keyId.isNotBlank()) {
                InfoRow("ID de clé", "0x${result.keyId}")
            }
            if (result.algorithm.isNotBlank()) {
                InfoRow("Algorithme", result.algorithm)
            }
            if (result.signatureDate != null) {
                InfoRow("Date", result.signatureDate)
            }

            if (result.fingerprint.isNotBlank() && result.fingerprint != "—") {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TmbBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Empreinte de la clé",
                        style = MaterialTheme.typography.bodySmall,
                        color = TmbSubtle
                    )
                    if (fpCopied) {
                        Text(
                            text = "✓ Copié",
                            style = MaterialTheme.typography.bodySmall,
                            color = TmbOnBackground,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = result.fingerprint,
                    fontFamily = MonospaceFamily,
                    fontSize = 11.sp,
                    color = TmbOnSurface,
                    letterSpacing = 0.3.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.clickable {
                        fpCopied = true
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as SystemClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("", result.fingerprint))
                    }
                )
            }

            // "Trouver la clé" — only when no public key was provided
            if (result.isValid == null && result.keyId.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://keys.openpgp.org/search?q=0x${result.keyId}")
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "🔍 Rechercher la clé 0x${result.keyId} sur keys.openpgp.org →",
                        style = MaterialTheme.typography.bodySmall,
                        color = TmbOnSurface
                    )
                }
            }

            if (result.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "⚠ ${result.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbWarning
                )
            }
        }
    }
}

// ── APK Certificate Card ──────────────────────────────────────────────────────

@Composable
fun APKCertCard(result: APKAnalysisResult) {
    val borderColor = if (result.isExpired) TmbDanger.copy(alpha = 0.4f) else TmbBorder
    val context = LocalContext.current
    var fpCopied by remember { mutableStateOf(false) }

    LaunchedEffect(fpCopied) {
        if (fpCopied) { delay(2000); fpCopied = false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TmbSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Certificat APK",
                    style = MaterialTheme.typography.titleMedium,
                    color = TmbOnSurface,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result.hasV2Block) CapabilityChip("v2", TmbOnSurface)
                    if (result.hasV3Block) CapabilityChip("v3", TmbOnSurface)
                    if (result.isExpired) CapabilityChip("EXPIRÉ", TmbDanger)
                }
            }

            Spacer(Modifier.height(10.dp))

            InfoRow("Sujet", result.subjectDN)
            if (result.issuerDN != result.subjectDN) {
                InfoRow("Émetteur", result.issuerDN)
            }
            InfoRow("Algorithme", result.sigAlgorithm)
            InfoRow("Valide du", result.validFrom)
            InfoRow("Valide jusqu'au", result.validUntil)
            InfoRow("Numéro de série", result.serialNumber)

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = TmbBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Empreinte SHA-256 — comparez avec celle du développeur",
                    style = MaterialTheme.typography.bodySmall,
                    color = TmbSubtle,
                    modifier = Modifier.weight(1f)
                )
                if (fpCopied) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "✓ Copié",
                        style = MaterialTheme.typography.bodySmall,
                        color = TmbOnBackground,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = result.fingerprintSha256,
                fontFamily = MonospaceFamily,
                fontSize = 10.sp,
                color = TmbOnSurface,
                letterSpacing = 0.3.sp,
                lineHeight = 15.sp,
                modifier = Modifier.clickable {
                    fpCopied = true
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as SystemClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("", result.fingerprintSha256))
                }
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = TmbSubtle,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TmbOnSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Section Header ─────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, emoji: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TmbOnBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = TmbBorder,
            thickness = 0.5.dp
        )
    }
}

// ── Error Card ────────────────────────────────────────────────────────────────

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TmbDanger.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TmbDanger.copy(alpha = 0.08f))
    ) {
        Text(
            text = "⚠ $message",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = TmbDanger
        )
    }
}

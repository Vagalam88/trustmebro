package com.trustmebro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

// ── TMB Color Palette ────────────────────────────────────────────────────────

val TmbBackground = Color(0xFF0A0A0A)
val TmbSurface = Color(0xFF131313)
val TmbSurfaceVariant = Color(0xFF1E1E1E)
val TmbAccent = Color(0xFFDD0000)        // Red — brand + primary action
val TmbAccentDim = Color(0xFFAA0000)     // Darker red
val TmbDanger = Color(0xFFDD0000)        // Red — errors/invalid
val TmbWarning = Color(0xFF888888)       // Grey
val TmbInfo = Color(0xFFBBBBBB)          // Light grey
val TmbOnBackground = Color(0xFFFFFFFF)  // White
val TmbOnSurface = Color(0xFFE0E0E0)     // Near white
val TmbSubtle = Color(0xFF555555)        // Medium grey
val TmbBorder = Color(0xFF222222)        // Dark border

private val TmbColorScheme = darkColorScheme(
    primary = TmbAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0000),
    onPrimaryContainer = TmbOnBackground,
    secondary = TmbInfo,
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = TmbOnBackground,
    tertiary = TmbWarning,
    onTertiary = Color(0xFF111111),
    error = TmbDanger,
    onError = Color.White,
    errorContainer = Color(0xFF3A0000),
    onErrorContainer = TmbOnBackground,
    background = TmbBackground,
    onBackground = TmbOnBackground,
    surface = TmbSurface,
    onSurface = TmbOnSurface,
    surfaceVariant = TmbSurfaceVariant,
    onSurfaceVariant = TmbSubtle,
    outline = TmbBorder,
    outlineVariant = Color(0xFF1A1A1A)
)

// ── Typography ────────────────────────────────────────────────────────────────

val MonospaceFamily = FontFamily.Monospace

private val TmbTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = TmbOnBackground
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = TmbOnBackground
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = TmbOnBackground
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = TmbOnBackground
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = TmbOnSurface
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = TmbOnSurface
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TmbOnSurface
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = TmbSubtle
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = MonospaceFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = TmbSubtle
    )
)

@Composable
fun TrustMeBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TmbColorScheme,
        typography = TmbTypography,
        content = content
    )
}

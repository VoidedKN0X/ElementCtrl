package com.vknxworks.elementctrl.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.elementctrl.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.data.ThemeMode

// Graphite base shared by the Ctrl apps. ElementCtrl's own accent is blossom, the pink of its icon.
private val Graphite = Color(0xFF121418)
private val Blossom = Color(0xFFF4A6C8)

/** ElementCtrl's accent. */
val ElementAccent = Blossom

private val BrandDark = darkColorScheme(
    primary = Blossom,
    onPrimary = Color(0xFF4F1530),
    primaryContainer = Color(0xFF6A2A47),
    onPrimaryContainer = Color(0xFFFFD9E6),
    secondary = Color(0xFFC9CED8),
    onSecondary = Color(0xFF1B1E24),
    secondaryContainer = Color(0xFF2B3038),
    onSecondaryContainer = Color(0xFFECEEF2),
    tertiary = Color(0xFF8BD8C6),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF1F4D43),
    onTertiaryContainer = Color(0xFFB4F1E1),
    background = Graphite,
    onBackground = Color(0xFFECEEF2),
    surface = Graphite,
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF23272E),
    onSurfaceVariant = Color(0xFF9AA1AD),
    surfaceContainerLowest = Color(0xFF0D0F12),
    surfaceContainerLow = Color(0xFF171A1F),
    surfaceContainer = Color(0xFF1C1F25),
    surfaceContainerHigh = Color(0xFF23272E),
    surfaceContainerHighest = Color(0xFF2B3038),
    outline = Color(0xFF5A616C),
    outlineVariant = Color(0xFF2E333B),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF9C3D66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E6),
    onPrimaryContainer = Color(0xFF3E0021),
    secondary = Color(0xFF4A505B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E7EC),
    onSecondaryContainer = Color(0xFF15181D),
    tertiary = Color(0xFF0F6E5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB4F1E1),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF15181D),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF15181D),
    surfaceVariant = Color(0xFFE4E7EC),
    onSurfaceVariant = Color(0xFF5B626E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2F5),
    surfaceContainer = Color(0xFFEBEDF1),
    surfaceContainerHigh = Color(0xFFE4E7EC),
    surfaceContainerHighest = Color(0xFFDCE0E6),
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFFD3D7DE),
)

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** The Ctrl series typeface. */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(52, 56, 800, -0.03),
    displayMedium = style(44, 48, 800, -0.03),
    displaySmall = style(36, 40, 800, -0.025),
    headlineLarge = style(32, 38, 800, -0.02),
    headlineMedium = style(28, 34, 700, -0.015),
    headlineSmall = style(24, 30, 700, -0.01),
    titleLarge = style(20, 26, 700, -0.005),
    titleMedium = style(16, 22, 700),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 500),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 600),
    labelSmall = style(11, 14, 600, 0.01),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Horizontal page margin used everywhere, as in the other Ctrl apps. */
val Gutter = 20.dp

/** Colours for names in group chats, chosen from the user ID so each person keeps theirs. */
val SenderColors = listOf(
    Color(0xFF0DBD8B), Color(0xFF368BD6), Color(0xFFAC3BA8), Color(0xFFE64F7A),
    Color(0xFFFF812D), Color(0xFF2DC2C5), Color(0xFF5C56F5), Color(0xFF74D12C),
)

fun senderColor(userId: String): Color = SenderColors[(userId.hashCode() and 0x7fffffff) % SenderColors.size]

@Composable
fun isDarkTheme(): Boolean {
    val mode by ElementCtrlApp.graph.prefs.theme.collectAsStateWithLifecycle()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
}

@Composable
fun ElementCtrlTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val dynamic by ElementCtrlApp.graph.prefs.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mode by ElementCtrlApp.graph.prefs.theme.collectAsStateWithLifecycle()
    var scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }
    if (mode == ThemeMode.BLACK) {
        // Pure black: the same as in the other Ctrl apps, for OLED screens.
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0C0E),
            surfaceContainer = Color(0xFF131518),
            surfaceContainerHigh = Color(0xFF1A1C20),
            surfaceContainerHighest = Color(0xFF22252A),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

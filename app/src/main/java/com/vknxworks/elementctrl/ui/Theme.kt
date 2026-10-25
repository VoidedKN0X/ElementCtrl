package com.vknxworks.elementctrl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.elementctrl.R

/** One Ctrl palette ("Signal" dark or "Signal Light"). Components read the current one through [CtrlColors]. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent for text and icons on this palette's backgrounds (the fill accent is [CtrlColors.Accent] in both). */
    val accentText: Color,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val controlBorder: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    val disabled: Color,
    val switchTrackOff: Color,
    val switchKnobOff: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val pinEmpty: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** Highlighted (jumped-to) message background. */
    val highlight: Color,
)

/**
 * The "Ctrl / Signal" colours shared by every Ctrl app, in a dark and a light palette that follow the device theme.
 * The accent lives in one place ([Accent]) so it can be changed for the whole app at once. The other colours are
 * read from the current palette ([LocalCtrlColors]), so they must be used from composable code.
 */
object CtrlColors {
    /** ElementCtrl's accent fill (hero cards, primary buttons, the selected tab, switch tracks, PIN dots, your bubbles). */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons on accent fills, in both palettes. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        accentText = Accent,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        border = Color(0xFF232328),
        controlBorder = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        disabled = Color(0xFF8C8C94),
        switchTrackOff = Color(0xFF2A2A30),
        switchKnobOff = Color(0xFF8C8C94),
        progressTrack = Color(0xFF26262C),
        progressFill = Color(0xFFF4F4F2),
        pinEmpty = Color(0xFF3A3A41),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF3A1719),
        onErrorContainer = Color(0xFFFFD2D2),
        highlight = Color(0xFF2B2240),
    )

    val Light = CtrlPalette(
        isDark = false,
        // #B48CFF is too pale for text on white.
        accentText = Color(0xFF6A3BDB),
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        border = Color(0xFFDEDED7),
        controlBorder = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        disabled = Color(0xFF74747C),
        switchTrackOff = Color(0xFFDCDCD5),
        switchKnobOff = Color(0xFF74747C),
        progressTrack = Color(0xFFE2E2DC),
        progressFill = Color(0xFF0B0B0D),
        pinEmpty = Color(0xFFC9C9C1),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE3E3),
        onErrorContainer = Color(0xFF5C1010),
        highlight = Color(0xFFEDE4FF),
    )

    /** The palette in use (follows the device's dark/light setting). */
    val current: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.raised
    /** 1 dp borders around cards and bubbles. */
    val Border: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.border
    /** 1 dp borders around controls: buttons, fields, the profile picture, the tab bar. */
    val ControlBorder: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.controlBorder
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.muted
    /** Disabled and stopped states. */
    val Disabled: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.disabled
    val SwitchTrackOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchTrackOff
    val SwitchKnobOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchKnobOff
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.progressTrack
    val ProgressFill: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.progressFill
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.pinEmpty
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.error
}

/** The current Ctrl palette, provided by [ElementCtrlTheme]. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/** ElementCtrl's accent (kept for older call sites). */
val ElementAccent = CtrlColors.Accent

private fun scheme(p: CtrlPalette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    // Material's primary is used for text, icons and outlines (text buttons, radio buttons, progress), so it's the
    // readable accent. Accent fills use CtrlColors.Accent / primaryContainer directly.
    primary = p.accentText,
    onPrimary = if (p.isDark) CtrlColors.OnAccent else Color.White,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = if (p.isDark) CtrlColors.Light.accentText else CtrlColors.Accent,
    secondary = p.text,
    onSecondary = p.background,
    secondaryContainer = p.raised,
    onSecondaryContainer = p.text,
    tertiary = p.accentText,
    onTertiary = if (p.isDark) CtrlColors.OnAccent else Color.White,
    tertiaryContainer = p.highlight,
    onTertiaryContainer = p.text,
    background = p.background,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.raised,
    onSurfaceVariant = p.muted,
    surfaceTint = Color.Transparent,
    surfaceBright = p.raised,
    surfaceDim = p.background,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.raised,
    surfaceContainerHighest = p.switchTrackOff,
    inverseSurface = if (p.isDark) CtrlColors.Light.background else CtrlColors.Dark.raised,
    inverseOnSurface = if (p.isDark) CtrlColors.Light.text else CtrlColors.Dark.text,
    outline = p.border,
    outlineVariant = p.divider,
    scrim = Color(0xCC000000),
    error = p.error,
    onError = p.onError,
    errorContainer = p.errorContainer,
    onErrorContainer = p.onErrorContainer,
)

private val CtrlDarkScheme = scheme(CtrlColors.Dark)
private val CtrlLightScheme = scheme(CtrlColors.Light)

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk: every UI text and heading. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** JetBrains Mono: numbers, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 56, 700, -0.04),
    displayMedium = style(48, 50, 700, -0.04),
    displaySmall = style(36, 40, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(28, 32, 700, -0.025),
    headlineSmall = style(24, 30, 700, -0.02),
    titleLarge = style(18, 24, 600),
    titleMedium = style(16, 22, 600),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500),
)

/** Mono and big-number styles that don't fit Material's type scale. */
object CtrlType {
    /** Section labels: JetBrains Mono 11sp, upper case (call .uppercase() on the text), wide tracking. */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** The "ELEMENT/CTRL" wordmark. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Small mono readouts: times, server addresses, IDs. */
    val Meta = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Trailing mono status in lists. */
    val Status = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
    /** Hero / display headline. */
    val Display = TextStyle(fontFamily = SpaceGrotesk, fontSize = 56.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em)
    /** Giant numeric readout on hero cards. */
    val Number = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** Lock screen keys. */
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
}

/** Corner radii of the Ctrl series. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(16.dp)
    val Cta = RoundedCornerShape(18.dp)
    val Field = RoundedCornerShape(16.dp)
    val Square = RoundedCornerShape(14.dp)
    val Badge = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
    val Dialog = RoundedCornerShape(26.dp)
    val Bubble = 18.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere, as in the other Ctrl apps. */
val Gutter = 20.dp

/** Colours behind initials of people without a picture, chosen from the user ID so each person keeps theirs. */
val SenderColors = listOf(
    Color(0xFF4FD1A5), Color(0xFF6AA8FF), Color(0xFFD08CFF), Color(0xFFFF7FA3),
    Color(0xFFFFA45C), Color(0xFF4FD8DB), Color(0xFF8F8BFF), Color(0xFF9BE15D),
)

/** Darker versions of [SenderColors] for names on light backgrounds. */
private val SenderNameColorsLight = listOf(
    Color(0xFF0E8A64), Color(0xFF2F6FC2), Color(0xFF9A3FCF), Color(0xFFC93E68),
    Color(0xFFB45F14), Color(0xFF13878A), Color(0xFF5A55D6), Color(0xFF4E8F1E),
)

private fun senderIndex(userId: String) = (userId.hashCode() and 0x7fffffff) % SenderColors.size

/** Avatar background for [userId] (initials are drawn in #08080A on it, in both themes). */
fun senderColor(userId: String): Color = SenderColors[senderIndex(userId)]

/** Name colour for [userId], readable on the current palette. */
@Composable
@ReadOnlyComposable
fun senderNameColor(userId: String): Color =
    if (LocalCtrlColors.current.isDark) SenderColors[senderIndex(userId)] else SenderNameColorsLight[senderIndex(userId)]

/** True while the dark palette is in use (it follows the device's dark/light setting). */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean = LocalCtrlColors.current.isDark

/**
 * The Ctrl look, following the device's dark/light setting (no in-app theme choice and no wallpaper colours).
 * [forceDark] keeps a screen dark in both modes (the call screen, which is mostly video).
 */
@Composable
fun ElementCtrlTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) CtrlDarkScheme else CtrlLightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

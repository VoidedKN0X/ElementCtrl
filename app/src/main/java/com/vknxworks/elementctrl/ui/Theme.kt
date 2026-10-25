package com.vknxworks.elementctrl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

/**
 * The "Ctrl / Signal" palette shared by every Ctrl app: near-black, high-contrast, one accent.
 * The accent lives in one place ([Accent]) so it can be changed for the whole app at once.
 */
object CtrlColors {
    /** ElementCtrl's accent. Change it here and every screen follows. */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons on accent fills. */
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    /** 1 dp borders around cards and bubbles. */
    val Border = Color(0xFF232328)
    /** 1 dp borders around controls: buttons, fields, the profile picture, the tab bar. */
    val ControlBorder = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled and stopped states. */
    val Disabled = Color(0xFF8C8C94)
    val SwitchTrackOff = Color(0xFF2A2A30)
    val ProgressTrack = Color(0xFF26262C)
    val PinEmpty = Color(0xFF3A3A41)
    val Error = Color(0xFFFF6B6B)
}

/** ElementCtrl's accent (kept for older call sites). */
val ElementAccent = CtrlColors.Accent

private val CtrlDark = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Raised,
    onSecondaryContainer = CtrlColors.Text,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.OnAccent,
    // Highlighted (jumped-to) messages: a dark accent tint, readable with light text.
    tertiaryContainer = Color(0xFF2B2240),
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.SwitchTrackOff,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.OnAccent,
    outline = CtrlColors.Border,
    outlineVariant = CtrlColors.Divider,
    scrim = Color(0xCC000000),
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF3A1719),
    onErrorContainer = Color(0xFFFFD2D2),
)

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

/** Colours for names in group chats, chosen from the user ID so each person keeps theirs. */
val SenderColors = listOf(
    Color(0xFF4FD1A5), Color(0xFF6AA8FF), Color(0xFFD08CFF), Color(0xFFFF7FA3),
    Color(0xFFFFA45C), Color(0xFF4FD8DB), Color(0xFF8F8BFF), Color(0xFF9BE15D),
)

fun senderColor(userId: String): Color = SenderColors[(userId.hashCode() and 0x7fffffff) % SenderColors.size]

/** The Ctrl look is dark only. Kept so existing callers keep compiling. */
@Composable
fun isDarkTheme(): Boolean = true

/**
 * Dark only, always the Ctrl palette: no light theme and no wallpaper (Material You) colours. The old theme
 * and "wallpaper colours" preferences are left in storage but no longer read.
 */
@Composable
fun ElementCtrlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CtrlDark, typography = AppTypography, shapes = AppShapes, content = content)
}

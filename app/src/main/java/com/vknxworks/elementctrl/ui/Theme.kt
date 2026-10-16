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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.data.ThemeMode

/** Element's green, used when Material You colours are off. */
private val BrandDark = darkColorScheme(
    primary = Color(0xFF5FDDB0),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = Color(0xFF7DFACB),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1F352C),
    secondaryContainer = Color(0xFF354B42),
    onSecondaryContainer = Color(0xFFCFE9DC),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0A3445),
    tertiaryContainer = Color(0xFF254B5C),
    onTertiaryContainer = Color(0xFFC2E8FD),
    background = Color(0xFF101413),
    onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF181D1B),
    surfaceContainer = Color(0xFF1C211F),
    surfaceContainerHigh = Color(0xFF262B29),
    surfaceContainerHighest = Color(0xFF313634),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    error = Color(0xFFFFB4AB),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF006C4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8AF7CF),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF092017),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2E8FD),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181D1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181D1B),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBEFEC),
    surfaceContainerHigh = Color(0xFFE5E9E6),
    surfaceContainerHighest = Color(0xFFDFE4E0),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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
        ThemeMode.DARK -> true
    }
}

@Composable
fun ElementCtrlTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val dynamic by ElementCtrlApp.graph.prefs.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}

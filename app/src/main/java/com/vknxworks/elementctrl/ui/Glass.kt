package com.vknxworks.elementctrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The frosted "island" look shared with the other Ctrl apps: a floating shape that blurs whatever scrolls
 * behind it, tinted with the theme surface, with a soft shadow and a light rim along the top edge.
 */
@Composable
fun Modifier.glassIsland(haze: HazeState, shape: Shape, elevation: Int = 14): Modifier {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val style = HazeStyle(
        backgroundColor = cs.surface,
        tints = listOf(HazeTint(cs.surfaceContainer.copy(alpha = if (dark) 0.55f else 0.62f))),
        blurRadius = 26.dp,
        noiseFactor = 0.04f,
    )
    return this
        .shadow(elevation.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
        .clip(shape)
        .hazeEffect(state = haze, style = style)
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.7f), cs.outlineVariant.copy(alpha = 0.25f))),
            shape,
        )
}

/** The blurred backdrop shared by floating glass surfaces (the tab bar). Null when not available. */
val LocalHazeState = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/**
 * Space taken at the bottom of the screen by the floating tab bar. Scrolling screens add it to their bottom
 * content padding, so their last items can scroll clear of the glass.
 */
val LocalBottomClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * Frosted glass, the same as the other Ctrl apps' tab bars: blurs whatever scrolls behind, tints it with the
 * surface colour, and adds a hairline edge and soft shadow so it floats. A translucent fill where blur isn't available.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    elevation: androidx.compose.ui.unit.Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier {
    val state = LocalHazeState.current
    val dark = isDarkTheme()
    val edge = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val highlight = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.35f)
    val base = this
        .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
        .clip(shape)
    val blurred = if (state != null) {
        base.hazeEffect(
            state,
            HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(tint.copy(alpha = if (dark) 0.62f else 0.70f)),
                blurRadius = 28.dp,
                noiseFactor = 0.04f,
                fallbackTint = HazeTint(tint.copy(alpha = 0.94f)),
            ),
        )
    } else {
        base.background(tint.copy(alpha = 0.96f))
    }
    return blurred
        .background(Brush.verticalGradient(listOf(highlight, Color.Transparent)))
        .border(1.dp, edge, shape)
}

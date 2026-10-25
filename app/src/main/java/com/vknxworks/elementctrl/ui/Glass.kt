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
 * The floating Ctrl island (composer, notes above the keyboard): the surface colour at ~92 % over a light blur of
 * whatever scrolls behind, a 1 dp control border, no glow. [elevation] is kept for callers but no longer drawn.
 */
@Composable
fun Modifier.glassIsland(haze: HazeState, shape: Shape, @Suppress("UNUSED_PARAMETER") elevation: Int = 14): Modifier {
    val style = HazeStyle(
        backgroundColor = CtrlColors.Background,
        tints = listOf(HazeTint(CtrlColors.Surface.copy(alpha = 0.92f))),
        blurRadius = 20.dp,
        noiseFactor = 0f,
    )
    return this
        .clip(shape)
        .hazeEffect(state = haze, style = style)
        .border(1.dp, CtrlColors.ControlBorder, shape)
}

/** The blurred backdrop shared by floating glass surfaces (the tab bar). Null when not available. */
val LocalHazeState = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/**
 * Space taken at the bottom of the screen by the floating tab bar. Scrolling screens add it to their bottom
 * content padding, so their last items can scroll clear of the glass.
 */
val LocalBottomClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** A floating Ctrl surface: surface colour at ~92 % (blurred behind where available) with a 1 dp border. */
@Composable
fun Modifier.glass(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.ui.unit.Dp = 18.dp,
    tint: Color = CtrlColors.Surface,
): Modifier {
    val state = LocalHazeState.current
    val base = this.clip(shape)
    val filled = if (state != null) {
        base.hazeEffect(
            state,
            HazeStyle(
                backgroundColor = CtrlColors.Background,
                tint = HazeTint(tint.copy(alpha = 0.92f)),
                blurRadius = 20.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(tint.copy(alpha = 0.96f)),
            ),
        )
    } else {
        base.background(tint.copy(alpha = 0.96f))
    }
    return filled.border(1.dp, CtrlColors.ControlBorder, shape)
}

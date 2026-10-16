package com.vknxworks.elementctrl.ui

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

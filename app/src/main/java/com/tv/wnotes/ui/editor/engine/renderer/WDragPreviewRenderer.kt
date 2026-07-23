package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.state.WDragPreviewState
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot

/**
 * Renders the floating drag preview.
 */
@Composable
fun WDragPreviewRenderer(
    previewState: WDragPreviewState,
    snapshot: WEditorSnapshot,
    animController: WDragAnimationController,
    modifier: Modifier = Modifier
) {
    if (!previewState.isVisible || previewState.content == null) return

    SideEffect {
        println("STAGE 9: Floating preview rendered at ${previewState.position}")
    }

    val content = previewState.content
    val fingerPosition = previewState.position
    val fingerOffset = previewState.fingerOffset
    val density = LocalDensity.current

    // Animated properties from controller
    val scale by animController.scale.asState()
    val opacity by animController.opacity.asState()
    val animatedPos by animController.dragOffset.asState()

    // Position calculation:
    // If animatedPos is Zero (initial frames), fallback to fingerPosition
    val currentPos = if (animatedPos == androidx.compose.ui.geometry.Offset.Zero) fingerPosition else animatedPos
    
    // The preview floats at finger position + relative offset (top-left of selection)
    val displayOffset = currentPos + fingerOffset

    Surface(
        modifier = Modifier
            .wrapContentSize()
            .graphicsLayer {
                translationX = displayOffset.x
                translationY = displayOffset.y - 20.dp.toPx() // Float slightly ABOVE finger for visibility
                scaleX = scale
                scaleY = scale
                alpha = opacity
            }
            .widthIn(max = 280.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content.blocks.forEach { block ->
                WBlockRenderer(
                    block = block,
                    snapshot = snapshot,
                    onTextLayout = {},
                    forceFullOpacity = true
                )
            }
        }
    }
}

package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.state.WInsertionCaretState

/**
 * Renders the predictive insertion beam during a drag-and-drop session.
 */
@Composable
fun WInsertionCaretRenderer(
    caretState: WInsertionCaretState,
    onRendered: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!caretState.isVisible) return

    LaunchedEffect(caretState.isVisible, caretState.drawRect) {
        if (caretState.isVisible) {
            println("DROP STAGE 7: Caret Rendered at ${caretState.drawRect}")
            onRendered()
        }
    }

    val beamColor = if (caretState.isValidTarget) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Red.copy(alpha = 0.5f) // Visual warning for invalid drop target
    }

    val alpha by animateFloatAsState(
        targetValue = if (caretState.isVisible) 1.0f else 0f,
        label = "InsertionCaretAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val rect = caretState.drawRect
        if (rect != Rect.Zero) {
            drawRect(
                color = beamColor,
                topLeft = rect.topLeft,
                size = rect.size.copy(width = 2.dp.toPx()),
                alpha = alpha
            )
            
            // Subtle glow/indicator at the top and bottom of the beam
            drawCircle(
                color = beamColor,
                radius = 3.dp.toPx(),
                center = rect.topCenter,
                alpha = alpha * 0.5f
            )
            drawCircle(
                color = beamColor,
                radius = 3.dp.toPx(),
                center = rect.bottomCenter,
                alpha = alpha * 0.5f
            )
        }
    }
}

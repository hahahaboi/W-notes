package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.state.WSelectionHandleState
import com.tv.wnotes.ui.editor.engine.state.WSelectionState

/**
 * Renders custom selection handles.
 */
@Composable
fun WSelectionHandleRenderer(
    selectionState: WSelectionState,
    modifier: Modifier = Modifier
) {
    if (!selectionState.isActive) return

    val handleColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        if (selectionState.startHandle.isVisible) {
            drawHandle(selectionState.startHandle, handleColor)
        }
        if (selectionState.endHandle.isVisible) {
            drawHandle(selectionState.endHandle, handleColor)
        }
    }
}

private fun DrawScope.drawHandle(
    handle: WSelectionHandleState,
    color: Color
) {
    val pos = handle.position
    if (pos == Offset.Unspecified) return

    val knobRadius = 6.dp.toPx()
    val stemWidth = 2.dp.toPx()
    val stemHeight = 20.dp.toPx() // Approximate, should ideally match text height

    // Draw knob
    drawCircle(
        color = color,
        radius = knobRadius,
        center = pos
    )

    // Draw stem (vertical line up from knob)
    drawLine(
        color = color,
        start = pos,
        end = Offset(pos.x, pos.y - stemHeight),
        strokeWidth = stemWidth
    )
}

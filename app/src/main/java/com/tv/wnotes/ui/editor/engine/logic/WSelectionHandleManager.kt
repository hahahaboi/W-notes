package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Logic for positioning and hit-testing selection handles.
 */
object WSelectionHandleManager {
    
    private val HANDLE_TOUCH_SLOP = 24.dp // Larger hit area for touch

    /**
     * Calculates the physical screen positions for START and END handles.
     */
    fun calculateHandlePositions(
        snapshot: WEditorSnapshot,
        layoutProvider: WLayoutProvider
    ): Pair<Offset, Offset> {
        if (!snapshot.selection.isActive || snapshot.selection.ranges.isEmpty()) {
            return Offset.Unspecified to Offset.Unspecified
        }

        val rangeId = snapshot.selection.activeRangeId ?: snapshot.selection.ranges.first().id
        val activeRange = snapshot.selection.ranges.find { it.id == rangeId } ?: snapshot.selection.ranges.first()
        val range = WSelectionManager.normalize(activeRange, snapshot.document.blocks)
        
        val startRect = layoutProvider.getInsertionBeamForPoint(range.anchor)
        val endRect = layoutProvider.getInsertionBeamForPoint(range.focus)

        val startPos = startRect?.bottomCenter ?: Offset.Unspecified
        val endPos = endRect?.bottomCenter ?: Offset.Unspecified

        return startPos to endPos
    }

    /**
     * Determines if an offset hits a handle.
     */
    fun hitTestHandles(
        offset: Offset,
        state: WSelectionState,
        density: Float
    ): WSelectionHandleType? {
        if (!state.isActive) return null

        val slopPx = 24f * density // Approximate slop

        if (state.startHandle.position != Offset.Unspecified) {
            val dist = (offset - state.startHandle.position).getDistance()
            if (dist < slopPx) return WSelectionHandleType.START
        }

        if (state.endHandle.position != Offset.Unspecified) {
            val dist = (offset - state.endHandle.position).getDistance()
            if (dist < slopPx) return WSelectionHandleType.END
        }

        return null
    }
}

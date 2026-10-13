package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange

/**
 * Represents an active drag-and-drop session.
 */
data class WDragSession(
    val selectedRanges: List<WSelectionRange>,
    val initialFingerPosition: Offset
)

/**
 * Semantic events emitted by the DragManager.
 */
sealed interface WDragEvent {
    data class ScrollUpRequested(val speed: Float) : WDragEvent
    data class ScrollDownRequested(val speed: Float) : WDragEvent
    data class DropExecuted(
        val sourceRanges: List<WSelectionRange>, 
        val targetPoint: WSelectionPoint,
        val virtualLineCount: Int = 0,
        val isCopy: Boolean = false
    ) : WDragEvent

    data class CrossTabDropExecuted(
        val sourceTabId: Long,
        val targetTabId: Long,
        val sourceRanges: List<WSelectionRange>,
        val targetPoint: WSelectionPoint,
        val isCopy: Boolean = false
    ) : WDragEvent

    data object DragCancelled : WDragEvent
}

enum class WDragInternalState {
    Idle,
    PreparingLift,
    Lifting,
    Dragging,
    Dropped,
    Cancelled
}

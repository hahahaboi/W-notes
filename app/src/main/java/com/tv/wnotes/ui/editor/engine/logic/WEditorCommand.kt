package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange

/**
 * Commands emitted by the GestureCoordinator or Keyboard interceptors.
 * These are processed by the EditorState to produce new snapshots.
 */
sealed interface WEditorCommand {
    data class MoveCursor(
        val point: WSelectionPoint,
        val affinity: CursorAffinity = CursorAffinity.AFTER
    ) : WEditorCommand
    data class SelectWord(val point: WSelectionPoint) : WEditorCommand
    data class SelectParagraph(val point: WSelectionPoint) : WEditorCommand
    data class BeginSelection(val point: WSelectionPoint) : WEditorCommand
    data class ExtendSelection(val point: WSelectionPoint) : WEditorCommand
    data class BeginDragSelection(val point: WSelectionPoint, val position: Offset) : WEditorCommand
    
    // Drag Lifecycle Commands
    data class StartPreparingLift(val range: WSelectionRange, val position: Offset) : WEditorCommand
    data object ExecuteLift : WEditorCommand
    data class UpdateLiftProgress(val progress: Float) : WEditorCommand
    data class UpdateDragPosition(val position: Offset) : WEditorCommand
    data class CompleteDrag(val targetPoint: WSelectionPoint?) : WEditorCommand
    data object CompleteDropCleanup : WEditorCommand
    data object CancelDrag : WEditorCommand

    data class UpdateActiveHandle(val handleType: com.tv.wnotes.ui.editor.engine.state.WSelectionHandleType?) : WEditorCommand
    data class MoveSelectionHandle(val handleType: com.tv.wnotes.ui.editor.engine.state.WSelectionHandleType, val point: WSelectionPoint) : WEditorCommand
    data object ClearSelection : WEditorCommand
    data object SelectAll : WEditorCommand
    
    // Keyboard Actions
    data object SplitBlock : WEditorCommand
    data object DeleteBackward : WEditorCommand
    data object DeleteForward : WEditorCommand
    
    // Navigation
    data class NavigateCursor(val direction: NavigationDirection) : WEditorCommand

    // Telemetry
    data class UpdateTelemetry(val transform: (com.tv.wnotes.ui.editor.engine.state.WTelemetryState) -> com.tv.wnotes.ui.editor.engine.state.WTelemetryState) : WEditorCommand
}

enum class NavigationDirection {
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN
}

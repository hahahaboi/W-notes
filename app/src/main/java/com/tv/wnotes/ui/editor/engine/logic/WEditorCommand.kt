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
    data class TapBelowDocument(val offset: Offset) : WEditorCommand
    
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
    
    // Multi-Range Selection (Stage XX)
    data class ActivateSelection(val rangeId: String) : WEditorCommand
    data class RemoveSelectionRange(val rangeId: String) : WEditorCommand
    data class SetSelectionInteraction(val mode: com.tv.wnotes.ui.editor.engine.state.WSelectionInteraction) : WEditorCommand
    data class ApplySpan(val spanType: com.tv.wnotes.ui.editor.engine.model.SpanType) : WEditorCommand

    // Block Management
    data class InsertBlock(val block: com.tv.wnotes.ui.editor.engine.model.WBlock, val index: Int? = null) : WEditorCommand
    data class UpdateBlock(val block: com.tv.wnotes.ui.editor.engine.model.WBlock) : WEditorCommand

    data object ClearSelection : WEditorCommand
    data object ClearSelectionAndHideCursor : WEditorCommand
    data object SelectAll : WEditorCommand
    data object Copy : WEditorCommand
    data object Cut : WEditorCommand
    data object Paste : WEditorCommand
    data object MakeTabFromSelection : WEditorCommand
    
    // Keyboard Actions
    data object SplitBlock : WEditorCommand
    data object DeleteBackward : WEditorCommand
    data object DeleteForward : WEditorCommand
    
    // Navigation
    data class NavigateCursor(val direction: NavigationDirection) : WEditorCommand

    // Context Annotations
    data class CreateAnnotation(val content: String, val range: WSelectionRange, val anchorText: String) : WEditorCommand
    data class UpdateAnnotation(val id: String, val content: String) : WEditorCommand
    data class DeleteAnnotation(val id: String) : WEditorCommand
    data class ShowAnnotation(val id: String) : WEditorCommand
    data object DismissAnnotation : WEditorCommand
    data class StartCreatingAnnotation(val range: WSelectionRange, val anchorText: String) : WEditorCommand
    data object CancelCreatingAnnotation : WEditorCommand

    // Text Markers
    data object ToggleMarkerSidebar : WEditorCommand
    data class CreateMarker(val label: String, val range: WSelectionRange, val anchorText: String) : WEditorCommand
    data class UpdateMarkerLabel(val id: String, val newLabel: String) : WEditorCommand
    data class UpdateMarkerType(val id: String, val newType: com.tv.wnotes.data.local.entity.MarkerType) : WEditorCommand
    data class DeleteMarker(val id: String) : WEditorCommand
    data class NavigateToMarker(val markerId: String) : WEditorCommand
    data object NavigateToNextMarker : WEditorCommand
    data object NavigateToPreviousMarker : WEditorCommand
    data object OpenQuickJump : WEditorCommand
    data object CloseQuickJump : WEditorCommand

    // Telemetry
    data class UpdateTelemetry(val transform: (com.tv.wnotes.ui.editor.engine.state.WTelemetryState) -> com.tv.wnotes.ui.editor.engine.state.WTelemetryState) : WEditorCommand
}

enum class NavigationDirection {
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN
}

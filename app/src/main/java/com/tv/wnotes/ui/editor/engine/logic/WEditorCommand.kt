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
    data class SetSelection(val selection: com.tv.wnotes.ui.editor.engine.state.WSelectionState) : WEditorCommand
    data class ApplySpan(val spanType: com.tv.wnotes.ui.editor.engine.model.SpanType) : WEditorCommand

    data class InsertBlock(val block: com.tv.wnotes.ui.editor.engine.model.WBlock, val index: Int? = null) : WEditorCommand
    data class UpdateBlock(val block: com.tv.wnotes.ui.editor.engine.model.WBlock) : WEditorCommand
    data class ToggleBlockType(val type: com.tv.wnotes.ui.editor.engine.model.TextBlockType) : WEditorCommand
    data object IncreaseIndent : WEditorCommand
    data object DecreaseIndent : WEditorCommand
    data class SetAlignment(val alignment: String) : WEditorCommand
    data class SetFontSize(val size: Int) : WEditorCommand
    data class AdjustFontSize(val delta: Int) : WEditorCommand
    data class ApplyFont(val fontFamily: String) : WEditorCommand

    data class UpdateTitle(val title: String, val cursorOffset: Int? = null) : WEditorCommand
    data object Undo : WEditorCommand
    data object Redo : WEditorCommand
    data object AIAssist : WEditorCommand
    data object AddTextBox : WEditorCommand
    data class SetDrawingMode(val active: Boolean) : WEditorCommand
    data object ActivateNormalText : WEditorCommand

    data object ClearSelection : WEditorCommand
    data object ClearSelectionAndHideCursor : WEditorCommand
    data object SelectAll : WEditorCommand
    data object Copy : WEditorCommand
    data object Cut : WEditorCommand
    data object Paste : WEditorCommand
    
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

    data class SetNavigationHighlight(val range: WSelectionRange?) : WEditorCommand
    data class SetSearchHighlights(val ranges: List<WSelectionRange>, val activeRange: WSelectionRange?) : WEditorCommand

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

    // Tab Ties
    data object StartTieTabs : WEditorCommand
    data object CancelTieTabs : WEditorCommand
    data class PlaceTabTie(val destinationTabId: Long, val range: WSelectionRange, val anchorText: String) : WEditorCommand
    data class ShowTabTieDestinations(val tieIds: Set<String>) : WEditorCommand
    data class JumpToTabTie(val tieId: String) : WEditorCommand

    data object MakeTabFromSelection : WEditorCommand
    data class CreateTabWithSelection(val selectedBlocks: List<com.tv.wnotes.ui.editor.engine.model.WBlock>) : WEditorCommand
    data class AddToTab(val selectedBlocks: List<com.tv.wnotes.ui.editor.engine.model.WBlock>) : WEditorCommand

    // Telemetry
    data class UpdateTelemetry(val transform: (com.tv.wnotes.ui.editor.engine.state.WTelemetryState) -> com.tv.wnotes.ui.editor.engine.state.WTelemetryState) : WEditorCommand

    // Zoom
    data class UpdateZoom(val zoom: Float) : WEditorCommand

    // Goal
    data class SetGoal(val point: WSelectionPoint) : WEditorCommand
    data class MoveGoal(val point: WSelectionPoint) : WEditorCommand
    object RemoveGoal : WEditorCommand
}

enum class NavigationDirection {
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN
}

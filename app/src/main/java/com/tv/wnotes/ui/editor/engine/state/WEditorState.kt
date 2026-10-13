package com.tv.wnotes.ui.editor.engine.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.ui.editor.engine.model.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Immutable snapshot of the entire editor state.
 */
data class WEditorSnapshot(
    val document: WDocument,
    val annotations: ImmutableList<ContextAnnotation> = persistentListOf(),
    val markers: ImmutableList<TextMarker> = persistentListOf(),
    val ties: ImmutableList<TabTie> = persistentListOf(),
    val isMarkerSidebarOpen: Boolean = false,
    val isDrawingMode: Boolean = false,
    val selection: WSelectionState,
    val cursor: WCursorState,
    val drag: WDragState,
    val viewport: WViewportState,
    val clipboard: WClipboardState,
    val settings: WEditorSettings,
    val history: WHistory,
    val telemetry: WTelemetryState = WTelemetryState(),
    val lastMutation: com.tv.wnotes.ui.editor.engine.logic.WMutationResult? = null,
    val activeAnnotationId: String? = null,
    val activeTieIds: Set<String>? = null,
    val isTyingTabs: Boolean = false,
    val isCreatingAnnotation: Boolean = false,
    val pendingAnnotationRange: WSelectionRange? = null,
    val pendingAnnotationAnchorText: String = "",
    val navigationHighlightRange: WSelectionRange? = null,
    val activeMarkerId: String? = null,
    val isQuickJumpOpen: Boolean = false,
    val searchHighlightRanges: ImmutableList<WSelectionRange> = persistentListOf(),
    val activeSearchHighlightRange: WSelectionRange? = null,
    val goal: WGoal? = null
) {
    val annotationIndex: Map<String, List<ContextAnnotation>> by lazy {
        annotations.groupBy { it.blockId }
    }

}

/**
 * The single source of truth for the WEditor.
 * Manages document modifications and state transitions.
 */
class WEditorState(initialSnapshot: WEditorSnapshot) {
    
    constructor(
        initialDocument: WDocument, 
        initialAnnotations: List<ContextAnnotation> = emptyList(),
        initialMarkers: List<TextMarker> = emptyList(),
        initialTies: List<TabTie> = emptyList(),
        cursorAtEnd: Boolean = false
    ) : this(
        WEditorSnapshot(
            document = initialDocument,
            annotations = initialAnnotations.toImmutableList(),
            markers = initialMarkers.toImmutableList(),
            ties = initialTies.toImmutableList(),
            selection = WSelectionState(),
            cursor = WCursorState(position = if (cursorAtEnd) {
                val lastBlock = initialDocument.blocks.lastOrNull()
                val text = (lastBlock as? com.tv.wnotes.ui.editor.engine.model.WTextContainer)?.text ?: ""
                WSelectionPoint(lastBlock?.id ?: "", text.length)
            } else {
                WSelectionPoint(
                    blockId = initialDocument.blocks.firstOrNull()?.id ?: "",
                    offset = 0
                )
            }),
            drag = WDragState(),
            viewport = WViewportState(),
            clipboard = WClipboardState(),
            settings = WEditorSettings(),
            history = WHistory(),
            telemetry = WTelemetryState()
        )
    )
    
    private val _snapshot = mutableStateOf(reconcileSelection(initialSnapshot))
    
    val snapshot: State<WEditorSnapshot> = _snapshot

    fun updateSnapshot(newSnapshot: WEditorSnapshot) {
        _snapshot.value = reconcileSelection(newSnapshot)
    }

    fun updateTelemetry(transform: (WTelemetryState) -> WTelemetryState) {
        _snapshot.value = _snapshot.value.copy(telemetry = transform(_snapshot.value.telemetry))
    }

    fun resetTelemetry() {
        _snapshot.value = _snapshot.value.copy(telemetry = WTelemetryState())
    }

    fun setGoal(point: WSelectionPoint) {
        _snapshot.value = _snapshot.value.copy(goal = WGoal(point.blockId, point.offset))
    }

    fun moveGoal(point: WSelectionPoint) {
        _snapshot.value = _snapshot.value.copy(goal = WGoal(point.blockId, point.offset))
    }

    fun removeGoal() {
        _snapshot.value = _snapshot.value.copy(goal = null)
    }

    fun applyMutation(result: com.tv.wnotes.ui.editor.engine.logic.WMutationResult) {
        when (result) {
            is com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Success -> {
                _snapshot.value = result.snapshot.copy(lastMutation = result)
            }
            is com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Failure -> {
                _snapshot.value = _snapshot.value.copy(lastMutation = result)
            }
        }
    }

    fun setFontSize(size: Int) {
        val result = com.tv.wnotes.ui.editor.engine.logic.WMutationEngine.applyFontSize(_snapshot.value, size)
        applyMutation(result)
    }

    fun adjustFontSize(delta: Int) {
        val result = com.tv.wnotes.ui.editor.engine.logic.WMutationEngine.adjustFontSize(_snapshot.value, delta)
        applyMutation(result)
    }

    fun moveCursor(point: WSelectionPoint, affinity: com.tv.wnotes.ui.editor.engine.logic.CursorAffinity) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            cursor = WCursorState(
                position = point,
                affinity = affinity,
                blinkVisible = true,
                isVisible = true
            ),
            selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.collapseTo(point)
        )
    }

    fun selectWord(point: WSelectionPoint) {
        val current = _snapshot.value
        val newSelection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.selectWord(
            point, 
            current.document.blocks,
            current.document.metadata.title
        )
        
        val mergedSelection = if (current.selection.interactionMode == WSelectionInteraction.ADD_RANGE && current.selection.isActive) {
            val combinedRanges = current.selection.ranges + newSelection.ranges
            val mergedRanges = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.mergeOverlappingSelections(combinedRanges, current.document.blocks)
            current.selection.copy(
                ranges = mergedRanges,
                activeRangeId = newSelection.activeRangeId, // The new one becomes active
                isActive = true,
                depth = WSelectionDepth.WORD,
                interactionMode = WSelectionInteraction.ADD_RANGE // STRICTLY PRESERVE ADD MODE
            )
        } else {
            newSelection
        }

        _snapshot.value = current.copy(
            selection = mergedSelection,
            cursor = current.cursor.copy(isVisible = !mergedSelection.isActive)
        )
    }

    fun selectParagraph(point: WSelectionPoint) {
        val current = _snapshot.value
        val newSelection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.selectParagraph(
            point,
            current.document.blocks,
            current.document.metadata.title
        )

        val mergedSelection = if (current.selection.interactionMode == WSelectionInteraction.ADD_RANGE && current.selection.isActive) {
            val combinedRanges = current.selection.ranges + newSelection.ranges
            val mergedRanges = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.mergeOverlappingSelections(combinedRanges, current.document.blocks)
            current.selection.copy(
                ranges = mergedRanges,
                activeRangeId = newSelection.activeRangeId,
                isActive = true,
                depth = WSelectionDepth.PARAGRAPH,
                interactionMode = WSelectionInteraction.ADD_RANGE // STRICTLY PRESERVE ADD MODE
            )
        } else {
            newSelection
        }

        _snapshot.value = current.copy(
            selection = mergedSelection,
            cursor = current.cursor.copy(isVisible = !mergedSelection.isActive)
        )
    }

    fun extendSelection(point: WSelectionPoint) {
        val current = _snapshot.value
        // If we have an active range, we extend ONLY that one.
        val activeRange = current.selection.ranges.find { it.id == current.selection.activeRangeId } ?: current.selection.ranges.lastOrNull()
        
        if (activeRange == null) {
            val selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.collapseTo(point)
            _snapshot.value = current.copy(selection = selection, cursor = current.cursor.copy(isVisible = true))
            return
        }

        val updatedRange = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.extendSelection(
            current.selection.copy(ranges = listOf(activeRange)), 
            point, 
            current.document.blocks,
            current.document.metadata.title
        ).ranges.first()

        val newRanges = current.selection.ranges.map { if (it.id == activeRange.id) updatedRange else it }
        
        _snapshot.value = current.copy(
            selection = current.selection.copy(ranges = newRanges, isActive = true),
            cursor = current.cursor.copy(isVisible = false)
        )
    }

    fun updateActiveHandle(handleType: WSelectionHandleType?) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            selection = current.selection.copy(activeHandle = handleType)
        )
    }

    fun moveSelectionHandle(handleType: WSelectionHandleType, point: WSelectionPoint) {
        val current = _snapshot.value
        val rangeId = current.selection.activeRangeId ?: current.selection.ranges.firstOrNull()?.id ?: return
        val range = current.selection.ranges.find { it.id == rangeId } ?: return
        
        val newRange = if (handleType == WSelectionHandleType.START) {
            range.copy(anchor = point)
        } else {
            range.copy(focus = point)
        }
        
        val newRanges = current.selection.ranges.map { if (it.id == rangeId) newRange else it }
        
        val newSelection = current.selection.copy(
            ranges = newRanges,
            isActive = true,
            depth = WSelectionDepth.CHARACTER
        )
        
        _snapshot.value = current.copy(
            selection = newSelection,
            cursor = current.cursor.copy(isVisible = false)
        )
    }

    fun updateHandlePositions(start: Offset, end: Offset) {
        val current = _snapshot.value
        if (!current.selection.isActive) return
        
        _snapshot.value = current.copy(
            selection = current.selection.copy(
                startHandle = current.selection.startHandle.copy(position = start, isVisible = start != Offset.Unspecified),
                endHandle = current.selection.endHandle.copy(position = end, isVisible = end != Offset.Unspecified)
            )
        )
    }

    fun clearSelection() {
        val current = _snapshot.value
        val cursorPosition = current.selection.ranges.firstOrNull()?.focus ?: current.cursor.position
        _snapshot.value = current.copy(
            selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.collapseTo(cursorPosition),
            cursor = current.cursor.copy(isVisible = true, position = cursorPosition)
        )
    }

    fun clearSelectionAndHideCursor() {
        val current = _snapshot.value
        val cursorPosition = current.cursor.position
        _snapshot.value = current.copy(
            selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.collapseTo(cursorPosition),
            cursor = current.cursor.copy(isVisible = false)
        )
    }

    // --- Multi-Range Selection APIs (Stage XX) ---

    fun setInteractionMode(mode: WSelectionInteraction) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            selection = current.selection.copy(interactionMode = mode)
        )
    }

    fun setSelection(selection: WSelectionState) {
        val current = _snapshot.value
        val reconciledSelection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.reconcile(
            selection,
            current.document.blocks,
            current.document.metadata.title
        )
        _snapshot.value = current.copy(
            selection = reconciledSelection,
            cursor = current.cursor.copy(isVisible = !reconciledSelection.isActive)
        )
    }

    private fun reconcileSelection(snapshot: WEditorSnapshot): WEditorSnapshot = snapshot.copy(
        selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.reconcile(
            snapshot.selection,
            snapshot.document.blocks,
            snapshot.document.metadata.title
        )
    )

    fun activateSelection(rangeId: String) {
        val current = _snapshot.value
        if (current.selection.activeRangeId == rangeId) return
        
        _snapshot.value = current.copy(
            selection = current.selection.copy(activeRangeId = rangeId)
        )
    }

    fun removeSelectionRange(rangeId: String) {
        val current = _snapshot.value
        val newRanges = current.selection.ranges.filter { it.id != rangeId }
        val newActiveId = if (current.selection.activeRangeId == rangeId) {
            newRanges.firstOrNull()?.id
        } else {
            current.selection.activeRangeId
        }

        _snapshot.value = current.copy(
            selection = current.selection.copy(
                ranges = newRanges,
                activeRangeId = newActiveId,
                isActive = newRanges.isNotEmpty()
            )
        )
    }

    // --- Context Annotation APIs ---

    fun showAnnotation(id: String) {
        val current = _snapshot.value
        _snapshot.value = current.copy(activeAnnotationId = id)
    }

    fun dismissAnnotation() {
        val current = _snapshot.value
        _snapshot.value = current.copy(activeAnnotationId = null)
    }

    fun setActiveTieIds(ids: Set<String>?) {
        _snapshot.value = _snapshot.value.copy(activeTieIds = ids)
    }

    fun dismissTiePopup() {
        _snapshot.value = _snapshot.value.copy(activeTieIds = null)
    }

    fun setTyingTabs(isTying: Boolean) {
        _snapshot.value = _snapshot.value.copy(isTyingTabs = isTying)
    }

    fun startCreatingAnnotation(range: WSelectionRange, anchorText: String) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            isCreatingAnnotation = true,
            pendingAnnotationRange = range,
            pendingAnnotationAnchorText = anchorText
        )
    }

    fun cancelCreatingAnnotation() {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            isCreatingAnnotation = false,
            pendingAnnotationRange = null,
            pendingAnnotationAnchorText = ""
        )
    }

    // --- Text Marker APIs ---

    fun toggleMarkerSidebar() {
        val current = _snapshot.value
        _snapshot.value = current.copy(isMarkerSidebarOpen = !current.isMarkerSidebarOpen)
    }

    fun setDrawingMode(active: Boolean) {
        val current = _snapshot.value
        _snapshot.value = current.copy(isDrawingMode = active)
    }

    fun setNavigationHighlight(range: WSelectionRange?) {
        val current = _snapshot.value
        _snapshot.value = current.copy(navigationHighlightRange = range)
    }

    fun setSearchHighlights(ranges: List<WSelectionRange>, activeRange: WSelectionRange?) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            searchHighlightRanges = ranges.toImmutableList(),
            activeSearchHighlightRange = activeRange,
            navigationHighlightRange = activeRange
        )
    }

    fun setActiveMarker(id: String?) {
        val current = _snapshot.value
        if (current.activeMarkerId == id) return
        _snapshot.value = current.copy(activeMarkerId = id)
    }

    fun setQuickJumpOpen(isOpen: Boolean) {
        val current = _snapshot.value
        if (current.isQuickJumpOpen == isOpen) return
        _snapshot.value = current.copy(isQuickJumpOpen = isOpen)
    }

    fun updateZoom(zoom: Float) {
        val current = _snapshot.value
        val clampedZoom = zoom.coerceIn(0.5f, 2.0f)
        if (current.settings.zoom == clampedZoom) return
        _snapshot.value = current.copy(
            settings = current.settings.copy(zoom = clampedZoom)
        )
    }

    // Note: Other high-level methods like insertText() will be refined 
    // to use specialized Managers (SelectionManager, ContentManager) 
    // to keep this class clean.
}

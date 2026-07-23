package com.tv.wnotes.ui.editor.engine.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.model.*
import kotlinx.collections.immutable.toImmutableList

/**
 * Immutable snapshot of the entire editor state.
 */
data class WEditorSnapshot(
    val document: WDocument,
    val selection: WSelectionState,
    val cursor: WCursorState,
    val drag: WDragState,
    val viewport: WViewportState,
    val clipboard: WClipboardState,
    val settings: WEditorSettings,
    val history: WHistory,
    val telemetry: WTelemetryState = WTelemetryState(),
    val lastMutation: com.tv.wnotes.ui.editor.engine.logic.WMutationResult? = null
)

/**
 * The single source of truth for the WEditor.
 * Manages document modifications and state transitions.
 */
class WEditorState(initialDocument: WDocument) {
    
    private val _snapshot = mutableStateOf(
        WEditorSnapshot(
            document = initialDocument,
            selection = WSelectionState(),
            cursor = WCursorState(position = WSelectionPoint(
                blockId = initialDocument.blocks.firstOrNull()?.id ?: "",
                offset = 0
            )),
            drag = WDragState(),
            viewport = WViewportState(),
            clipboard = WClipboardState(),
            settings = WEditorSettings(),
            history = WHistory(),
            telemetry = WTelemetryState()
        )
    )
    
    val snapshot: State<WEditorSnapshot> = _snapshot

    fun updateSnapshot(newSnapshot: WEditorSnapshot) {
        _snapshot.value = newSnapshot
    }

    fun updateTelemetry(transform: (WTelemetryState) -> WTelemetryState) {
        _snapshot.value = _snapshot.value.copy(telemetry = transform(_snapshot.value.telemetry))
    }

    fun resetTelemetry() {
        _snapshot.value = _snapshot.value.copy(telemetry = WTelemetryState())
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
        val selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.selectWord(point, current.document.blocks)
        _snapshot.value = current.copy(
            selection = selection,
            cursor = current.cursor.copy(isVisible = !selection.isActive)
        )
    }

    fun selectParagraph(point: WSelectionPoint) {
        val current = _snapshot.value
        val selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.selectParagraph(point)
        _snapshot.value = current.copy(
            selection = selection,
            cursor = current.cursor.copy(isVisible = !selection.isActive)
        )
    }

    fun extendSelection(point: WSelectionPoint) {
        val current = _snapshot.value
        val selection = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.extendSelection(current.selection, point)
        _snapshot.value = current.copy(
            selection = selection,
            cursor = current.cursor.copy(isVisible = !selection.isActive)
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
        val range = current.selection.ranges.firstOrNull() ?: return
        
        val newRange = if (handleType == WSelectionHandleType.START) {
            range.copy(anchor = point)
        } else {
            range.copy(focus = point)
        }
        
        val newSelection = current.selection.copy(
            ranges = listOf(newRange),
            isActive = true,
            mode = WSelectionMode.CHARACTER
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

    // Note: Other high-level methods like insertText() will be refined 
    // to use specialized Managers (SelectionManager, ContentManager) 
    // to keep this class clean.
}

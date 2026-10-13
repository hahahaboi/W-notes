package com.tv.wnotes.ui.editor.engine.input

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.tv.wnotes.ui.editor.engine.clipboard.WClipboardManager
import com.tv.wnotes.ui.editor.engine.logic.WHistoryManager
import com.tv.wnotes.ui.editor.engine.logic.WLayoutProvider
import com.tv.wnotes.ui.editor.engine.logic.WMutationEngine
import com.tv.wnotes.ui.editor.engine.logic.WMutationResult
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.logic.WSpanManager
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Gateway between Android input events and the WEditor engine.
 * Manages focus, IME connection, and command dispatching.
 */
class WInputController(
    private val editorState: WEditorState,
    private val historyManager: WHistoryManager,
    private val clipboardManager: WClipboardManager,
) {
    private var isBatchEditing = false

    /**
     * Called by WInputConnection or UI components to apply changes.
     */
    fun onOperation(operation: WOperation, forceNewEntry: Boolean = false) {
        val currentSnapshot = editorState.snapshot.value
        val newSnapshot = historyManager.record(
            snapshot = currentSnapshot,
            operation = operation,
            forceNewEntry = forceNewEntry || !isBatchEditing
        )
        editorState.updateSnapshot(newSnapshot)
    }

    fun applyMutation(result: WMutationResult) {
        if (result is WMutationResult.Success) {
            val before = editorState.snapshot.value
            val operation = WOperation.GeneralMutation(
                beforeSnapshot = before,
                afterSnapshot = result.snapshot,
                mutationType = result.mutationType
            )
            onOperation(operation)
        }
    }

    /**
     * Translates IME commit into a semantic operation.
     */
    fun onCommitText(text: String) {
        val result = WMutationEngine.insertText(editorState.snapshot.value, text)
        applyMutation(result)
    }

    /**
     * Translates IME backspace into a semantic operation.
     */
    fun onDeleteBackward() {
        val result = WMutationEngine.deleteBackward(editorState.snapshot.value)
        applyMutation(result)
    }

    /**
     * Translates IME delete into a semantic operation.
     */
    fun onDeleteForward() {
        val result = WMutationEngine.deleteForward(editorState.snapshot.value)
        applyMutation(result)
    }

    /**
     * Handles hardware keyboard shortcuts.
     */
    fun onKeyboardShortcut(shortcut: WShortcut) {
        val snapshot = editorState.snapshot.value
        when (shortcut) {
            WShortcut.Undo -> {
                val newSnapshot = historyManager.undo(snapshot)
                editorState.updateSnapshot(newSnapshot)
            }
            WShortcut.Redo -> {
                val newSnapshot = historyManager.redo(snapshot)
                editorState.updateSnapshot(newSnapshot)
            }
            WShortcut.Copy -> {
                clipboardManager.copy(snapshot)
            }
            WShortcut.Cut -> {
                clipboardManager.cut(snapshot)?.let { onOperation(it) }
            }
            WShortcut.Paste -> {
                clipboardManager.paste(snapshot)?.let { onOperation(it) }
            }
            WShortcut.PastePlainText -> {
                clipboardManager.paste(snapshot, asPlainText = true)?.let { onOperation(it) }
            }
            WShortcut.SelectAll -> {
                val currentBlockId = snapshot.cursor.position.blockId
                val newSelection = if (currentBlockId == "W_NOTE_TITLE") {
                    // If in title, select only the title
                    WSelectionManager.selectParagraph(snapshot.cursor.position, snapshot.document.blocks, snapshot.document.metadata.title)
                } else {
                    // Otherwise select everything in the note section
                    WSelectionManager.selectAll(snapshot.document.blocks, snapshot.document.metadata.title)
                }
                editorState.updateSnapshot(snapshot.copy(selection = newSelection))
            }
        }
    }

    /**
     * Translates IME enter into a semantic operation.
     */
    fun onEnter() {
        val result = WMutationEngine.splitBlock(editorState.snapshot.value)
        applyMutation(result)
    }
    fun beginBatchEdit() { isBatchEditing = true }
    fun endBatchEdit() { isBatchEditing = false }

    /**
     * Handles cursor navigation or selection extension.
     */
    fun onNavigation(
        direction: com.tv.wnotes.ui.editor.engine.logic.NavigationDirection, 
        layoutProvider: WLayoutProvider?,
        isShiftPressed: Boolean = false
    ) {
        val snapshot = editorState.snapshot.value
        val current = if (isShiftPressed && snapshot.selection.isActive) snapshot.selection.ranges.last().focus else snapshot.cursor.position
        
        val nextPoint = when (direction) {
            com.tv.wnotes.ui.editor.engine.logic.NavigationDirection.LEFT -> {
                if (current.offset > 0) current.copy(offset = current.offset - 1)
                else {
                    val index = snapshot.document.blocks.indexOfFirst { it.id == current.blockId }
                    if (index > 0) {
                        val prev = snapshot.document.blocks[index - 1]
                        val text = (prev as? com.tv.wnotes.ui.editor.engine.model.WTextContainer)?.text ?: ""
                        WSelectionPoint(prev.id, text.length)
                    } else current
                }
            }
            com.tv.wnotes.ui.editor.engine.logic.NavigationDirection.RIGHT -> {
                val block = snapshot.document.blocks.find { it.id == current.blockId }
                val textLength = (block as? com.tv.wnotes.ui.editor.engine.model.WTextContainer)?.text?.length ?: 0
                if (current.offset < textLength) current.copy(offset = current.offset + 1)
                else {
                    val index = snapshot.document.blocks.indexOfFirst { it.id == current.blockId }
                    if (index < snapshot.document.blocks.size - 1) {
                        val next = snapshot.document.blocks[index + 1]
                        WSelectionPoint(next.id, 0)
                    } else current
                }
            }
            com.tv.wnotes.ui.editor.engine.logic.NavigationDirection.UP,
            com.tv.wnotes.ui.editor.engine.logic.NavigationDirection.DOWN -> {
                (layoutProvider as? com.tv.wnotes.ui.editor.engine.renderer.WLayoutState)?.getNextPosition(current, direction) ?: current
            }
            else -> current
        }
        
        if (isShiftPressed) {
            editorState.extendSelection(nextPoint)
        } else {
            editorState.moveCursor(nextPoint, com.tv.wnotes.ui.editor.engine.logic.CursorAffinity.AFTER)
        }
    }
}

sealed interface WShortcut {
    data object Undo : WShortcut
    data object Redo : WShortcut
    data object SelectAll : WShortcut
    data object Copy : WShortcut
    data object Cut : WShortcut
    data object Paste : WShortcut
    data object PastePlainText : WShortcut
}

package com.tv.wnotes.ui.editor.engine.input

import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.*
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.WEditorState
import com.tv.wnotes.ui.editor.engine.state.WOperation
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.logic.WSpanManager
import com.tv.wnotes.ui.editor.engine.logic.WAnnotationManager

/**
 * Custom InputConnection for WEditor.
 * Translates low-level Android IME events into semantic engine operations.
 */
class WInputConnection(
    private val controller: WInputController,
    private val editorState: WEditorState
) : InputConnection {

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        controller.onCommitText(text.toString())
        return true
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        // Simple implementation for composing text
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (beforeLength == 0 && afterLength == 0) return true
        
        val snapshot = editorState.snapshot.value
        val cursor = snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == cursor.blockId }
        if (block !is WTextContainer) return false

        val start = (cursor.offset - beforeLength).coerceAtLeast(0)
        val end = (cursor.offset + afterLength).coerceIn(0, block.text.length)

        val operation = WOperation.TextChange(
            blockId = cursor.blockId,
            oldText = block.text,
            newText = StringBuilder(block.text).delete(start, end).toString(),
            oldSpans = block.spans,
            newSpans = WSpanManager.shiftSpansForDeletion(block.spans, start, end),
            beforeCursor = cursor,
            afterCursor = cursor.copy(offset = start),
            beforeAnnotations = snapshot.annotations,
            afterAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(snapshot.annotations, cursor.blockId, start, end)
        )
        
        controller.onOperation(operation)
        return true
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        val snapshot = editorState.snapshot.value
        val cursor = snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == cursor.blockId }
        if (block !is WTextContainer) return ""
        val start = (cursor.offset - n).coerceAtLeast(0)
        return block.text.substring(start, cursor.offset)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        val snapshot = editorState.snapshot.value
        val cursor = snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == cursor.blockId }
        if (block !is WTextContainer) return ""
        val end = (cursor.offset + n).coerceIn(0, block.text.length)
        return block.text.substring(cursor.offset, end)
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        val snapshot = editorState.snapshot.value
        if (snapshot.selection.ranges.isEmpty()) return null
        
        // Only handling single block selection for IME compatibility for now
        val range = snapshot.selection.ranges.first()
        if (range.anchor.blockId != range.focus.blockId) return null
        
        val block = snapshot.document.blocks.find { it.id == range.anchor.blockId }
        if (block !is WTextContainer) return null
        val start = minOf(range.anchor.offset, range.focus.offset)
        val end = maxOf(range.anchor.offset, range.focus.offset)
        return block.text.substring(start, end)
    }

    override fun getCursorCapsMode(reqModes: Int): Int {
        val snapshot = editorState.snapshot.value
        val cursor = snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == cursor.blockId }
        if (block !is WTextContainer) return 0
        return TextUtils.getCapsMode(block.text, cursor.offset, reqModes)
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        // Implementation for setting selection within current block
        return true
    }

    override fun beginBatchEdit(): Boolean {
        controller.beginBatchEdit()
        return true
    }

    override fun endBatchEdit(): Boolean {
        controller.endBatchEdit()
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    controller.onDeleteBackward()
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    // controller.onEnter()
                    return true
                }
            }
        }
        return false
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        // Handle composing region
        return true
    }

    // Required by Interface
    override fun finishComposingText(): Boolean = true
    override fun commitCompletion(text: CompletionInfo?): Boolean = false
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = false
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
    override fun performEditorAction(editorAction: Int): Boolean = true
    override fun performContextMenuAction(id: Int): Boolean = true
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun closeConnection() {}
    override fun clearMetaKeyStates(states: Int): Boolean = false
    override fun reportFullscreenMode(enabled: Boolean): Boolean = false
    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = false
    override fun getHandler(): android.os.Handler? = null
}

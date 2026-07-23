package com.tv.wnotes.ui.editor.engine.input

import android.view.KeyEvent
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.WEditorState
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint

/**
 * Logic for handling hardware keyboard events.
 * Translates raw KeyEvents into high-level input actions.
 */
class WKeyboardInterceptor(
    private val controller: WInputController,
    private val editorState: WEditorState
) {
    /**
     * @return true if the event was consumed.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed

        return when (event.keyCode) {
            KeyEvent.KEYCODE_Z -> {
                if (isCtrl) {
                    if (isShift) controller.onKeyboardShortcut(WShortcut.Redo)
                    else controller.onKeyboardShortcut(WShortcut.Undo)
                    true
                } else false
            }
            KeyEvent.KEYCODE_Y -> {
                if (isCtrl) {
                    controller.onKeyboardShortcut(WShortcut.Redo)
                    true
                } else false
            }
            KeyEvent.KEYCODE_A -> {
                if (isCtrl) {
                    controller.onKeyboardShortcut(WShortcut.SelectAll)
                    true
                } else false
            }
            KeyEvent.KEYCODE_C -> {
                if (isCtrl) {
                    controller.onKeyboardShortcut(WShortcut.Copy)
                    true
                } else false
            }
            KeyEvent.KEYCODE_X -> {
                if (isCtrl) {
                    controller.onKeyboardShortcut(WShortcut.Cut)
                    true
                } else false
            }
            KeyEvent.KEYCODE_V -> {
                if (isCtrl) {
                    if (isShift) controller.onKeyboardShortcut(WShortcut.PastePlainText)
                    else controller.onKeyboardShortcut(WShortcut.Paste)
                    true
                } else false
            }
            // Navigation
            KeyEvent.KEYCODE_DPAD_LEFT, 
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                handleNavigation(event.keyCode, isShift, isCtrl)
                true
            }
            else -> false
        }
    }

    private fun handleNavigation(keyCode: Int, isShift: Boolean, isCtrl: Boolean) {
        val snapshot = editorState.snapshot.value
        val currentPoint = snapshot.cursor.position
        val blocks = snapshot.document.blocks
        val blockIndex = blocks.indexOfFirst { it.id == currentPoint.blockId }
        if (blockIndex == -1) return
        
        val block = blocks[blockIndex] as? WTextBlock ?: return
        
        var nextPoint = currentPoint

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (currentPoint.offset > 0) {
                    nextPoint = currentPoint.copy(offset = currentPoint.offset - 1)
                } else if (blockIndex > 0) {
                    val prevBlock = blocks[blockIndex - 1] as? WTextBlock
                    if (prevBlock != null) {
                        nextPoint = WSelectionPoint(prevBlock.id, prevBlock.text.length)
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (currentPoint.offset < block.text.length) {
                    nextPoint = currentPoint.copy(offset = currentPoint.offset + 1)
                } else if (blockIndex < blocks.size - 1) {
                    val nextBlock = blocks[blockIndex + 1] as? WTextBlock
                    if (nextBlock != null) {
                        nextPoint = WSelectionPoint(nextBlock.id, 0)
                    }
                }
            }
        }

        if (isShift) {
            // Logic to extend selection
        } else {
            editorState.updateSnapshot(snapshot.copy(
                cursor = snapshot.cursor.copy(position = nextPoint),
                selection = WSelectionManager.collapseTo(nextPoint)
            ))
        }
    }
}

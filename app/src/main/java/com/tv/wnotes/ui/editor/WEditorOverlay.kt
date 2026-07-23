package com.tv.wnotes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tv.wnotes.ui.editor.engine.renderer.WSelectionHandleRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WDragPreviewRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WInsertionCaretRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WDragAnimationController
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WDragPhase

/**
 * Separate layer responsible for Cursor, Selection, Handles, Drag Previews, etc.
 * Initially renders nothing.
 */
@Composable
fun WEditorOverlay(
    snapshot: WEditorSnapshot,
    animController: WDragAnimationController,
    onCaretRendered: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDragging = snapshot.drag.phase != WDragPhase.IDLE

    Box(modifier = modifier.fillMaxSize()) {
        // Handles (Hidden while dragging)
        if (!isDragging) {
            WSelectionHandleRenderer(
                selectionState = snapshot.selection,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Insertion Caret (Only while dragging)
        if (isDragging) {
            WInsertionCaretRenderer(
                caretState = snapshot.drag.insertionCaret,
                onRendered = onCaretRendered,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Drag Preview
        WDragPreviewRenderer(
            previewState = snapshot.drag.preview,
            snapshot = snapshot,
            animController = animController,
            modifier = Modifier.fillMaxSize()
        )
    }
}

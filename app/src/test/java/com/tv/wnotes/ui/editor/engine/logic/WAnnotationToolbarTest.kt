package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Test

class WAnnotationToolbarTest {

    private val blockId = "b1"
    private val doc = WDocument("1", persistentListOf(WTextBlock(blockId, text = "Hello world")))
    private val initialSnapshot = WEditorSnapshot(
        document = doc,
        selection = WSelectionState(),
        cursor = WCursorState(WSelectionPoint(blockId, 0)),
        drag = WDragState(),
        viewport = WViewportState(),
        clipboard = WClipboardState(),
        settings = WEditorSettings(),
        history = WHistory()
    )

    @Test
    fun testStartCreatingAnnotationCapturesSelection() {
        val state = WEditorState(initialSnapshot)
        val range = WSelectionRange(WSelectionPoint(blockId, 0), WSelectionPoint(blockId, 5))
        
        state.startCreatingAnnotation(range, "Hello")
        
        val snapshot = state.snapshot.value
        assertTrue(snapshot.isCreatingAnnotation)
        assertEquals(range, snapshot.pendingAnnotationRange)
        assertEquals("Hello", snapshot.pendingAnnotationAnchorText)
    }

    @Test
    fun testCancelCreatingAnnotationClearsPending() {
        val state = WEditorState(initialSnapshot)
        val range = WSelectionRange(WSelectionPoint(blockId, 0), WSelectionPoint(blockId, 5))
        
        state.startCreatingAnnotation(range, "Hello")
        state.cancelCreatingAnnotation()
        
        val snapshot = state.snapshot.value
        assertFalse(snapshot.isCreatingAnnotation)
        assertNull(snapshot.pendingAnnotationRange)
        assertEquals("", snapshot.pendingAnnotationAnchorText)
    }

    @Test
    fun testSaveAnnotationUsesPendingRange() {
        val range = WSelectionRange(WSelectionPoint(blockId, 0), WSelectionPoint(blockId, 5))
        val snapshot = initialSnapshot.copy(
            isCreatingAnnotation = true,
            pendingAnnotationRange = range,
            pendingAnnotationAnchorText = "Hello"
        )
        
        // Simulate Save from UI
        val result = WMutationEngine.annotateRange(snapshot, "Greeting", range, "Hello")
        
        assertTrue(result is WMutationResult.Success)
        val nextSnapshot = (result as WMutationResult.Success).snapshot
        assertEquals(1, nextSnapshot.annotations.size)
        assertEquals("Greeting", nextSnapshot.annotations[0].content)
        assertEquals(range, WSelectionRange(nextSnapshot.annotations[0].startOffset.let { WSelectionPoint(blockId, it) }, nextSnapshot.annotations[0].endOffset.let { WSelectionPoint(blockId, it) }))
    }
}

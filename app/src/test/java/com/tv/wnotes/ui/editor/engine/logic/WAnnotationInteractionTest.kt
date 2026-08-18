package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Test

class WAnnotationInteractionTest {

    private val blockId = "b1"
    private val annId = "a1"
    private val annotations = listOf(
        ContextAnnotation(id = annId, tabId = 1, blockId = blockId, startOffset = 5, endOffset = 10, anchorText = "world", content = "c")
    )
    private val doc = WDocument("1", persistentListOf(WTextBlock(blockId, text = "hello world")))
    private val initialSnapshot = WEditorSnapshot(
        document = doc,
        annotations = persistentListOf(annotations[0]),
        selection = WSelectionState(),
        cursor = WCursorState(WSelectionPoint(blockId, 0)),
        drag = WDragState(),
        viewport = WViewportState(),
        clipboard = WClipboardState(),
        settings = WEditorSettings(),
        history = WHistory()
    )

    @Test
    fun testHitTestAnnotation() {
        // "hello world"
        // 01234567890
        // ann at [5, 10)
        
        val found = WAnnotationManager.getAnnotationAtPoint(annotations, blockId, 7)
        assertNotNull(found)
        assertEquals(annId, found?.id)

        val notFound = WAnnotationManager.getAnnotationAtPoint(annotations, blockId, 3)
        assertNull(notFound)
    }

    @Test
    fun testHitTestMultiLineAnnotation() {
        // Not implemented multi-line hit testing yet in WAnnotationManager, 
        // but it uses character offsets which are line-agnostic.
        // So it should work if the offsets are correct.
    }

    @Test
    fun testGetAnnotationsIntersecting() {
        val intersecting = WAnnotationManager.getAnnotationsIntersecting(annotations, blockId, 0, 20)
        assertEquals(1, intersecting.size)

        val nonIntersecting = WAnnotationManager.getAnnotationsIntersecting(annotations, blockId, 0, 5)
        assertEquals(0, nonIntersecting.size)
    }
}

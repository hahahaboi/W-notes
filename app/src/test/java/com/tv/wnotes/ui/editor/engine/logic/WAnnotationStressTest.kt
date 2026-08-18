package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WAnnotationStressTest {

    @Test
    fun testStressAnnotationShifting() {
        val numAnnotations = 500
        val blockId = "b1"
        val text = "A".repeat(2000)
        val blocks = persistentListOf(WTextBlock(blockId, text = text))
        val document = WDocument("1", blocks)
        
        val annotations = (0 until numAnnotations).map { i ->
            ContextAnnotation(
                id = UUID.randomUUID().toString(),
                tabId = 1,
                blockId = blockId,
                startOffset = i * 2,
                endOffset = i * 2 + 1,
                anchorText = "A",
                content = "Context $i"
            )
        }.toImmutableList()

        val snapshot = WEditorSnapshot(
            document = document,
            annotations = annotations,
            selection = WSelectionState(),
            cursor = WCursorState(WSelectionPoint(blockId, 0)),
            drag = WDragState(),
            viewport = WViewportState(),
            clipboard = WClipboardState(),
            settings = WEditorSettings(),
            history = WHistory()
        )

        val startTime = System.currentTimeMillis()
        
        // Simulate insertion at the very beginning (shifts ALL 500 annotations)
        val result = WMutationEngine.insertText(snapshot, "NEW ")
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        println("Shifting $numAnnotations annotations took ${duration}ms")
        
        assertTrue(result is WMutationResult.Success)
        val nextSnapshot = (result as WMutationResult.Success).snapshot
        assertEquals(numAnnotations, nextSnapshot.annotations.size)
        assertEquals(4, nextSnapshot.annotations[0].startOffset)
        
        // Requirement: Should be fast (e.g. < 50ms on a modern machine, though CI might be slower)
        assertTrue("Performance too slow: ${duration}ms", duration < 100)
    }

    @Test
    fun testStressHitTesting() {
        val numAnnotations = 500
        val blockId = "b1"
        val annotations = (0 until numAnnotations).map { i ->
            ContextAnnotation(
                id = "ann-$i",
                tabId = 1,
                blockId = blockId,
                startOffset = i * 2,
                endOffset = i * 2 + 1,
                anchorText = "A",
                content = "c"
            )
        }

        val startTime = System.nanoTime()
        val found = WAnnotationManager.getAnnotationAtPoint(annotations, blockId, 998)
        val endTime = System.nanoTime()
        val durationUs = (endTime - startTime) / 1000
        
        println("Hit testing among $numAnnotations annotations took ${durationUs}us")
        
        assertNotNull(found)
        assertEquals("ann-499", found?.id)
        
        // Scanning 500 items is extremely fast in memory
        assertTrue(durationUs < 1000) // < 1ms
    }
}

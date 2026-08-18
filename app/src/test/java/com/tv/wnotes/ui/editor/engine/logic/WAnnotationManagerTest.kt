package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import org.junit.Assert.*
import org.junit.Test

class WAnnotationManagerTest {

    private val blockId = "block-1"
    private val tabId = 1L

    private fun createAnnotation(start: Int, end: Int, text: String = "anchor"): ContextAnnotation {
        return ContextAnnotation(
            id = "ann-1",
            tabId = tabId,
            blockId = blockId,
            startOffset = start,
            endOffset = end,
            anchorText = text,
            content = "context"
        )
    }

    @Test
    fun testShiftInsertionBefore() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForInsertion(listOf(ann), blockId, 5, 5)
        assertEquals(15, shifted[0].startOffset)
        assertEquals(25, shifted[0].endOffset)
    }

    @Test
    fun testShiftInsertionAfter() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForInsertion(listOf(ann), blockId, 25, 5)
        assertEquals(10, shifted[0].startOffset)
        assertEquals(20, shifted[0].endOffset)
    }

    @Test
    fun testShiftInsertionInside() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForInsertion(listOf(ann), blockId, 15, 5)
        assertEquals(10, shifted[0].startOffset)
        assertEquals(25, shifted[0].endOffset)
    }

    @Test
    fun testShiftInsertionAtStart() {
        // Boundary Policy: insertion at startOffset -> outside (moves forward)
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForInsertion(listOf(ann), blockId, 10, 5)
        assertEquals(15, shifted[0].startOffset)
        assertEquals(25, shifted[0].endOffset)
    }

    @Test
    fun testShiftInsertionAtEnd() {
        // Boundary Policy: insertion at endOffset -> outside (stays put)
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForInsertion(listOf(ann), blockId, 20, 5)
        assertEquals(10, shifted[0].startOffset)
        assertEquals(20, shifted[0].endOffset)
    }

    @Test
    fun testShiftDeletionBefore() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForDeletion(listOf(ann), blockId, 0, 5)
        assertEquals(5, shifted[0].startOffset)
        assertEquals(15, shifted[0].endOffset)
    }

    @Test
    fun testShiftDeletionAfter() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForDeletion(listOf(ann), blockId, 25, 5)
        assertEquals(10, shifted[0].startOffset)
        assertEquals(20, shifted[0].endOffset)
    }

    @Test
    fun testShiftDeletionInside() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForDeletion(listOf(ann), blockId, 12, 17)
        assertEquals(10, shifted[0].startOffset)
        assertEquals(15, shifted[0].endOffset)
    }

    @Test
    fun testShiftDeletionOverlapStart() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForDeletion(listOf(ann), blockId, 5, 15)
        assertEquals(5, shifted[0].startOffset)
        assertEquals(10, shifted[0].endOffset)
    }

    @Test
    fun testShiftDeletionOverlapEnd() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForDeletion(listOf(ann), blockId, 15, 25)
        assertEquals(10, shifted[0].startOffset)
        assertEquals(15, shifted[0].endOffset)
    }

    @Test
    fun testShiftDeletionCoversAll() {
        val ann = createAnnotation(10, 20)
        val shifted = WAnnotationManager.shiftAnnotationsForDeletion(listOf(ann), blockId, 5, 25)
        assertTrue(shifted.isEmpty())
    }

    @Test
    fun testSplitBefore() {
        val ann = createAnnotation(10, 20)
        val split = WAnnotationManager.splitAnnotations(listOf(ann), blockId, "new-id", 5)
        assertEquals("new-id", split[0].blockId)
        assertEquals(5, split[0].startOffset)
        assertEquals(15, split[0].endOffset)
    }

    @Test
    fun testSplitAfter() {
        val ann = createAnnotation(10, 20)
        val split = WAnnotationManager.splitAnnotations(listOf(ann), blockId, "new-id", 25)
        assertEquals(blockId, split[0].blockId)
        assertEquals(10, split[0].startOffset)
        assertEquals(20, split[0].endOffset)
    }

    @Test
    fun testSplitInside() {
        // Larger part should be preserved.
        // Head: 10 to 15 (5 chars), Tail: 15 to 20 (5 chars). Tie-break: keep in head.
        val ann = createAnnotation(10, 20)
        val split = WAnnotationManager.splitAnnotations(listOf(ann), blockId, "new-id", 15)
        assertEquals(blockId, split[0].blockId)
        assertEquals(10, split[0].startOffset)
        assertEquals(15, split[0].endOffset)
        
        // Tail larger: 10 to 12 (2 chars), 12 to 20 (8 chars)
        val split2 = WAnnotationManager.splitAnnotations(listOf(ann), blockId, "new-id", 12)
        assertEquals("new-id", split2[0].blockId)
        assertEquals(0, split2[0].startOffset)
        assertEquals(8, split2[0].endOffset)
    }

    @Test
    fun testMerge() {
        val ann = createAnnotation(10, 20)
        val merged = WAnnotationManager.mergeAnnotations(listOf(ann), "merged-id", blockId, 50)
        assertEquals("merged-id", merged[0].blockId)
        assertEquals(60, merged[0].startOffset)
        assertEquals(70, merged[0].endOffset)
    }

    @Test
    fun testRecoveryValid() {
        val ann = createAnnotation(0, 5, "hello")
        val blocks = listOf(WTextBlock(id = blockId, text = "hello world"))
        val recovered = WAnnotationManager.verifyAndRelocate(listOf(ann), blocks)
        assertEquals(0, recovered[0].startOffset)
        assertEquals(5, recovered[0].endOffset)
    }

    @Test
    fun testRecoveryDrift() {
        val ann = createAnnotation(0, 5, "world") // Wrong offset
        val blocks = listOf(WTextBlock(id = blockId, text = "hello world"))
        val recovered = WAnnotationManager.verifyAndRelocate(listOf(ann), blocks)
        assertEquals(6, recovered[0].startOffset)
        assertEquals(11, recovered[0].endOffset)
    }

    @Test
    fun testRecoveryAmbiguous() {
        val ann = createAnnotation(0, 3, "abc")
        val blocks = listOf(WTextBlock(id = blockId, text = "abc ... abc ... abc"))
        // Original was at 0. Closest to 0 should be chosen.
        val recovered = WAnnotationManager.verifyAndRelocate(listOf(ann), blocks)
        assertEquals(0, recovered[0].startOffset)
        
        val ann2 = createAnnotation(15, 18, "abc")
        val recovered2 = WAnnotationManager.verifyAndRelocate(listOf(ann2), blocks)
        assertEquals(16, recovered2[0].startOffset) // Closest match to 15 is at 16
    }
}

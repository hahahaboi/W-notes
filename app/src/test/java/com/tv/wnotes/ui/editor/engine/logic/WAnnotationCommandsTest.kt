package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Test

class WAnnotationCommandsTest {

    private val blockId = "b1"
    private val doc = WDocument("1", persistentListOf(WTextBlock(blockId, text = "The chlorophyll absorbs light.")))
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
    fun testCreateAnnotation() {
        val range = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 15)) // "chlorophyll"
        val result = WMutationEngine.annotateRange(initialSnapshot, "Green pigment", range, "chlorophyll")
        
        assertTrue(result is WMutationResult.Success)
        val snapshot = (result as WMutationResult.Success).snapshot
        assertEquals(1, snapshot.annotations.size)
        assertEquals("Green pigment", snapshot.annotations[0].content)
        assertEquals(4, snapshot.annotations[0].startOffset)
        assertEquals(15, snapshot.annotations[0].endOffset)
    }

    @Test
    fun testRejectEmptyAnnotation() {
        val range = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 15))
        val result = WMutationEngine.annotateRange(initialSnapshot, "", range, "chlorophyll")
        assertTrue(result is WMutationResult.Failure)
    }

    @Test
    fun testRejectEmptyRange() {
        val range = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 4))
        val result = WMutationEngine.annotateRange(initialSnapshot, "pigment", range, "")
        assertTrue(result is WMutationResult.Failure)
    }

    @Test
    fun testEditAnnotation() {
        val range = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 15))
        val res1 = WMutationEngine.annotateRange(initialSnapshot, "Green", range, "chlorophyll")
        val s1 = (res1 as WMutationResult.Success).snapshot
        val annId = s1.annotations[0].id

        val res2 = WMutationEngine.updateAnnotation(s1, annId, "Very Green")
        assertTrue(res2 is WMutationResult.Success)
        val s2 = (res2 as WMutationResult.Success).snapshot
        assertEquals(1, s2.annotations.size)
        assertEquals("Very Green", s2.annotations[0].content)
        assertEquals(annId, s2.annotations[0].id)
    }

    @Test
    fun testDeleteAnnotation() {
        val range = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 15))
        val res1 = WMutationEngine.annotateRange(initialSnapshot, "Green", range, "chlorophyll")
        val s1 = (res1 as WMutationResult.Success).snapshot
        val annId = s1.annotations[0].id

        val res2 = WMutationEngine.deleteAnnotation(s1, annId)
        assertTrue(res2 is WMutationResult.Success)
        val s2 = (res2 as WMutationResult.Success).snapshot
        assertTrue(s2.annotations.isEmpty())
    }

    @Test
    fun testAnnotationSurvivesTextReplacement() {
        // "The chlorophyll absorbs light."
        // Annotated: "chlorophyll" [4, 15)
        val range = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 15))
        val res1 = WMutationEngine.annotateRange(initialSnapshot, "pigment", range, "chlorophyll")
        val s1 = (res1 as WMutationResult.Success).snapshot

        // Replace "chlorophyll" with "the pigment"
        val selection = WSelectionState(ranges = listOf(range), isActive = true)
        val s1WithSelection = s1.copy(selection = selection)
        
        val res2 = WMutationEngine.insertText(s1WithSelection, "the pigment")
        assertTrue(res2 is WMutationResult.Success)
        val s2 = (res2 as WMutationResult.Success).snapshot
        
        // Result: "The the pigment absorbs light."
        // Annotation should now cover "the pigment"
        assertEquals(1, s2.annotations.size)
        assertEquals(4, s2.annotations[0].startOffset)
        assertEquals(15, s2.annotations[0].endOffset) // "the pigment" is 11 chars. 4 + 11 = 15.
        // Wait, "chlorophyll" was 11 chars too. So 15 is correct.
    }

    @Test
    fun testOverlappingAnnotations() {
        val r1 = WSelectionRange(WSelectionPoint(blockId, 4), WSelectionPoint(blockId, 15)) // "chlorophyll"
        val r2 = WSelectionRange(WSelectionPoint(blockId, 11), WSelectionPoint(blockId, 23)) // "phyll absorbs"
        
        val res1 = WMutationEngine.annotateRange(initialSnapshot, "A", r1, "chlorophyll")
        val s1 = (res1 as WMutationResult.Success).snapshot
        
        val res2 = WMutationEngine.annotateRange(s1, "B", r2, "phyll absorbs")
        val s2 = (res2 as WMutationResult.Success).snapshot
        
        assertEquals(2, s2.annotations.size)
        assertTrue(s2.annotations.any { it.content == "A" })
        assertTrue(s2.annotations.any { it.content == "B" })
    }
}

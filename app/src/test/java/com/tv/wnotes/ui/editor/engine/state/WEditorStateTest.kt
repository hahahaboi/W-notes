package com.tv.wnotes.ui.editor.engine.state

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.logic.WHistoryManager
import com.tv.wnotes.ui.editor.engine.logic.WMutationEngine
import com.tv.wnotes.ui.editor.engine.logic.WMutationResult
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Test

class WEditorStateTest {

    @Test
    fun testSnapshotImmutability() {
        val doc = WDocument("id", persistentListOf(WTextBlock("b1", text = "hello")))
        val ann = ContextAnnotation(id = "a1", tabId = 1, blockId = "b1", startOffset = 0, endOffset = 5, anchorText = "hello", content = "c")
        
        val state = WEditorState(doc, listOf(ann))
        val snapshotA = state.snapshot.value
        
        val range = WSelectionRange(WSelectionPoint("b1", 0), WSelectionPoint("b1", 5), "r1")
        val result = WMutationEngine.annotateRange(snapshotA, "c2", range, "hello")
        assertTrue(result is WMutationResult.Success)
        val snapshotB = (result as WMutationResult.Success).snapshot
        
        assertNotEquals(snapshotA, snapshotB)
        assertEquals(1, snapshotA.annotations.size)
        assertEquals(2, snapshotB.annotations.size)
    }

    @Test
    fun testAnnotationCRUD() {
        val doc = WDocument("id", persistentListOf(WTextBlock("b1", text = "hello")))
        val state = WEditorState(doc)
        var snapshot = state.snapshot.value
        
        val range = WSelectionRange(WSelectionPoint("b1", 0), WSelectionPoint("b1", 5), "r1")
        
        // Create
        val res1 = WMutationEngine.annotateRange(snapshot, "content", range, "hello")
        snapshot = (res1 as WMutationResult.Success).snapshot
        assertEquals(1, snapshot.annotations.size)
        assertEquals("content", snapshot.annotations[0].content)
        val annId = snapshot.annotations[0].id
        
        // Update
        val res2 = WMutationEngine.updateAnnotation(snapshot, annId, "new content")
        snapshot = (res2 as WMutationResult.Success).snapshot
        assertEquals("new content", snapshot.annotations[0].content)
        
        // Delete
        val res3 = WMutationEngine.deleteAnnotation(snapshot, annId)
        snapshot = (res3 as WMutationResult.Success).snapshot
        assertTrue(snapshot.annotations.isEmpty())
    }

    @Test
    fun testUndoRedoWithAnnotations() {
        val doc = WDocument("id", persistentListOf(WTextBlock("b1", text = "hello")))
        val state = WEditorState(doc)
        val history = WHistoryManager()
        
        val range = WSelectionRange(WSelectionPoint("b1", 0), WSelectionPoint("b1", 5), "r1")
        
        // 1. Add Annotation
        val snapshotBefore = state.snapshot.value
        val result = WMutationEngine.annotateRange(snapshotBefore, "c", range, "hello")
        val snapshotAfter = (result as WMutationResult.Success).snapshot
        val op = WOperation.GeneralMutation(snapshotBefore, snapshotAfter, com.tv.wnotes.ui.editor.engine.logic.WMutationType.ANNOTATE_RANGE)
        
        val snapshot1 = history.record(snapshotBefore, op)
        state.updateSnapshot(snapshot1)
        assertEquals(1, state.snapshot.value.annotations.size)

        // 2. Undo
        val snapshot2 = history.undo(state.snapshot.value)
        state.updateSnapshot(snapshot2)
        assertTrue(state.snapshot.value.annotations.isEmpty())

        // 3. Redo
        val snapshot3 = history.redo(state.snapshot.value)
        state.updateSnapshot(snapshot3)
        assertEquals(1, state.snapshot.value.annotations.size)
    }

    @Test
    fun testMutationShiftsAnnotationsInSnapshot() {
        val doc = WDocument("id", persistentListOf(WTextBlock("b1", text = "hello")))
        val ann = ContextAnnotation(id = "a1", tabId = 1, blockId = "b1", startOffset = 0, endOffset = 5, anchorText = "hello", content = "c")
        val state = WEditorState(doc, listOf(ann))
        
        // Insert text at 0
        val result = WMutationEngine.insertText(state.snapshot.value, "new ")
        assertTrue(result is com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Success)
        val nextSnapshot = (result as com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Success).snapshot
        
        assertEquals(4, nextSnapshot.annotations[0].startOffset)
        assertEquals(9, nextSnapshot.annotations[0].endOffset)
    }
}

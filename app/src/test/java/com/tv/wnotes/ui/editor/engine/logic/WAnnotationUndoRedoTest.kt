package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Test

class WAnnotationUndoRedoTest {

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
    fun testUndoRedoAnnotationCreation() {
        val historyManager = WHistoryManager()
        val range = WSelectionRange(WSelectionPoint(blockId, 0), WSelectionPoint(blockId, 5))
        
        // 1. Create Annotation
        val res = WMutationEngine.annotateRange(initialSnapshot, "content", range, "Hello")
        val s1 = (res as WMutationResult.Success).snapshot
        val op = WOperation.GeneralMutation(initialSnapshot, s1, WMutationType.ANNOTATE_RANGE)
        
        val snapshotWithHistory = historyManager.record(initialSnapshot, op)
        assertEquals(1, snapshotWithHistory.annotations.size)

        // 2. Undo
        val undone = historyManager.undo(snapshotWithHistory)
        assertTrue(undone.annotations.isEmpty())

        // 3. Redo
        val redone = historyManager.redo(undone)
        assertEquals(1, redone.annotations.size)
        assertEquals("content", redone.annotations[0].content)
    }

    @Test
    fun testUndoRedoAnnotationDeletion() {
        val historyManager = WHistoryManager()
        val range = WSelectionRange(WSelectionPoint(blockId, 0), WSelectionPoint(blockId, 5))
        val res1 = WMutationEngine.annotateRange(initialSnapshot, "content", range, "Hello")
        val s1 = (res1 as WMutationResult.Success).snapshot
        val annId = s1.annotations[0].id

        // 1. Delete Annotation
        val res2 = WMutationEngine.deleteAnnotation(s1, annId)
        val s2 = (res2 as WMutationResult.Success).snapshot
        val op = WOperation.GeneralMutation(s1, s2, WMutationType.DELETE_ANNOTATION)
        
        val s2WithHistory = historyManager.record(s1, op)
        assertTrue(s2WithHistory.annotations.isEmpty())

        // 2. Undo
        val undone = historyManager.undo(s2WithHistory)
        assertEquals(1, undone.annotations.size)
        assertEquals(annId, undone.annotations[0].id)

        // 3. Redo
        val redone = historyManager.redo(undone)
        assertTrue(redone.annotations.isEmpty())
    }

    @Test
    fun testMixedTextAndAnnotationHistory() {
        val historyManager = WHistoryManager()
        var current = initialSnapshot

        // 1. Type "A "
        val res1 = WMutationEngine.insertText(current, "A ")
        val s1 = (res1 as WMutationResult.Success).snapshot
        current = historyManager.record(current, WOperation.GeneralMutation(current, s1, WMutationType.INSERT_TEXT))

        // 2. Annotate "A"
        val range = WSelectionRange(WSelectionPoint(blockId, 0), WSelectionPoint(blockId, 1))
        val res2 = WMutationEngine.annotateRange(current, "note", range, "A")
        val s2 = (res2 as WMutationResult.Success).snapshot
        current = historyManager.record(current, WOperation.GeneralMutation(current, s2, WMutationType.ANNOTATE_RANGE))

        // 3. Type "B"
        val res3 = WMutationEngine.insertText(current, "B")
        val s3 = (res3 as WMutationResult.Success).snapshot
        current = historyManager.record(current, WOperation.GeneralMutation(current, s3, WMutationType.INSERT_TEXT))

        assertEquals("A BHello world", current.document.blocks[0].let { (it as com.tv.wnotes.ui.editor.engine.model.WTextBlock).text })
        assertEquals(1, current.annotations.size)

        // Undo B
        current = historyManager.undo(current)
        assertEquals("A Hello world", current.document.blocks[0].let { (it as com.tv.wnotes.ui.editor.engine.model.WTextBlock).text })
        assertEquals(1, current.annotations.size)

        // Undo Annotation
        current = historyManager.undo(current)
        assertTrue(current.annotations.isEmpty())

        // Undo "A "
        current = historyManager.undo(current)
        assertEquals("Hello world", current.document.blocks[0].let { (it as com.tv.wnotes.ui.editor.engine.model.WTextBlock).text })
    }
}

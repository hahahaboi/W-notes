package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WHistory
import com.tv.wnotes.ui.editor.engine.state.WOperation

/**
 * Manages the undo/redo history for the editor.
 * Ensures semantic operations are recorded, coalesced, and reversed correctly.
 */
class WHistoryManager(
    private val maxHistorySize: Int = 100,
    private val coalesceTimeoutMs: Long = 1000L
) {
    private var lastOperationTime = 0L

    /**
     * Executes a new operation, adding it to the undo stack and clearing redo.
     * 
     * @param snapshot The current state before the operation.
     * @param operation The operation to record and apply.
     * @param forceNewEntry If true, prevents coalescing with the previous operation.
     */
    fun record(
        snapshot: WEditorSnapshot,
        operation: WOperation,
        forceNewEntry: Boolean = false
    ): WEditorSnapshot {
        val now = System.currentTimeMillis()
        val history = snapshot.history
        
        val canCoalesce = !forceNewEntry && 
                          (now - lastOperationTime < coalesceTimeoutMs)
        
        val lastOp = history.undoStack.lastOrNull()
        
        val newUndoStack = if (canCoalesce && lastOp != null) {
            val merged = operation.mergeWith(lastOp)
            if (merged != null) {
                history.undoStack.dropLast(1) + merged
            } else {
                history.undoStack + operation
            }
        } else {
            history.undoStack + operation
        }

        lastOperationTime = now

        // Limit stack size
        val finalUndoStack = if (newUndoStack.size > maxHistorySize) {
            newUndoStack.drop(newUndoStack.size - maxHistorySize)
        } else {
            newUndoStack
        }

        // Apply the operation to get the new document state
        val nextSnapshot = operation.redo(snapshot)

        // Return new snapshot with updated history state
        return nextSnapshot.copy(
            history = history.copy(
                undoStack = finalUndoStack,
                redoStack = emptyList() // New edits clear the redo stack
            )
        )
    }

    /**
     * Reverses the last operation on the undo stack.
     */
    fun undo(snapshot: WEditorSnapshot): WEditorSnapshot {
        val history = snapshot.history
        if (history.undoStack.isEmpty()) return snapshot

        val op = history.undoStack.last()
        
        // Use the operation to reverse the document state
        val undoneSnapshot = op.undo(snapshot)

        return undoneSnapshot.copy(
            history = history.copy(
                undoStack = history.undoStack.dropLast(1),
                redoStack = history.redoStack + op
            )
        )
    }

    /**
     * Re-applies the last operation on the redo stack.
     */
    fun redo(snapshot: WEditorSnapshot): WEditorSnapshot {
        val history = snapshot.history
        if (history.redoStack.isEmpty()) return snapshot

        val op = history.redoStack.last()
        
        // Use the operation to re-apply the document state
        val redoneSnapshot = op.redo(snapshot)

        return redoneSnapshot.copy(
            history = history.copy(
                undoStack = history.undoStack + op,
                redoStack = history.redoStack.dropLast(1)
            )
        )
    }

    fun canUndo(snapshot: WEditorSnapshot): Boolean = snapshot.history.undoStack.isNotEmpty()
    fun canRedo(snapshot: WEditorSnapshot): Boolean = snapshot.history.redoStack.isNotEmpty()
    
    /**
     * Clears all history.
     */
    fun clear(snapshot: WEditorSnapshot): WEditorSnapshot {
        return snapshot.copy(
            history = snapshot.history.copy(
                undoStack = emptyList(),
                redoStack = emptyList()
            )
        )
    }
}

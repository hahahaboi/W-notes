package com.tv.wnotes.ui.editor.engine.state

import androidx.compose.runtime.Immutable
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.logic.WContentManager
import kotlinx.collections.immutable.toImmutableList

/**
 * Represents a single undoable user action.
 */
@Immutable
sealed interface WOperation {
    fun redo(snapshot: WEditorSnapshot): WEditorSnapshot
    fun undo(snapshot: WEditorSnapshot): WEditorSnapshot
    fun mergeWith(previous: WOperation): WOperation? = null

    /**
     * Captures a generic text change within a block.
     */
    data class TextChange(
        val blockId: String,
        val oldText: String,
        val newText: String,
        val oldSpans: List<WTextSpan>,
        val newSpans: List<WTextSpan>,
        val beforeCursor: WSelectionPoint,
        val afterCursor: WSelectionPoint
    ) : WOperation {
        override fun redo(snapshot: WEditorSnapshot) = applyChange(snapshot, newText, newSpans, afterCursor)
        override fun undo(snapshot: WEditorSnapshot) = applyChange(snapshot, oldText, oldSpans, beforeCursor)

        private fun applyChange(
            snapshot: WEditorSnapshot,
            text: String,
            spans: List<WTextSpan>,
            cursor: WSelectionPoint
        ): WEditorSnapshot {
            val blocks = snapshot.document.blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index == -1) return snapshot

            val block = blocks[index] as? WTextBlock ?: return snapshot
            blocks[index] = block.copy(
                text = text,
                spans = spans,
                revision = block.revision + 1,
                updatedAt = System.currentTimeMillis()
            )

            return snapshot.copy(
                document = snapshot.document.copy(
                    blocks = blocks.toImmutableList(),
                    revision = snapshot.document.revision + 1
                ),
                cursor = snapshot.cursor.copy(position = cursor),
                selection = WSelectionManager.collapseTo(cursor)
            )
        }

        override fun mergeWith(previous: WOperation): WOperation? {
            if (previous !is TextChange || previous.blockId != this.blockId) return null
            
            // Simple check: if this insertion follows the previous one exactly
            val isContiguous = previous.afterCursor == this.beforeCursor
            if (!isContiguous) return null

            return TextChange(
                blockId = blockId,
                oldText = previous.oldText,
                newText = this.newText,
                oldSpans = previous.oldSpans,
                newSpans = this.newSpans,
                beforeCursor = previous.beforeCursor,
                afterCursor = this.afterCursor
            )
        }
    }

    /**
     * Captures a block split (Enter key).
     */
    data class SplitBlock(
        val originalBlockId: String,
        val splitOffset: Int,
        val newBlock: WTextBlock,
        val beforeCursor: WSelectionPoint,
        val afterCursor: WSelectionPoint
    ) : WOperation {
        override fun redo(snapshot: WEditorSnapshot) = applySplit(snapshot)
        override fun undo(snapshot: WEditorSnapshot) = applyMerge(snapshot)

        private fun applySplit(snapshot: WEditorSnapshot): WEditorSnapshot {
            val blocks = snapshot.document.blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == originalBlockId }
            if (index == -1) return snapshot
            val block = blocks[index] as WTextBlock
            val head = block.copy(text = block.text.substring(0, splitOffset), revision = block.revision + 1)
            blocks[index] = head
            blocks.add(index + 1, newBlock)
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList()),
                cursor = snapshot.cursor.copy(position = afterCursor)
            )
        }

        private fun applyMerge(snapshot: WEditorSnapshot): WEditorSnapshot {
            val blocks = snapshot.document.blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == originalBlockId }
            if (index == -1) return snapshot
            val block = blocks[index] as WTextBlock
            // Assuming newBlock was inserted right after
            blocks.removeAt(index + 1)
            val restored = block.copy(
                text = block.text + newBlock.text,
                spans = block.spans + newBlock.spans.map { it.copy(start = it.start + block.text.length, end = it.end + block.text.length) },
                revision = block.revision + 1
            )
            blocks[index] = restored
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList()),
                cursor = snapshot.cursor.copy(position = beforeCursor)
            )
        }
    }

    /**
     * Captures a movement of text from one location to another.
     */
    data class MoveSelectionOperation(
        val beforeSnapshot: WEditorSnapshot,
        val afterSnapshot: WEditorSnapshot
    ) : WOperation {
        override fun redo(snapshot: WEditorSnapshot) = afterSnapshot
        override fun undo(snapshot: WEditorSnapshot) = beforeSnapshot
    }

    /**
     * Captures a checklist state change.
     */
    data class ToggleChecklist(
        val blockId: String,
        val isChecked: Boolean
    ) : WOperation {
        override fun redo(snapshot: WEditorSnapshot) = applyToggle(snapshot, isChecked)
        override fun undo(snapshot: WEditorSnapshot) = applyToggle(snapshot, !isChecked)

        private fun applyToggle(snapshot: WEditorSnapshot, checked: Boolean): WEditorSnapshot {
            val blocks = snapshot.document.blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index == -1) return snapshot
            val block = blocks[index] as? WChecklistBlock ?: return snapshot
            blocks[index] = block.copy(isChecked = checked, revision = block.revision + 1)
            return snapshot.copy(document = snapshot.document.copy(blocks = blocks.toImmutableList()))
        }
    }

    /**
     * Captures a block list structure change.
     */
    data class MoveBlock(
        val fromIndex: Int,
        val toIndex: Int
    ) : WOperation {
        override fun redo(snapshot: WEditorSnapshot) = applyMove(snapshot, fromIndex, toIndex)
        override fun undo(snapshot: WEditorSnapshot) = applyMove(snapshot, toIndex, fromIndex)

        private fun applyMove(snapshot: WEditorSnapshot, from: Int, to: Int): WEditorSnapshot {
            val blocks = snapshot.document.blocks.toMutableList()
            val block = blocks.removeAt(from)
            blocks.add(to, block)
            return snapshot.copy(document = snapshot.document.copy(blocks = blocks.toImmutableList()))
        }
    }

    /**
     * Captures a clipboard-related change (Paste or Cut).
     */
    data class ClipboardOperation(
        val beforeSnapshot: WEditorSnapshot,
        val afterSnapshot: WEditorSnapshot,
        val type: Type
    ) : WOperation {
        enum class Type { Cut, Paste, PastePlainText }
        override fun redo(snapshot: WEditorSnapshot) = afterSnapshot
        override fun undo(snapshot: WEditorSnapshot) = beforeSnapshot
    }

    /**
     * Captures any mutation produced by WMutationEngine.
     */
    data class GeneralMutation(
        val beforeSnapshot: WEditorSnapshot,
        val afterSnapshot: WEditorSnapshot,
        val mutationType: com.tv.wnotes.ui.editor.engine.logic.WMutationType
    ) : WOperation {
        override fun redo(snapshot: WEditorSnapshot) = afterSnapshot
        override fun undo(snapshot: WEditorSnapshot) = beforeSnapshot
    }
}

/**
 * Data container for the history stacks.
 */
@Immutable
data class WHistory(
    val undoStack: List<WOperation> = emptyList(),
    val redoStack: List<WOperation> = emptyList(),
    val maxHistorySize: Int = 100
)

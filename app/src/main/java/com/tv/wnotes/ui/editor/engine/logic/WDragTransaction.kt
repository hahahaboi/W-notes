package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.model.WTextSpan
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.toImmutableList

/**
 * Encapsulates a complete drag-and-drop operation as an atomic transaction.
 */
data class WDragTransaction(
    val sourceRange: WSelectionRange,
    val targetPoint: WSelectionPoint,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of a drop execution.
 */
sealed interface WTransactionResult {
    data class Success(
        val newSnapshot: WEditorSnapshot,
        val operation: WOperation.MoveSelectionOperation
    ) : WTransactionResult
    
    data class Rejected(val reason: String) : WTransactionResult
}

/**
 * Orchestrates the atomic execution of text/block movement.
 */
object WDropExecutor {

    // Animation hooks for the UI layer
    fun beforeDrop() {}
    fun duringDrop() {}
    fun afterDrop() {}

    /**
     * Executes the drag transaction and returns the resulting state.
     * Ensures all rich text and metadata are preserved.
     */
    fun execute(snapshot: WEditorSnapshot, transaction: WDragTransaction): WTransactionResult {
        val document = snapshot.document
        val source = transaction.sourceRange
        val target = transaction.targetPoint

        // 1. Validation
        if (isTargetInsideSource(source, target, document.blocks)) {
            return WTransactionResult.Rejected("Target is inside source selection")
        }

        val normalizedSource = WSelectionManager.normalize(source, document.blocks)
        
        // Phase 2: Implementation of text movement.
        // We currently support moves where the source is within a single block.
        // This covers "moving a word" or "moving a paragraph (block)".
        if (normalizedSource.anchor.blockId == normalizedSource.focus.blockId) {
            return executeSingleBlockSourceMove(snapshot, normalizedSource, target)
        }

        return WTransactionResult.Rejected("Multi-block source dragging logic is pending complex diffing integration")
    }

    private fun executeSingleBlockSourceMove(
        snapshot: WEditorSnapshot,
        source: WSelectionRange,
        target: WSelectionPoint
    ): WTransactionResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val sourceBlockId = source.anchor.blockId
        val sourceIndex = blocks.indexOfFirst { it.id == sourceBlockId }
        val sourceBlock = blocks[sourceIndex] as? WTextContainer ?: return WTransactionResult.Rejected("Invalid source")

        val start = source.anchor.offset
        val end = source.focus.offset
        val length = end - start

        val movedText = sourceBlock.text.substring(start, end)
        val movedSpans = sourceBlock.spans.filter { it.start >= start && it.end <= end }
            .map { it.copy(start = it.start - start, end = it.end - start) }

        // 1. Remove from source
        val textAfterRemove = StringBuilder(sourceBlock.text).delete(start, end).toString()
        val spansAfterRemove = WSpanManager.shiftSpansForDeletion(sourceBlock.spans, start, end)
        val updatedSourceBlock = sourceBlock.copyWithText(textAfterRemove, spansAfterRemove)

        // 2. Adjust target if in same block
        var adjustedTargetOffset = target.offset
        if (target.blockId == sourceBlockId && adjustedTargetOffset > start) {
            adjustedTargetOffset = (adjustedTargetOffset - length).coerceAtLeast(start)
        }

        // 3. Insert into target
        val targetIndex = blocks.indexOfFirst { it.id == target.blockId }
        val targetBlockRaw = if (target.blockId == sourceBlockId) updatedSourceBlock else blocks[targetIndex]
        val targetBlock = targetBlockRaw as? WTextContainer ?: return WTransactionResult.Rejected("Invalid target")

        val textAfterInsert = StringBuilder(targetBlock.text).insert(adjustedTargetOffset, movedText).toString()
        val baseSpans = if (target.blockId == sourceBlockId) spansAfterRemove else targetBlock.spans
        
        val spansShifted = WSpanManager.shiftSpansForInsertion(baseSpans, adjustedTargetOffset, length)
        val finalSpans = (spansShifted + movedSpans.map { 
            it.copy(start = it.start + adjustedTargetOffset, end = it.end + adjustedTargetOffset) 
        }).toImmutableList()

        val updatedTargetBlock = targetBlock.copyWithText(textAfterInsert, finalSpans)

        // Update list
        if (target.blockId == sourceBlockId) {
            blocks[sourceIndex] = updatedTargetBlock
        } else {
            blocks[sourceIndex] = updatedSourceBlock
            blocks[targetIndex] = updatedTargetBlock
        }

        val newCursorPoint = WSelectionPoint(target.blockId, adjustedTargetOffset + length)
        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(),
                revision = snapshot.document.revision + 1
            ),
            cursor = snapshot.cursor.copy(position = newCursorPoint),
            selection = WSelectionManager.collapseTo(newCursorPoint)
        )

        val operation = WOperation.MoveSelectionOperation(
            beforeSnapshot = snapshot,
            afterSnapshot = newSnapshot
        )

        return WTransactionResult.Success(newSnapshot, operation)
    }

    private fun isTargetInsideSource(
        source: WSelectionRange, 
        target: WSelectionPoint, 
        blocks: List<WBlock>
    ): Boolean {
        return WSelectionManager.contains(
            state = WSelectionState(ranges = listOf(source), isActive = true),
            blockId = target.blockId,
            offset = target.offset,
            blocks = blocks
        )
    }
}

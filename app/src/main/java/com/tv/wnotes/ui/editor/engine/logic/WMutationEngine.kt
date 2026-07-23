package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

/**
 * The core document transformation engine.
 * Ensures every mutation is atomic, immutable, and deterministic.
 */
object WMutationEngine {

    /**
     * Executes a move range operation atomically.
     */
    fun moveRange(
        snapshot: WEditorSnapshot,
        source: WSelectionRange,
        target: WSelectionPoint,
        prefix: String = "",
        suffix: String = "",
        virtualLineCount: Int = 0
    ): WMutationResult {
        val blocks = snapshot.document.blocks
        
        // Validation: Target must not be strictly inside source
        if (WSelectionManager.containsExclusive(
                state = com.tv.wnotes.ui.editor.engine.state.WSelectionState(ranges = listOf(source), isActive = true),
                blockId = target.blockId,
                offset = target.offset,
                blocks = blocks
            )
        ) {
            return WMutationResult.Failure("Cannot drop text strictly inside its own selection", WMutationType.MOVE_RANGE)
        }

        val normalizedSource = WSelectionManager.normalize(source, blocks)
        
        val actualPrefix = if (virtualLineCount > 0) {
            "\n".repeat(virtualLineCount) + prefix
        } else {
            prefix
        }

        return if (normalizedSource.anchor.blockId == normalizedSource.focus.blockId) {
            executeSingleBlockMove(snapshot, normalizedSource, target, actualPrefix, suffix)
        } else {
            executeMultiBlockMove(snapshot, normalizedSource, target, actualPrefix, suffix)
        }
    }

    private fun executeMultiBlockMove(
        snapshot: WEditorSnapshot,
        source: WSelectionRange,
        target: WSelectionPoint,
        prefix: String,
        suffix: String
    ): WMutationResult {
        // Implementation for Stage 13 + Stage 14
        // 1. Identify all blocks in the range
        val blocks = snapshot.document.blocks
        val startBlockIdx = blocks.indexOfFirst { it.id == source.anchor.blockId }
        val endBlockIdx = blocks.indexOfFirst { it.id == source.focus.blockId }
        
        if (startBlockIdx == -1 || endBlockIdx == -1) return WMutationResult.Failure("Invalid range", WMutationType.MOVE_RANGE)
        
        // 2. Extract content
        val extractedContent = mutableListOf<WBlock>()
        for (i in startBlockIdx..endBlockIdx) {
            val block = blocks[i] as? WTextContainer ?: continue
            val start = if (i == startBlockIdx) source.anchor.offset else 0
            val end = if (i == endBlockIdx) source.focus.offset else block.text.length
            
            if (start < end) {
                val text = block.text.substring(start, end)
                val spans = block.spans.filter { it.start >= start && it.end <= end }
                    .map { it.copy(start = it.start - start, end = it.end - start) }
                extractedContent.add(WTextBlock(id = UUID.randomUUID().toString(), text = text, spans = spans.toImmutableList()))
            }
        }
        
        // 3. Delete range
        val deleteResult = deleteRangeInternal(snapshot, source)
        if (deleteResult is WMutationResult.Failure) return deleteResult
        val snapshotAfterDelete = (deleteResult as WMutationResult.Success).snapshot
        
        // 4. Insert at target
        val mergedTextBuilder = StringBuilder()
        val mergedSpans = mutableListOf<com.tv.wnotes.ui.editor.engine.model.WTextSpan>()
        
        // Apply prefix
        mergedTextBuilder.append(prefix)

        extractedContent.forEachIndexed { index, block ->
            val container = block as WTextContainer
            val offset = mergedTextBuilder.length
            
            mergedTextBuilder.append(container.text)
            container.spans.forEach { span ->
                mergedSpans.add(span.copy(start = span.start + offset, end = span.end + offset))
            }
            
            if (index < extractedContent.size - 1) {
                mergedTextBuilder.append("\n")
            }
        }

        // Apply suffix
        mergedTextBuilder.append(suffix)
        
        return insertRichTextAtPoint(snapshotAfterDelete, target, mergedTextBuilder.toString(), mergedSpans)
    }

    private fun deleteRangeInternal(snapshot: WEditorSnapshot, range: WSelectionRange): WMutationResult {
        // Atomic deletion of a range across blocks
        val nextSnapshot = WContentManager.deleteSelection(snapshot.copy(selection = snapshot.selection.copy(ranges = listOf(range), isActive = true)))
        return WMutationResult.Success(snapshot = nextSnapshot, mutationType = WMutationType.DELETE_RANGE, affectedBlockIds = emptySet(), affectedCharacterCount = 0)
    }

    private fun insertRichTextAtPoint(
        snapshot: WEditorSnapshot, 
        point: WSelectionPoint, 
        text: String,
        spans: List<com.tv.wnotes.ui.editor.engine.model.WTextSpan>
    ): WMutationResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == point.blockId }
        if (index == -1) return WMutationResult.Failure("Invalid target", WMutationType.MOVE_RANGE)
        
        val block = blocks[index] as? WTextContainer ?: return WMutationResult.Failure("Target is not a text container", WMutationType.MOVE_RANGE)
        
        val newText = StringBuilder(block.text).insert(point.offset, text).toString()
        val shiftedBaseSpans = WSpanManager.shiftSpansForInsertion(block.spans, point.offset, text.length)
        val finalSpans = (shiftedBaseSpans + spans.map { it.copy(start = it.start + point.offset, end = it.end + point.offset) }).toImmutableList()
        
        blocks[index] = block.copyWithText(newText, finalSpans)
        
        val newCursor = WSelectionPoint(point.blockId, point.offset + text.length)
        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
            cursor = snapshot.cursor.copy(position = newCursor, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newCursor)
        )
        
        return WMutationResult.Success(snapshot = newSnapshot, mutationType = WMutationType.MOVE_RANGE, affectedBlockIds = setOf(point.blockId), affectedCharacterCount = text.length)
    }

    private fun executeSingleBlockMove(
        snapshot: WEditorSnapshot,
        source: WSelectionRange,
        target: WSelectionPoint,
        prefix: String,
        suffix: String
    ): WMutationResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val sourceBlockId = source.anchor.blockId
        val sourceIndex = blocks.indexOfFirst { it.id == sourceBlockId }
        val sourceBlock = blocks[sourceIndex] as? WTextContainer ?: return WMutationResult.Failure("Invalid source", WMutationType.MOVE_RANGE)

        val start = source.anchor.offset
        val end = source.focus.offset
        val length = end - start

        val movedText = prefix + sourceBlock.text.substring(start, end) + suffix
        val movedSpans = sourceBlock.spans.filter { it.start >= start && it.end <= end }
            .map { it.copy(start = it.start - start + prefix.length, end = it.end - start + prefix.length) }

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
        val targetBlock = targetBlockRaw as? WTextContainer ?: return WMutationResult.Failure("Invalid target", WMutationType.MOVE_RANGE)

        val textAfterInsert = StringBuilder(targetBlock.text).insert(adjustedTargetOffset, movedText).toString()
        val baseSpans = if (target.blockId == sourceBlockId) spansAfterRemove else targetBlock.spans
        
        val spansShifted = WSpanManager.shiftSpansForInsertion(baseSpans, adjustedTargetOffset, length)
        val finalSpans = (spansShifted + movedSpans.map { 
            it.copy(start = it.start + adjustedTargetOffset, end = it.end + adjustedTargetOffset) 
        }).toImmutableList()

        val updatedTargetBlock = targetBlock.copyWithText(textAfterInsert, finalSpans)

        // Update list
        val affectedIds = mutableSetOf(sourceBlockId, target.blockId)
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

        return WMutationResult.Success(
            snapshot = newSnapshot,
            mutationType = WMutationType.MOVE_RANGE,
            affectedBlockIds = affectedIds,
            affectedCharacterCount = length
        )
    }

    /**
     * Inserts text at the current cursor.
     */
    fun insertText(snapshot: WEditorSnapshot, text: String): WMutationResult {
        // Reuse logic from WContentManager but return WMutationResult
        val nextSnapshot = WContentManager.insertText(snapshot, text)
        val blockId = snapshot.cursor.position.blockId
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.INSERT_TEXT,
            affectedBlockIds = setOf(blockId),
            affectedCharacterCount = text.length
        )
    }

    /**
     * Deletes selection or character backward.
     */
    fun deleteBackward(snapshot: WEditorSnapshot): WMutationResult {
        if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            return deleteSelection(snapshot)
        }
        
        val blockId = snapshot.cursor.position.blockId
        val nextSnapshot = WContentManager.deleteBackward(snapshot)
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.DELETE_RANGE,
            affectedBlockIds = setOf(blockId),
            affectedCharacterCount = 1
        )
    }

    /**
     * Deletes selection or character forward.
     */
    fun deleteForward(snapshot: WEditorSnapshot): WMutationResult {
        if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            return deleteSelection(snapshot)
        }
        
        // Basic forward delete within block for now
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == cursor.blockId }
        if (index == -1) return WMutationResult.Failure("Invalid cursor", WMutationType.DELETE_RANGE)
        
        val block = blocks[index]
        if (block !is WTextContainer) return WMutationResult.Failure("Invalid block", WMutationType.DELETE_RANGE)
        if (cursor.offset >= block.text.length) return WMutationResult.Failure("End of block", WMutationType.DELETE_RANGE)
        
        val newText = StringBuilder(block.text).deleteAt(cursor.offset).toString()
        val newSpans = WSpanManager.shiftSpansForDeletion(block.spans, cursor.offset, cursor.offset + 1)
        
        blocks[index] = block.copyWithText(newText, newSpans)
        
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1)
        )
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.DELETE_RANGE,
            affectedBlockIds = setOf(block.id),
            affectedCharacterCount = 1
        )
    }

    /**
     * Deletes the currently selected range.
     */
    fun deleteSelection(snapshot: WEditorSnapshot): WMutationResult {
        if (!snapshot.selection.isActive) return WMutationResult.Failure("No selection", WMutationType.DELETE_RANGE)
        
        val nextSnapshot = WContentManager.deleteSelection(snapshot)
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.DELETE_RANGE,
            affectedBlockIds = emptySet(), // ContentManager should ideally provide this
            affectedCharacterCount = 0
        )
    }

    /**
     * Splits a block (Enter).
     */
    fun splitBlock(snapshot: WEditorSnapshot): WMutationResult {
        val blockId = snapshot.cursor.position.blockId
        val nextSnapshot = WContentManager.splitBlock(snapshot)
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.SPLIT_BLOCK,
            affectedBlockIds = setOf(blockId), // Actually affects old and new block
            affectedCharacterCount = 0
        )
    }
}

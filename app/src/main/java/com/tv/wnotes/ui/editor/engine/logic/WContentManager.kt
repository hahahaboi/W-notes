package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.model.TextBlockType
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

/**
 * The exclusive subsystem for modifying document content.
 * Orchestrates text editing, block operations, and state synchronization.
 */
object WContentManager {

    /**
     * Inserts text at the current cursor position or replaces selection.
     */
    fun insertText(snapshot: WEditorSnapshot, text: String): WEditorSnapshot {
        val workingSnapshot = if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            deleteSelection(snapshot)
        } else {
            snapshot
        }

        val cursor = workingSnapshot.cursor.position
        val blocks = workingSnapshot.document.blocks.toMutableList()
        val blockIndex = blocks.indexOfFirst { it.id == cursor.blockId }
        
        if (blockIndex == -1) return workingSnapshot
        val block = blocks[blockIndex]
        if (block !is WTextContainer) return workingSnapshot

        val newText = StringBuilder(block.text).insert(cursor.offset, text).toString()
        val newSpans = WSpanManager.shiftSpansForInsertion(block.spans, cursor.offset, text.length)
        
        val updatedBlock = block.copyWithText(newText, newSpans)
        blocks[blockIndex] = updatedBlock

        // Update Annotations
        val newAnnotations = WAnnotationManager.shiftAnnotationsForInsertion(
            workingSnapshot.annotations,
            cursor.blockId,
            cursor.offset,
            text.length
        )

        val newPoint = WSelectionPoint(block.id, cursor.offset + text.length)
        
        return workingSnapshot.copy(
            document = workingSnapshot.document.copy(
                blocks = blocks.toImmutableList(),
                revision = workingSnapshot.document.revision + 1
            ),
            annotations = newAnnotations.toImmutableList(),
            cursor = workingSnapshot.cursor.copy(position = newPoint, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newPoint)
        )
    }

    /**
     * Deletes the character before the cursor or the current selection.
     */
    fun deleteBackward(snapshot: WEditorSnapshot): WEditorSnapshot {
        if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            return deleteSelection(snapshot)
        }

        val cursor = snapshot.cursor.position
        if (cursor.offset > 0) {
            // Simple backspace within block
            val blocks = snapshot.document.blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == cursor.blockId }
            val block = blocks[index]
            if (block !is WTextContainer) return snapshot

            val newText = StringBuilder(block.text).deleteAt(cursor.offset - 1).toString()
            val newSpans = WSpanManager.shiftSpansForDeletion(block.spans, cursor.offset - 1, cursor.offset)
            
            blocks[index] = block.copyWithText(newText, newSpans)

            // Update Annotations
            val newAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                snapshot.annotations,
                cursor.blockId,
                cursor.offset - 1,
                cursor.offset
            )

            val newPoint = cursor.copy(offset = cursor.offset - 1)
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
                annotations = newAnnotations.toImmutableList(),
                cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
                selection = WSelectionManager.collapseTo(newPoint)
            )
        } else {
            // Merge with previous block
            return mergeWithPreviousBlock(snapshot, cursor.blockId)
        }
    }

    /**
     * Splits a block at the current cursor position (Enter key).
     */
    fun splitBlock(snapshot: WEditorSnapshot): WEditorSnapshot {
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == cursor.blockId }
        if (index == -1) return snapshot
        
        val block = blocks[index]
        if (block !is WTextContainer) return snapshot

        val headText = block.text.substring(0, cursor.offset)
        val tailText = block.text.substring(cursor.offset)
        
        val (headSpans, tailSpans) = WSpanManager.splitSpans(block.spans, cursor.offset)

        val updatedHead = block.copyWithText(headText, headSpans)
        
        val newTailId = UUID.randomUUID().toString()
        val newTail = WTextBlock(
            id = newTailId,
            text = tailText,
            spans = tailSpans,
            type = if (block is WTextBlock) block.type else TextBlockType.Body,
            parentBlockId = block.parentBlockId
        )

        blocks[index] = updatedHead
        blocks.add(index + 1, newTail)

        // Split Annotations
        val newAnnotations = WAnnotationManager.splitAnnotations(
            snapshot.annotations,
            block.id,
            newTailId,
            cursor.offset
        )

        val newPoint = WSelectionPoint(newTailId, 0)
        return snapshot.copy(
            document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
            annotations = newAnnotations.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newPoint)
        )
    }

    private fun mergeWithPreviousBlock(snapshot: WEditorSnapshot, blockId: String): WEditorSnapshot {
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index <= 0) return snapshot // Nothing to merge with

        val prevBlock = blocks[index - 1]
        val currentBlock = blocks[index]
        
        if (prevBlock !is WTextContainer || currentBlock !is WTextContainer) return snapshot

        val prevTextLen = prevBlock.text.length
        val newText = prevBlock.text + currentBlock.text
        val shiftedCurrentSpans = currentBlock.spans.map { 
            it.copy(start = it.start + prevTextLen, end = it.end + prevTextLen) 
        }
        val newSpans = (prevBlock.spans + shiftedCurrentSpans).toImmutableList()

        val mergedBlock = prevBlock.copyWithText(newText, newSpans)

        // Merge Annotations
        val currentAnnotations = WAnnotationManager.mergeAnnotations(
            snapshot.annotations,
            prevBlock.id,
            blockId,
            prevTextLen
        )

        val cursorOffset = prevBlock.text.length
        blocks.removeAt(index)
        blocks[index - 1] = mergedBlock

        val newPoint = WSelectionPoint(mergedBlock.id, cursorOffset)
        return snapshot.copy(
            document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
            annotations = currentAnnotations.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newPoint)
        )
    }

    fun deleteSelection(snapshot: WEditorSnapshot): WEditorSnapshot {
        if (snapshot.selection.ranges.isEmpty()) return snapshot
        
        val activeRangeId = snapshot.selection.activeRangeId
        val targetRange = if (activeRangeId != null) {
            snapshot.selection.ranges.find { it.id == activeRangeId } ?: snapshot.selection.ranges.first()
        } else {
            snapshot.selection.ranges.first()
        }
        
        val range = WSelectionManager.normalize(targetRange, snapshot.document.blocks)
        val blocks = snapshot.document.blocks.toMutableList()
        
        val startIdx = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val endIdx = blocks.indexOfFirst { it.id == range.focus.blockId }
        
        if (startIdx == -1 || endIdx == -1) return snapshot

        if (startIdx == endIdx) {
            // Single block deletion
            val block = blocks[startIdx]
            if (block !is WTextContainer) return snapshot
            val newText = StringBuilder(block.text).delete(range.anchor.offset, range.focus.offset).toString()
            val newSpans = WSpanManager.shiftSpansForDeletion(block.spans, range.anchor.offset, range.focus.offset)
            
            blocks[startIdx] = block.copyWithText(newText, newSpans)

            // Update Annotations
            val newAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                snapshot.annotations,
                block.id,
                range.anchor.offset,
                range.focus.offset
            )

            val newPoint = WSelectionPoint(block.id, range.anchor.offset)
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
                annotations = newAnnotations.toImmutableList(),
                cursor = snapshot.cursor.copy(position = newPoint),
                selection = WSelectionManager.collapseTo(newPoint)
            )
        } else {
            // Multi-block deletion: merge start and end blocks, remove middle ones
            val startBlock = blocks[startIdx]
            val endBlock = blocks[endIdx]
            if (startBlock !is WTextContainer || endBlock !is WTextContainer) return snapshot
            
            val remainingHead = startBlock.text.substring(0, range.anchor.offset)
            val remainingTail = endBlock.text.substring(range.focus.offset)
            
            val headSpans = WSpanManager.shiftSpansForDeletion(startBlock.spans, range.anchor.offset, startBlock.text.length)
            val tailSpans = WSpanManager.shiftSpansForDeletion(endBlock.spans, 0, range.focus.offset).map {
                it.copy(start = it.start + remainingHead.length, end = it.end + remainingHead.length)
            }

            val mergedBlock = startBlock.copyWithText(remainingHead + remainingTail, (headSpans + tailSpans).toImmutableList())
            
            // Annotations handling for multi-block delete:
            var currentAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                snapshot.annotations,
                startBlock.id,
                range.anchor.offset,
                startBlock.text.length
            )
            
            currentAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                currentAnnotations,
                endBlock.id,
                0,
                range.focus.offset
            ).map { annotation ->
                if (annotation.blockId == endBlock.id) {
                    annotation.copy(
                        blockId = startBlock.id,
                        startOffset = annotation.startOffset + remainingHead.length,
                        endOffset = annotation.endOffset + remainingHead.length
                    )
                } else annotation
            }
            
            val middleBlockIds = blocks.subList(startIdx + 1, endIdx).map { it.id }.toSet()
            currentAnnotations = currentAnnotations.filter { it.blockId !in middleBlockIds }

            // Remove blocks from startIdx to endIdx and insert merged
            val subList = blocks.subList(startIdx, endIdx + 1)
            subList.clear()
            blocks.add(startIdx, mergedBlock)

            val newPoint = WSelectionPoint(mergedBlock.id, range.anchor.offset)
            return snapshot.copy(
                document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
                annotations = currentAnnotations.toImmutableList(),
                cursor = snapshot.cursor.copy(position = newPoint),
                selection = WSelectionManager.collapseTo(newPoint)
            )
        }
    }
}

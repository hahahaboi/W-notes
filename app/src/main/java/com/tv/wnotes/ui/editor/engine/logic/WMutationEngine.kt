package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.*
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
    fun moveRanges(
        snapshot: WEditorSnapshot,
        sources: List<WSelectionRange>,
        target: WSelectionPoint,
        prefix: String = "",
        suffix: String = "",
        virtualLineCount: Int = 0
    ): WMutationResult {
        val blocks = snapshot.document.blocks
        
        // 1. Validation: Target must not be strictly inside any source
        if (WSelectionManager.containsAnyExclusive(sources, target.blockId, target.offset, blocks)) {
            return WMutationResult.Failure("Cannot drop text strictly inside its own selection", WMutationType.MOVE_RANGE)
        }

        // 2. Capture content in selection order
        val capturedBlocks = mutableListOf<WBlock>()
        val capturedAnnotations = mutableListOf<ContextAnnotation>()

        sources.forEach { range ->
            val normalized = WSelectionManager.normalize(range, blocks)
            val (fragment, annotations) = captureFragmentWithAnnotations(snapshot, normalized)
            
            if (capturedBlocks.isNotEmpty() && capturedBlocks.last() is WTextBlock && fragment.isNotEmpty() && fragment[0] is WTextBlock) {
                // Merge text blocks with a space
                val last = capturedBlocks.last() as WTextBlock
                val first = fragment[0] as WTextBlock
                val shift = last.text.length + 1
                val joinedText = last.text + " " + first.text
                val shiftedFirstSpans = first.spans.map { it.copy(start = it.start + shift, end = it.end + shift) }
                val joinedSpans = (last.spans + shiftedFirstSpans).toImmutableList()
                capturedBlocks[capturedBlocks.size - 1] = last.copyWithText(joinedText, joinedSpans)
                
                // Shift and add annotations for the merged block
                annotations.filter { it.blockId == first.id }.forEach { ann ->
                    capturedAnnotations.add(ann.copy(blockId = last.id, startOffset = ann.startOffset + shift, endOffset = ann.endOffset + shift))
                }
                capturedAnnotations.addAll(annotations.filter { it.blockId != first.id })
                capturedBlocks.addAll(fragment.drop(1))
            } else {
                capturedBlocks.addAll(fragment)
                capturedAnnotations.addAll(annotations)
            }
        }

        // 3. Delete source ranges in reverse document order
        val sortedSources = WSelectionManager.normalizeSelectionOrder(sources, blocks).reversed()
        var currentSnapshot = snapshot
        var adjustedTarget = target

        sortedSources.forEach { range ->
            val blocksBeforeDelete = currentSnapshot.document.blocks
            val normalized = WSelectionManager.normalize(range, blocksBeforeDelete)
            
            // Adjust target if it's in the same block and after the deleted range
            if (adjustedTarget.blockId == normalized.anchor.blockId && adjustedTarget.offset > normalized.anchor.offset) {
                if (normalized.anchor.blockId == normalized.focus.blockId) {
                    val length = normalized.focus.offset - normalized.anchor.offset
                    adjustedTarget = adjustedTarget.copy(offset = (adjustedTarget.offset - length).coerceAtLeast(normalized.anchor.offset))
                }
            }
            
            val deleteRes = deleteRangeInternal(currentSnapshot, normalized)
            if (deleteRes is WMutationResult.Success) {
                currentSnapshot = deleteRes.snapshot
            }
        }

        // 4. Combine captured content into string + spans + annotations for insertion
        val mergedTextBuilder = StringBuilder()
        val mergedSpans = mutableListOf<WTextSpan>()
        val mergedAnnotations = mutableListOf<ContextAnnotation>()
        
        val actualPrefix = if (virtualLineCount > 0) "\n".repeat(virtualLineCount) + prefix else prefix
        mergedTextBuilder.append(actualPrefix)

        capturedBlocks.forEachIndexed { index, block ->
            val container = block as WTextContainer
            val offset = mergedTextBuilder.length
            mergedTextBuilder.append(container.text)
            container.spans.forEach { span ->
                mergedSpans.add(span.copy(start = span.start + offset, end = span.end + offset))
            }
            // Map captured annotations to the final insertion block
            capturedAnnotations.filter { it.blockId == block.id }.forEach { ann ->
                mergedAnnotations.add(ann.copy(startOffset = ann.startOffset + offset, endOffset = ann.endOffset + offset))
            }
            if (index < capturedBlocks.size - 1) {
                mergedTextBuilder.append("\n")
            }
        }
        mergedTextBuilder.append(suffix)

        return insertRichTextAtPoint(currentSnapshot, adjustedTarget, mergedTextBuilder.toString(), mergedSpans, mergedAnnotations)
    }

    private fun captureFragmentWithAnnotations(snapshot: WEditorSnapshot, range: WSelectionRange): Pair<List<WBlock>, List<ContextAnnotation>> {
        val document = snapshot.document
        val blocks = document.blocks
        val startBlockIdx = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val endBlockIdx = blocks.indexOfFirst { it.id == range.focus.blockId }
        
        if (startBlockIdx == -1 || endBlockIdx == -1) return emptyList<WBlock>() to emptyList()
        
        val extractedContent = mutableListOf<WBlock>()
        val extractedAnnotations = mutableListOf<ContextAnnotation>()

        for (i in startBlockIdx..endBlockIdx) {
            val block = blocks[i] as? WTextContainer ?: continue
            val start = if (i == startBlockIdx) range.anchor.offset else 0
            val end = if (i == endBlockIdx) range.focus.offset else block.text.length
            
            if (start < end) {
                val text = block.text.substring(start, end)
                val spans = block.spans.filter { it.start >= start && it.end <= end }
                    .map { it.copy(start = it.start - start, end = it.end - start) }
                val newBlockId = UUID.randomUUID().toString()
                extractedContent.add(WTextBlock(id = newBlockId, text = text, spans = spans.toImmutableList()))
                
                // Capture annotations strictly inside the range
                snapshot.annotations.filter { it.blockId == (blocks[i].id) && it.startOffset >= start && it.endOffset <= end }.forEach { ann ->
                    extractedAnnotations.add(ann.copy(
                        blockId = newBlockId,
                        startOffset = ann.startOffset - start,
                        endOffset = ann.endOffset - start
                    ))
                }
            }
        }
        return extractedContent to extractedAnnotations
    }

    private fun insertRichTextAtPoint(
        snapshot: WEditorSnapshot, 
        point: WSelectionPoint, 
        text: String,
        spans: List<WTextSpan>,
        newAnns: List<ContextAnnotation> = emptyList()
    ): WMutationResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == point.blockId }
        if (index == -1) return WMutationResult.Failure("Invalid target", WMutationType.MOVE_RANGE)
        
        val block = blocks[index] as? WTextContainer ?: return WMutationResult.Failure("Target is not a text container", WMutationType.MOVE_RANGE)
        
        val newText = StringBuilder(block.text).insert(point.offset, text).toString()
        val shiftedBaseSpans = WSpanManager.shiftSpansForInsertion(block.spans, point.offset, text.length)
        val finalSpans = (shiftedBaseSpans + spans.map { it.copy(start = it.start + point.offset, end = it.end + point.offset) }).toImmutableList()
        
        blocks[index] = block.copyWithText(newText, finalSpans)
        
        // Update existing annotations
        val shiftedAnnotations = WAnnotationManager.shiftAnnotationsForInsertion(
            snapshot.annotations,
            point.blockId,
            point.offset,
            text.length
        )

        // Update existing markers
        val shiftedMarkers = WMarkerManager.shiftMarkersForInsertion(
            snapshot.markers,
            point.blockId,
            point.offset,
            text.length
        )
        
        // Add moved annotations
        val finalAnnotations = shiftedAnnotations + newAnns.map { ann ->
            ann.copy(blockId = point.blockId, startOffset = ann.startOffset + point.offset, endOffset = ann.endOffset + point.offset)
        }

        val newCursor = WSelectionPoint(point.blockId, point.offset + text.length)
        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(), 
                revision = snapshot.document.revision + 1
            ),
            annotations = finalAnnotations.toImmutableList(),
            markers = shiftedMarkers.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newCursor, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newCursor),
            activeAnnotationId = null
        )
        
        return WMutationResult.Success(snapshot = newSnapshot, mutationType = WMutationType.MOVE_RANGE, affectedBlockIds = setOf(point.blockId), affectedCharacterCount = text.length)
    }

    fun insertText(snapshot: WEditorSnapshot, text: String): WMutationResult {
        if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            return replaceSelection(snapshot, text)
        }

        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val blockIndex = blocks.indexOfFirst { it.id == cursor.blockId }
        
        if (blockIndex == -1) return WMutationResult.Failure("Invalid cursor", WMutationType.INSERT_TEXT)
        val block = blocks[blockIndex]
        if (block !is WTextContainer) return WMutationResult.Failure("Invalid block", WMutationType.INSERT_TEXT)

        val newText = StringBuilder(block.text).insert(cursor.offset, text).toString()
        val newSpans = WSpanManager.shiftSpansForInsertion(block.spans, cursor.offset, text.length)
        
        blocks[blockIndex] = block.copyWithText(newText, newSpans)

        // Update Annotations
        val newAnnotations = WAnnotationManager.shiftAnnotationsForInsertion(
            snapshot.annotations,
            cursor.blockId,
            cursor.offset,
            text.length
        )

        // Update Markers
        val newMarkers = WMarkerManager.shiftMarkersForInsertion(
            snapshot.markers,
            cursor.blockId,
            cursor.offset,
            text.length
        )

        val newPoint = WSelectionPoint(block.id, cursor.offset + text.length)
        
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(),
                revision = snapshot.document.revision + 1
            ),
            annotations = newAnnotations.toImmutableList(),
            markers = newMarkers.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newPoint),
            activeAnnotationId = null
        )

        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.INSERT_TEXT,
            affectedBlockIds = setOf(cursor.blockId),
            affectedCharacterCount = text.length
        )
    }

    private fun replaceSelection(snapshot: WEditorSnapshot, text: String): WMutationResult {
        if (!WSelectionManager.isSingleBlock(snapshot.selection)) {
            // For multi-block, we fall back to delete + insert
            val delRes = deleteSelection(snapshot)
            return if (delRes is WMutationResult.Success) insertText(delRes.snapshot, text) else delRes
        }

        val range = WSelectionManager.normalize(snapshot.selection.ranges.first(), snapshot.document.blocks)
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val block = blocks[index] as WTextContainer

        val newText = StringBuilder(block.text).replace(range.anchor.offset, range.focus.offset, text).toString()
        val delSpans = WSpanManager.shiftSpansForDeletion(block.spans, range.anchor.offset, range.focus.offset)
        val newSpans = WSpanManager.shiftSpansForInsertion(delSpans, range.anchor.offset, text.length)

        blocks[index] = block.copyWithText(newText, newSpans)

        val newAnns = WAnnotationManager.replaceAnnotations(
            snapshot.annotations,
            range.anchor.blockId,
            range.anchor.offset,
            range.focus.offset,
            text.length
        )

        // Markers: simplified for replacement (shift start, then end)
        var shiftedMarkers = WMarkerManager.shiftMarkersForDeletion(
            snapshot.markers,
            range.anchor.blockId,
            range.anchor.offset,
            range.focus.offset,
            snapshot.document.blocks
        )
        shiftedMarkers = WMarkerManager.shiftMarkersForInsertion(
            shiftedMarkers,
            range.anchor.blockId,
            range.anchor.offset,
            text.length
        )

        val newPoint = WSelectionPoint(range.anchor.blockId, range.anchor.offset + text.length)
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
            annotations = newAnns.toImmutableList(),
            markers = shiftedMarkers.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newPoint),
            selection = WSelectionManager.collapseTo(newPoint),
            activeAnnotationId = null
        )

        return WMutationResult.Success(nextSnapshot, WMutationType.REPLACE_RANGE, setOf(range.anchor.blockId), text.length)
    }

    fun deleteBackward(snapshot: WEditorSnapshot): WMutationResult {
        if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            return deleteSelection(snapshot)
        }
        
        val cursor = snapshot.cursor.position
        if (cursor.offset > 0) {
            val blocks = snapshot.document.blocks.toMutableList()
            val index = blocks.indexOfFirst { it.id == cursor.blockId }
            val block = blocks[index]
            if (block !is WTextContainer) return WMutationResult.Failure("Invalid block", WMutationType.DELETE_RANGE)

            val newText = StringBuilder(block.text).deleteAt(cursor.offset - 1).toString()
            val newSpans = WSpanManager.shiftSpansForDeletion(block.spans, cursor.offset - 1, cursor.offset)
            
            blocks[index] = block.copyWithText(newText, newSpans)

            val newAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                snapshot.annotations,
                cursor.blockId,
                cursor.offset - 1,
                cursor.offset
            )

            val newMarkers = WMarkerManager.shiftMarkersForDeletion(
                snapshot.markers,
                cursor.blockId,
                cursor.offset - 1,
                cursor.offset,
                snapshot.document.blocks
            )

            val newPoint = cursor.copy(offset = cursor.offset - 1)
            val nextSnapshot = snapshot.copy(
                document = snapshot.document.copy(
                    blocks = blocks.toImmutableList(), 
                    revision = snapshot.document.revision + 1
                ),
                annotations = newAnnotations.toImmutableList(),
                markers = newMarkers.toImmutableList(),
                cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
                selection = WSelectionManager.collapseTo(newPoint),
                activeAnnotationId = null
            )
            return WMutationResult.Success(nextSnapshot, WMutationType.DELETE_RANGE, setOf(cursor.blockId), 1)
        } else {
            return mergeWithPreviousBlock(snapshot, cursor.blockId)
        }
    }

    private fun mergeWithPreviousBlock(snapshot: WEditorSnapshot, blockId: String): WMutationResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index <= 0) return WMutationResult.Success(snapshot, WMutationType.DELETE_RANGE, emptySet(), 0)

        val prevBlock = blocks[index - 1]
        val currentBlock = blocks[index]
        
        if (prevBlock !is WTextContainer || currentBlock !is WTextContainer) 
            return WMutationResult.Failure("Cannot merge non-text blocks", WMutationType.DELETE_RANGE)

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

        // Merge Markers
        val currentMarkers = WMarkerManager.mergeMarkers(
            snapshot.markers,
            prevBlock.id,
            blockId,
            prevTextLen
        )

        blocks.removeAt(index)
        blocks[index - 1] = mergedBlock

        val newPoint = WSelectionPoint(mergedBlock.id, prevTextLen)
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(), 
                revision = snapshot.document.revision + 1
            ),
            annotations = currentAnnotations.toImmutableList(),
            markers = currentMarkers.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newPoint),
            activeAnnotationId = null
        )
        return WMutationResult.Success(nextSnapshot, WMutationType.DELETE_RANGE, setOf(prevBlock.id, blockId), 0)
    }

    fun deleteForward(snapshot: WEditorSnapshot): WMutationResult {
        if (snapshot.selection.isActive && !WSelectionManager.isCollapsed(snapshot.selection)) {
            return deleteSelection(snapshot)
        }
        
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == cursor.blockId }
        if (index == -1) return WMutationResult.Failure("Invalid cursor", WMutationType.DELETE_RANGE)
        
        val block = blocks[index]
        if (block !is WTextContainer) return WMutationResult.Failure("Invalid block", WMutationType.DELETE_RANGE)
        
        if (cursor.offset >= block.text.length) {
            // Merge with next block
            if (index < blocks.size - 1) {
                return mergeWithPreviousBlock(snapshot, blocks[index + 1].id)
            }
            return WMutationResult.Failure("End of document", WMutationType.DELETE_RANGE)
        }
        
        val newText = StringBuilder(block.text).deleteAt(cursor.offset).toString()
        val newSpans = WSpanManager.shiftSpansForDeletion(block.spans, cursor.offset, cursor.offset + 1)
        
        blocks[index] = block.copyWithText(newText, newSpans)

        val newAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
            snapshot.annotations,
            cursor.blockId,
            cursor.offset,
            cursor.offset + 1
        )

        val newMarkers = WMarkerManager.shiftMarkersForDeletion(
            snapshot.markers,
            cursor.blockId,
            cursor.offset,
            cursor.offset + 1,
            snapshot.document.blocks
        )
        
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(), 
                revision = snapshot.document.revision + 1
            ),
            annotations = newAnnotations.toImmutableList(),
            markers = newMarkers.toImmutableList(),
            activeAnnotationId = null
        )
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.DELETE_RANGE,
            affectedBlockIds = setOf(block.id),
            affectedCharacterCount = 1
        )
    }

    fun applySpanToSelection(snapshot: WEditorSnapshot, spanType: SpanType): WMutationResult {
        if (!snapshot.selection.isActive || snapshot.selection.ranges.isEmpty()) {
            return WMutationResult.Failure("No selection", WMutationType.FORMAT_SELECTION)
        }

        var currentSnapshot = snapshot
        val affectedIds = mutableSetOf<String>()

        snapshot.selection.ranges.forEach { range ->
            val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
            val blocks = currentSnapshot.document.blocks.toMutableList()
            
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            
            if (startIdx != -1 && endIdx != -1) {
                for (i in startIdx..endIdx) {
                    val block = blocks[i]
                    if (block !is WTextContainer) continue

                    val start = if (i == startIdx) normalized.anchor.offset else 0
                    val end = if (i == endIdx) normalized.focus.offset else block.text.length
                    
                    if (start < end) {
                        val newSpan = WTextSpan(start, end, spanType)
                        val newSpans = (block.spans + newSpan).toImmutableList()
                        blocks[i] = block.copyWithText(block.text, newSpans)
                        affectedIds.add(block.id)
                    }
                }
                currentSnapshot = currentSnapshot.copy(
                    document = currentSnapshot.document.copy(
                        blocks = blocks.toImmutableList(),
                        revision = currentSnapshot.document.revision + 1
                    )
                )
            }
        }

        return WMutationResult.Success(
            snapshot = currentSnapshot,
            mutationType = WMutationType.FORMAT_SELECTION,
            affectedBlockIds = affectedIds,
            affectedCharacterCount = 0
        )
    }

    fun insertBlock(snapshot: WEditorSnapshot, block: WBlock, index: Int? = null): WMutationResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val targetIndex = index ?: (blocks.indexOfFirst { it.id == snapshot.cursor.position.blockId } + 1).coerceIn(0, blocks.size)
        
        blocks.add(targetIndex, block)
        
        return WMutationResult.Success(
            snapshot = snapshot.copy(
                document = snapshot.document.copy(
                    blocks = blocks.toImmutableList(),
                    revision = snapshot.document.revision + 1
                )
            ),
            mutationType = WMutationType.INSERT_BLOCK,
            affectedBlockIds = setOf(block.id),
            affectedCharacterCount = 0
        )
    }

    fun updateBlock(snapshot: WEditorSnapshot, updatedBlock: WBlock): WMutationResult {
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == updatedBlock.id }
        if (index == -1) return WMutationResult.Failure("Block not found", WMutationType.UPDATE_BLOCK)
        
        blocks[index] = updatedBlock
        
        return WMutationResult.Success(
            snapshot = snapshot.copy(
                document = snapshot.document.copy(
                    blocks = blocks.toImmutableList(),
                    revision = snapshot.document.revision + 1
                )
            ),
            mutationType = WMutationType.UPDATE_BLOCK,
            affectedBlockIds = setOf(updatedBlock.id),
            affectedCharacterCount = 0
        )
    }

    fun deleteSelection(snapshot: WEditorSnapshot): WMutationResult {
        if (!snapshot.selection.isActive || snapshot.selection.ranges.isEmpty()) {
            return WMutationResult.Failure("No selection", WMutationType.DELETE_RANGE)
        }
        
        val blocks = snapshot.document.blocks
        val sortedReverse = WSelectionManager.normalizeSelectionOrder(snapshot.selection.ranges, blocks).reversed()
        
        var currentSnapshot = snapshot
        val affectedIds = mutableSetOf<String>()

        sortedReverse.forEach { range ->
            val result = deleteRangeInternal(currentSnapshot, range)
            if (result is WMutationResult.Success) {
                currentSnapshot = result.snapshot
                affectedIds.addAll(result.affectedBlockIds)
            }
        }
        
        return WMutationResult.Success(
            snapshot = currentSnapshot,
            mutationType = WMutationType.DELETE_RANGE,
            affectedBlockIds = affectedIds,
            affectedCharacterCount = 0
        )
    }

    private fun deleteRangeInternal(snapshot: WEditorSnapshot, range: WSelectionRange): WMutationResult {
        val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
        val blocks = snapshot.document.blocks.toMutableList()
        
        val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
        val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
        
        if (startIdx == -1 || endIdx == -1) return WMutationResult.Failure("Invalid range", WMutationType.DELETE_RANGE)

        if (startIdx == endIdx) {
            val block = blocks[startIdx]
            if (block !is WTextContainer) return WMutationResult.Failure("Invalid block", WMutationType.DELETE_RANGE)
            
            val newText = StringBuilder(block.text).delete(normalized.anchor.offset, normalized.focus.offset).toString()
            val newSpans = WSpanManager.shiftSpansForDeletion(block.spans, normalized.anchor.offset, normalized.focus.offset)
            
            blocks[startIdx] = block.copyWithText(newText, newSpans)

            val newAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                snapshot.annotations,
                block.id,
                normalized.anchor.offset,
                normalized.focus.offset
            )

            val newMarkers = WMarkerManager.shiftMarkersForDeletion(
                snapshot.markers,
                block.id,
                normalized.anchor.offset,
                normalized.focus.offset,
                snapshot.document.blocks
            )

            val newPoint = WSelectionPoint(block.id, normalized.anchor.offset)
            val nextSnapshot = snapshot.copy(
                document = snapshot.document.copy(
                    blocks = blocks.toImmutableList(), 
                    revision = snapshot.document.revision + 1
                ),
                annotations = newAnnotations.toImmutableList(),
                markers = newMarkers.toImmutableList(),
                cursor = snapshot.cursor.copy(position = newPoint),
                selection = WSelectionManager.collapseTo(newPoint),
                activeAnnotationId = null
            )
            return WMutationResult.Success(nextSnapshot, WMutationType.DELETE_RANGE, setOf(block.id), 0)
        } else {
            // Multi-block deletion
            val startBlock = blocks[startIdx]
            val endBlock = blocks[endIdx]
            if (startBlock !is WTextContainer || endBlock !is WTextContainer) 
                return WMutationResult.Failure("Invalid blocks", WMutationType.DELETE_RANGE)
            
            val remainingHead = startBlock.text.substring(0, normalized.anchor.offset)
            val remainingTail = endBlock.text.substring(normalized.focus.offset)
            
            val headSpans = WSpanManager.shiftSpansForDeletion(startBlock.spans, normalized.anchor.offset, startBlock.text.length)
            val tailSpans = WSpanManager.shiftSpansForDeletion(endBlock.spans, 0, normalized.focus.offset).map {
                it.copy(start = it.start + remainingHead.length, end = it.end + remainingHead.length)
            }

            val mergedBlock = startBlock.copyWithText(remainingHead + remainingTail, (headSpans + tailSpans).toImmutableList())
            
            var currentAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                snapshot.annotations,
                startBlock.id,
                normalized.anchor.offset,
                startBlock.text.length
            )
            
            currentAnnotations = WAnnotationManager.shiftAnnotationsForDeletion(
                currentAnnotations,
                endBlock.id,
                0,
                normalized.focus.offset
            ).map { annotation ->
                if (annotation.blockId == endBlock.id) {
                    annotation.copy(
                        blockId = startBlock.id,
                        startOffset = annotation.startOffset + remainingHead.length,
                        endOffset = annotation.endOffset + remainingHead.length,
                        updatedAt = System.currentTimeMillis()
                    )
                } else annotation
            }
            
            val middleBlockIds = blocks.subList(startIdx + 1, endIdx).map { it.id }.toSet()
            currentAnnotations = currentAnnotations.filter { it.blockId !in middleBlockIds }

            // Markers Multi-block deletion
            val currentMarkers = WMarkerManager.shiftMarkersForDeletion(
                snapshot.markers,
                startBlock.id,
                normalized.anchor.offset,
                startBlock.text.length,
                snapshot.document.blocks
            ).let { m1 ->
                WMarkerManager.shiftMarkersForDeletion(
                    m1,
                    endBlock.id,
                    0,
                    normalized.focus.offset,
                    snapshot.document.blocks
                )
            }.map { marker ->
                if (marker.startBlockId == endBlock.id) marker.copy(startBlockId = startBlock.id, startOffset = marker.startOffset + remainingHead.length)
                else marker
            }.map { marker ->
                if (marker.endBlockId == endBlock.id) marker.copy(endBlockId = startBlock.id, endOffset = marker.endOffset + remainingHead.length)
                else marker
            }.filter { it.startBlockId !in middleBlockIds && it.endBlockId !in middleBlockIds }

            blocks.subList(startIdx, endIdx + 1).clear()
            blocks.add(startIdx, mergedBlock)

            val newPoint = WSelectionPoint(mergedBlock.id, normalized.anchor.offset)
            val nextSnapshot = snapshot.copy(
                document = snapshot.document.copy(
                    blocks = blocks.toImmutableList(), 
                    revision = snapshot.document.revision + 1
                ),
                annotations = currentAnnotations.toImmutableList(),
                markers = currentMarkers.toImmutableList(),
                cursor = snapshot.cursor.copy(position = newPoint),
                selection = WSelectionManager.collapseTo(newPoint),
                activeAnnotationId = null
            )
            return WMutationResult.Success(nextSnapshot, WMutationType.DELETE_RANGE, setOf(startBlock.id, endBlock.id) + middleBlockIds, 0)
        }
    }

    fun splitBlock(snapshot: WEditorSnapshot): WMutationResult {
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == cursor.blockId }
        if (index == -1) return WMutationResult.Failure("Invalid cursor", WMutationType.SPLIT_BLOCK)
        
        val block = blocks[index]
        if (block !is WTextContainer) return WMutationResult.Failure("Invalid block", WMutationType.SPLIT_BLOCK)

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

        // Split Markers
        val newMarkers = WMarkerManager.splitMarkers(
            snapshot.markers,
            block.id,
            newTailId,
            cursor.offset
        )

        val newPoint = WSelectionPoint(newTailId, 0)
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(), 
                revision = snapshot.document.revision + 1
            ),
            annotations = newAnnotations.toImmutableList(),
            markers = newMarkers.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newPoint, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newPoint),
            activeAnnotationId = null
        )
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.SPLIT_BLOCK,
            affectedBlockIds = setOf(block.id, newTailId),
            affectedCharacterCount = 0
        )
    }

    fun extendDocument(snapshot: WEditorSnapshot, numLines: Int): WMutationResult {
        if (numLines <= 0) return WMutationResult.Failure("Invalid line count", WMutationType.EXTEND_DOCUMENT)
        
        val blocks = snapshot.document.blocks.toMutableList()
        val lastIdx = blocks.lastIndex
        if (lastIdx == -1) return WMutationResult.Failure("Empty document", WMutationType.EXTEND_DOCUMENT)
        
        val originalBlock = blocks[lastIdx]
        val lastBlock = originalBlock as? WTextContainer ?: return WMutationResult.Failure("Last block is not text", WMutationType.EXTEND_DOCUMENT)
        
        val newText = lastBlock.text + "\n".repeat(numLines)
        blocks[lastIdx] = lastBlock.copyWithText(newText, lastBlock.spans)
        
        val newCursor = WSelectionPoint(originalBlock.id, newText.length)
        val nextSnapshot = snapshot.copy(
            document = snapshot.document.copy(blocks = blocks.toImmutableList(), revision = snapshot.document.revision + 1),
            cursor = snapshot.cursor.copy(position = newCursor, blinkVisible = true),
            selection = WSelectionManager.collapseTo(newCursor)
        )
        
        return WMutationResult.Success(
            snapshot = nextSnapshot,
            mutationType = WMutationType.EXTEND_DOCUMENT,
            affectedBlockIds = setOf(originalBlock.id),
            affectedCharacterCount = numLines
        )
    }

    fun annotateRange(snapshot: WEditorSnapshot, content: String, range: WSelectionRange, anchorText: String): WMutationResult {
        if (content.isBlank()) return WMutationResult.Failure("Annotation content cannot be empty", WMutationType.ANNOTATE_RANGE)
        
        val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
        if (normalized.anchor.blockId != normalized.focus.blockId) {
            return WMutationResult.Failure("Annotations currently only support a single block", WMutationType.ANNOTATE_RANGE)
        }

        val block = snapshot.document.blocks.find { it.id == normalized.anchor.blockId } ?: return WMutationResult.Failure("Block not found", WMutationType.ANNOTATE_RANGE)
        if (block !is WTextContainer) return WMutationResult.Failure("Selected block is not a text container", WMutationType.ANNOTATE_RANGE)

        if (normalized.anchor.offset == normalized.focus.offset) {
            return WMutationResult.Failure("Cannot annotate an empty range", WMutationType.ANNOTATE_RANGE)
        }

        val annotation = ContextAnnotation(
            tabId = snapshot.document.id.toLongOrNull() ?: -1L,
            blockId = normalized.anchor.blockId,
            startOffset = normalized.anchor.offset,
            endOffset = normalized.focus.offset,
            anchorText = anchorText,
            content = content
        )
        val nextSnapshot = snapshot.copy(
            annotations = (snapshot.annotations + annotation).toImmutableList(),
            selection = WSelectionManager.collapseTo(normalized.focus),
            cursor = snapshot.cursor.copy(position = normalized.focus, isVisible = true),
            isCreatingAnnotation = false,
            pendingAnnotationRange = null,
            pendingAnnotationAnchorText = ""
        )
        return WMutationResult.Success(nextSnapshot, WMutationType.ANNOTATE_RANGE, setOf(normalized.anchor.blockId), 0)
    }

    fun updateAnnotation(snapshot: WEditorSnapshot, id: String, content: String): WMutationResult {
        if (content.isBlank()) return WMutationResult.Failure("Annotation content cannot be empty", WMutationType.UPDATE_ANNOTATION)
        
        val index = snapshot.annotations.indexOfFirst { it.id == id }
        if (index == -1) return WMutationResult.Failure("Annotation not found", WMutationType.UPDATE_ANNOTATION)

        val newAnnotations = snapshot.annotations.map {
            if (it.id == id) it.copy(content = content, updatedAt = System.currentTimeMillis())
            else it
        }
        val nextSnapshot = snapshot.copy(annotations = newAnnotations.toImmutableList())
        return WMutationResult.Success(nextSnapshot, WMutationType.UPDATE_ANNOTATION, emptySet(), 0)
    }

    fun deleteAnnotation(snapshot: WEditorSnapshot, id: String): WMutationResult {
        val index = snapshot.annotations.indexOfFirst { it.id == id }
        if (index == -1) return WMutationResult.Success(snapshot, WMutationType.DELETE_ANNOTATION, emptySet(), 0)

        val newAnnotations = snapshot.annotations.filter { it.id != id }
        val nextSnapshot = snapshot.copy(
            annotations = newAnnotations.toImmutableList(),
            activeAnnotationId = if (snapshot.activeAnnotationId == id) null else snapshot.activeAnnotationId
        )
        return WMutationResult.Success(nextSnapshot, WMutationType.DELETE_ANNOTATION, emptySet(), 0)
    }

    // --- Text Marker Methods ---

    fun createMarker(snapshot: WEditorSnapshot, label: String, range: WSelectionRange, anchorText: String): WMutationResult {
        if (label.isBlank()) return WMutationResult.Failure("Marker label cannot be empty", WMutationType.ANNOTATE_RANGE)
        
        val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
        
        // Prevent exact duplicates
        val exists = snapshot.markers.any { 
            it.startBlockId == normalized.anchor.blockId && it.startOffset == normalized.anchor.offset &&
            it.endBlockId == normalized.focus.blockId && it.endOffset == normalized.focus.offset 
        }
        if (exists) return WMutationResult.Failure("Marker already exists for this selection", WMutationType.ANNOTATE_RANGE)

        val marker = TextMarker(
            tabId = snapshot.document.id.toLongOrNull() ?: -1L,
            startBlockId = normalized.anchor.blockId,
            startOffset = normalized.anchor.offset,
            endBlockId = normalized.focus.blockId,
            endOffset = normalized.focus.offset,
            anchorText = anchorText,
            label = label
        )

        val nextSnapshot = snapshot.copy(
            markers = (snapshot.markers + marker).toImmutableList(),
            selection = WSelectionManager.collapseTo(normalized.focus),
            cursor = snapshot.cursor.copy(position = normalized.focus, isVisible = true)
        )
        return WMutationResult.Success(nextSnapshot, WMutationType.ANNOTATE_RANGE, emptySet(), 0)
    }

    fun updateMarkerLabel(snapshot: WEditorSnapshot, id: String, newLabel: String): WMutationResult {
        if (newLabel.isBlank()) return WMutationResult.Failure("Label cannot be empty", WMutationType.UPDATE_ANNOTATION)
        val newMarkers = snapshot.markers.map {
            if (it.id == id) it.copy(label = newLabel) else it
        }
        val nextSnapshot = snapshot.copy(markers = newMarkers.toImmutableList())
        return WMutationResult.Success(nextSnapshot, WMutationType.UPDATE_ANNOTATION, emptySet(), 0)
    }

    fun updateMarkerType(snapshot: WEditorSnapshot, id: String, newType: com.tv.wnotes.data.local.entity.MarkerType): WMutationResult {
        val newMarkers = snapshot.markers.map {
            if (it.id == id) it.copy(type = newType) else it
        }
        val nextSnapshot = snapshot.copy(markers = newMarkers.toImmutableList())
        return WMutationResult.Success(nextSnapshot, WMutationType.UPDATE_ANNOTATION, emptySet(), 0)
    }

    fun deleteMarker(snapshot: WEditorSnapshot, id: String): WMutationResult {
        val newMarkers = snapshot.markers.filter { it.id != id }
        val nextSnapshot = snapshot.copy(markers = newMarkers.toImmutableList())
        return WMutationResult.Success(nextSnapshot, WMutationType.DELETE_ANNOTATION, emptySet(), 0)
    }
}

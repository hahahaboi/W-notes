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
        virtualLineCount: Int = 0,
        isCopy: Boolean = false
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
            
            if (capturedBlocks.isNotEmpty() && (capturedBlocks.last() is WTextBlock) && fragment.isNotEmpty() && (fragment[0] is WTextBlock)) {
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

        // 3. Delete source ranges in reverse document order (Skip if copying)
        var currentSnapshot = snapshot
        var adjustedTarget = target

        if (!isCopy) {
            val sortedSources = WSelectionManager.normalizeSelectionOrder(sources, blocks).reversed()

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

    fun insertRichTextAtPoint(
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
        val selectionStart = WSelectionPoint(point.blockId, point.offset)
        val newSelectionRange = WSelectionRange(selectionStart, newCursor)

        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(), 
                revision = snapshot.document.revision + 1
            ),
            annotations = finalAnnotations.toImmutableList(),
            markers = shiftedMarkers.toImmutableList(),
            cursor = snapshot.cursor.copy(position = newCursor, blinkVisible = true),
            selection = WSelectionState(
                ranges = listOf(newSelectionRange),
                activeRangeId = newSelectionRange.id,
                isActive = true,
                depth = WSelectionDepth.CHARACTER
            ),
            activeAnnotationId = null
        )
        
        return WMutationResult.Success(snapshot = newSnapshot, mutationType = WMutationType.MOVE_RANGE, affectedBlockIds = setOf(point.blockId), affectedCharacterCount = text.length)
    }

    fun applyFontSize(snapshot: WEditorSnapshot, size: Int): WMutationResult {
        if (!snapshot.selection.isActive || snapshot.selection.ranges.isEmpty() || WSelectionManager.isCollapsed(snapshot.selection)) {
            // Update default font size for the note
            val nextSnapshot = snapshot.copy(
                settings = snapshot.settings.copy(fontSize = size)
            )
            return WMutationResult.Success(
                snapshot = nextSnapshot,
                mutationType = WMutationType.FORMAT_SELECTION,
                affectedBlockIds = emptySet(),
                affectedCharacterCount = 0
            )
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
                        // Remove existing FontSize spans in this range first
                        val cleanedSpans = block.spans.filterNot { 
                            it.type == SpanType.FontSize && 
                            it.start < end && it.end > start 
                        }
                        
                        val newSpan = WTextSpan(start, end, SpanType.FontSize, mapOf("size" to size.toString()))
                        val newSpans = (cleanedSpans + newSpan).toImmutableList()
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

    fun adjustFontSize(snapshot: WEditorSnapshot, delta: Int): WMutationResult {
        if (!snapshot.selection.isActive || snapshot.selection.ranges.isEmpty()) {
            val newSize = (snapshot.settings.fontSize + delta).coerceIn(6, 64)
            val nextSnapshot = snapshot.copy(
                settings = snapshot.settings.copy(fontSize = newSize)
            )
            return WMutationResult.Success(
                snapshot = nextSnapshot,
                mutationType = WMutationType.FORMAT_SELECTION,
                affectedBlockIds = emptySet(),
                affectedCharacterCount = 0
            )
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
                        // We need to find all FontSize spans that intersect with this range
                        // and adjust them. If parts of the range have no FontSize span,
                        // we use the default from settings.
                        
                        val rangeSpans = block.spans.filter { it.type == SpanType.FontSize && it.start < end && it.end > start }
                        val newSpans = block.spans.toMutableList()
                        
                        if (rangeSpans.isEmpty()) {
                            // No explicit font size spans, apply one based on settings
                            val baseSize = snapshot.settings.fontSize
                            val newSize = (baseSize + delta).coerceIn(6, 64)
                            newSpans.add(WTextSpan(start, end, SpanType.FontSize, mapOf("size" to newSize.toString())))
                        } else {
                            // Adjust existing spans and fill gaps
                            var lastProcessed = start
                            val sortedSpans = rangeSpans.sortedBy { it.start }
                            
                            val spansToAdd = mutableListOf<WTextSpan>()
                            val spansToRemove = mutableSetOf<WTextSpan>()
                            
                            sortedSpans.forEach { span ->
                                // Gap before this span
                                if (span.start > lastProcessed) {
                                    val baseSize = snapshot.settings.fontSize
                                    val newSize = (baseSize + delta).coerceIn(6, 64)
                                    spansToAdd.add(WTextSpan(lastProcessed, span.start, SpanType.FontSize, mapOf("size" to newSize.toString())))
                                }
                                
                                // Adjust this span (might need to split if it extends beyond range)
                                val actualStart = maxOf(span.start, start)
                                val actualEnd = minOf(span.end, end)
                                
                                val currentSize = span.attributes["size"]?.toIntOrNull() ?: snapshot.settings.fontSize
                                val newSize = (currentSize + delta).coerceIn(6, 64)
                                
                                spansToRemove.add(span)
                                spansToAdd.add(WTextSpan(actualStart, actualEnd, SpanType.FontSize, mapOf("size" to newSize.toString())))
                                
                                // Re-add fragments of the original span if it was clipped
                                if (span.start < start) {
                                    spansToAdd.add(span.copy(end = start))
                                }
                                if (span.end > end) {
                                    spansToAdd.add(span.copy(start = end))
                                }
                                
                                lastProcessed = actualEnd
                            }
                            
                            // Gap after last span
                            if (lastProcessed < end) {
                                val baseSize = snapshot.settings.fontSize
                                val newSize = (baseSize + delta).coerceIn(6, 64)
                                spansToAdd.add(WTextSpan(lastProcessed, end, SpanType.FontSize, mapOf("size" to newSize.toString())))
                            }
                            
                            newSpans.removeAll(spansToRemove)
                            newSpans.addAll(spansToAdd)
                        }
                        
                        blocks[i] = block.copyWithText(block.text, newSpans.toImmutableList())
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
        val blocks = snapshot.document.blocks
        val index = blocks.indexOfFirst { it.id == cursor.blockId }
        if (index == -1) return WMutationResult.Failure("Invalid cursor", WMutationType.DELETE_RANGE)
        val block = blocks[index]

        // Backspace on empty list item exits list
        if (cursor.offset == 0 && block is WTextContainer && block.text.isEmpty()) {
            val isListItem = when (block) {
                is WChecklistBlock -> true
                is WTextBlock -> block.type == TextBlockType.Bullet || block.type == TextBlockType.Numbered
                else -> false
            }
            if (isListItem) {
                return toggleBlockType(snapshot, if (block is WChecklistBlock) TextBlockType.Checklist else (block as WTextBlock).type)
            }
        }

        if (cursor.offset > 0) {
            val mutableBlocks = blocks.toMutableList()
            val textBlock = block as WTextContainer

            val newText = StringBuilder(textBlock.text).deleteAt(cursor.offset - 1).toString()
            val newSpans = WSpanManager.shiftSpansForDeletion(textBlock.spans, cursor.offset - 1, cursor.offset)
            
            mutableBlocks[index] = textBlock.copyWithText(newText, newSpans)

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
                    blocks = mutableBlocks.toImmutableList(), 
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

    /**
     * Toggles an inline style. With a selection it styles the selection (or removes the style if the
     * whole selection already has it); with just a cursor it does the same for the word under the cursor.
     */
    fun applySpanToSelection(snapshot: WEditorSnapshot, spanType: SpanType): WMutationResult {
        val blocks = snapshot.document.blocks
        val segments = selectedSegments(snapshot)
            ?: wordAtCursor(snapshot)?.let { listOf(it) }
            ?: return WMutationResult.Failure("No selection", WMutationType.FORMAT_SELECTION)

        val alreadyStyled = segments.all { (blockId, start, end) ->
            val block = blocks.find { it.id == blockId } as? WTextContainer ?: return@all true
            isFullyCovered(block.spans, spanType, start, end)
        }

        val newBlocks = blocks.toMutableList()
        val affectedIds = mutableSetOf<String>()
        segments.forEach { (blockId, start, end) ->
            val index = newBlocks.indexOfFirst { it.id == blockId }
            val block = newBlocks.getOrNull(index) as? WTextContainer ?: return@forEach
            val remaining = removeSpanRange(block.spans, spanType, start, end)
            val newSpans = if (alreadyStyled) remaining else remaining + WTextSpan(start, end, spanType)
            newBlocks[index] = block.copyWithText(block.text, newSpans.toImmutableList())
            affectedIds.add(blockId)
        }

        return WMutationResult.Success(
            snapshot = snapshot.copy(
                document = snapshot.document.copy(
                    blocks = newBlocks.toImmutableList(),
                    revision = snapshot.document.revision + 1
                )
            ),
            mutationType = WMutationType.FORMAT_SELECTION,
            affectedBlockIds = affectedIds,
            affectedCharacterCount = 0
        )
    }

    /** Non-empty (blockId, start, end) pieces of the active selection, or null if nothing is selected. */
    private fun selectedSegments(snapshot: WEditorSnapshot): List<Triple<String, Int, Int>>? {
        val selection = snapshot.selection
        if (!selection.isActive || selection.ranges.isEmpty() || WSelectionManager.isCollapsed(selection)) return null
        val blocks = snapshot.document.blocks
        val segments = mutableListOf<Triple<String, Int, Int>>()
        selection.ranges.forEach { range ->
            val normalized = WSelectionManager.normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            if (startIdx == -1 || endIdx == -1) return@forEach
            for (i in startIdx..endIdx) {
                val block = blocks[i] as? WTextContainer ?: continue
                val start = if (i == startIdx) normalized.anchor.offset else 0
                val end = if (i == endIdx) normalized.focus.offset else block.text.length
                if (start < end) segments.add(Triple(blocks[i].id, start, end))
            }
        }
        return segments.ifEmpty { null }
    }

    private fun wordAtCursor(snapshot: WEditorSnapshot): Triple<String, Int, Int>? {
        val point = snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == point.blockId } as? WTextContainer ?: return null
        val text = block.text
        val offset = point.offset.coerceIn(0, text.length)
        var start = offset
        var end = offset
        while (start > 0 && text[start - 1].isLetterOrDigit()) start--
        while (end < text.length && text[end].isLetterOrDigit()) end++
        return if (start < end) Triple(point.blockId, start, end) else null
    }

    private fun isFullyCovered(spans: List<WTextSpan>, type: SpanType, start: Int, end: Int): Boolean {
        var covered = start
        spans.filter { it.type == type }.sortedBy { it.start }.forEach { span ->
            if (span.start <= covered && span.end > covered) covered = span.end
        }
        return covered >= end
    }

    /** Removes [type] from [start, end), splitting spans that stick out on either side. */
    private fun removeSpanRange(spans: List<WTextSpan>, type: SpanType, start: Int, end: Int): List<WTextSpan> =
        spans.flatMap { span ->
            if (span.type != type || span.end <= start || span.start >= end) {
                listOf(span)
            } else {
                listOfNotNull(
                    span.copy(end = start).takeIf { it.start < it.end },
                    span.copy(start = end).takeIf { it.start < it.end }
                )
            }
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

    fun toggleBlockType(snapshot: WEditorSnapshot, targetType: TextBlockType): WMutationResult {
        val selection = snapshot.selection
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val affectedIds = mutableSetOf<String>()

        val rangeToToggle = if (selection.isActive && !WSelectionManager.isCollapsed(selection)) {
            val normalized = WSelectionManager.normalize(selection.ranges.first(), blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            startIdx..endIdx
        } else {
            val idx = blocks.indexOfFirst { it.id == cursor.blockId }
            if (idx != -1) idx..idx else return WMutationResult.Failure("No block found", WMutationType.FORMAT_SELECTION)
        }

        rangeToToggle.forEach { i ->
            val block = blocks[i]
            val newBlock = when (block) {
                is WTextBlock -> {
                    when (targetType) {
                        TextBlockType.Checklist -> {
                            WChecklistBlock(
                                id = block.id,
                                text = block.text,
                                spans = block.spans,
                                style = block.style,
                                parentBlockId = block.parentBlockId
                            )
                        }
                        else -> {
                            block.copy(type = if (block.type == targetType) TextBlockType.Body else targetType)
                        }
                    }
                }
                is WChecklistBlock -> {
                    if (targetType == TextBlockType.Checklist) {
                        WTextBlock(
                            id = block.id,
                            text = block.text,
                            spans = block.spans,
                            style = block.style,
                            parentBlockId = block.parentBlockId,
                            type = TextBlockType.Body
                        )
                    } else {
                        WTextBlock(
                            id = block.id,
                            text = block.text,
                            spans = block.spans,
                            style = block.style,
                            parentBlockId = block.parentBlockId,
                            type = targetType
                        )
                    }
                }
                else -> block
            }
            blocks[i] = newBlock
            affectedIds.add(block.id)
        }

        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(),
                revision = snapshot.document.revision + 1
            )
        )

        return WMutationResult.Success(
            snapshot = newSnapshot,
            mutationType = WMutationType.FORMAT_SELECTION,
            affectedBlockIds = affectedIds,
            affectedCharacterCount = 0
        )
    }

    fun adjustIndent(snapshot: WEditorSnapshot, delta: Int): WMutationResult {
        val selection = snapshot.selection
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val affectedIds = mutableSetOf<String>()

        val rangeToAdjust = if (selection.isActive && !WSelectionManager.isCollapsed(selection)) {
            val normalized = WSelectionManager.normalize(selection.ranges.first(), blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            startIdx..endIdx
        } else {
            val idx = blocks.indexOfFirst { it.id == cursor.blockId }
            if (idx != -1) idx..idx else return WMutationResult.Failure("No block found", WMutationType.FORMAT_SELECTION)
        }

        rangeToAdjust.forEach { i ->
            val block = blocks[i]
            val newStyle = block.style.copy(indentationLevel = (block.style.indentationLevel + delta).coerceAtLeast(0))
            val newBlock = when (block) {
                is WTextBlock -> block.copy(style = newStyle)
                is WChecklistBlock -> block.copy(style = newStyle)
                is WMediaBlock -> block.copy(style = newStyle)
                is WDrawingBlock -> block.copy(style = newStyle)
                is WTextBoxBlock -> block.copy(style = newStyle)
            }
            blocks[i] = newBlock
            affectedIds.add(block.id)
        }

        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(),
                revision = snapshot.document.revision + 1
            )
        )

        return WMutationResult.Success(newSnapshot, WMutationType.FORMAT_SELECTION, affectedIds, 0)
    }

    fun setAlignment(snapshot: WEditorSnapshot, alignment: String): WMutationResult {
        val selection = snapshot.selection
        val cursor = snapshot.cursor.position
        val blocks = snapshot.document.blocks.toMutableList()
        val affectedIds = mutableSetOf<String>()

        val rangeToAdjust = if (selection.isActive && !WSelectionManager.isCollapsed(selection)) {
            val normalized = WSelectionManager.normalize(selection.ranges.first(), blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            startIdx..endIdx
        } else {
            val idx = blocks.indexOfFirst { it.id == cursor.blockId }
            if (idx != -1) idx..idx else return WMutationResult.Failure("No block found", WMutationType.FORMAT_SELECTION)
        }

        rangeToAdjust.forEach { i ->
            val block = blocks[i]
            val newStyle = block.style.copy(alignment = alignment)
            val newBlock = when (block) {
                is WTextBlock -> block.copy(style = newStyle)
                is WChecklistBlock -> block.copy(style = newStyle)
                is WMediaBlock -> block.copy(style = newStyle)
                is WDrawingBlock -> block.copy(style = newStyle)
                is WTextBoxBlock -> block.copy(style = newStyle)
            }
            blocks[i] = newBlock
            affectedIds.add(block.id)
        }

        val newSnapshot = snapshot.copy(
            document = snapshot.document.copy(
                blocks = blocks.toImmutableList(),
                revision = snapshot.document.revision + 1
            )
        )

        return WMutationResult.Success(newSnapshot, WMutationType.FORMAT_SELECTION, affectedIds, 0)
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

        // If block is a list item and is empty, Enter should exit the list
        val isListItem = when (block) {
            is WChecklistBlock -> true
            is WTextBlock -> block.type == TextBlockType.Bullet || block.type == TextBlockType.Numbered
            else -> false
        }

        if (isListItem && block.text.isEmpty()) {
            return toggleBlockType(snapshot, if (block is WChecklistBlock) TextBlockType.Checklist else (block as WTextBlock).type)
        }

        val headText = block.text.substring(0, cursor.offset)
        val tailText = block.text.substring(cursor.offset)
        
        val (headSpans, tailSpans) = WSpanManager.splitSpans(block.spans, cursor.offset)

        val updatedHead = block.copyWithText(headText, headSpans)
        
        val newTailId = UUID.randomUUID().toString()
        val newTail = when (block) {
            is WChecklistBlock -> WChecklistBlock(
                id = newTailId,
                text = tailText,
                spans = tailSpans,
                style = block.style,
                parentBlockId = block.parentBlockId,
                isChecked = false
            )
            is WTextBlock -> WTextBlock(
                id = newTailId,
                text = tailText,
                spans = tailSpans,
                type = block.type,
                style = block.style,
                parentBlockId = block.parentBlockId
            )
            else -> WTextBlock(id = newTailId, text = tailText, spans = tailSpans)
        }

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

    fun applyFont(snapshot: WEditorSnapshot, fontFamily: String): WMutationResult {
        if (!snapshot.selection.isActive || snapshot.selection.ranges.isEmpty() || WSelectionManager.isCollapsed(snapshot.selection)) {
            // Nothing selected: change the default font for the whole note
            return WMutationResult.Success(
                snapshot = snapshot.copy(settings = snapshot.settings.copy(fontFamily = fontFamily)),
                mutationType = WMutationType.FORMAT_SELECTION,
                affectedBlockIds = emptySet(),
                affectedCharacterCount = 0
            )
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
                        val cleanedSpans = block.spans.filterNot { 
                            it.type == SpanType.FontFamily && it.start < end && it.end > start 
                        }
                        val newSpan = WTextSpan(start, end, SpanType.FontFamily, mapOf("family" to fontFamily))
                        val newSpans = (cleanedSpans + newSpan).toImmutableList()
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

        return WMutationResult.Success(currentSnapshot, WMutationType.FORMAT_SELECTION, affectedIds, 0)
    }

    fun addTextBox(snapshot: WEditorSnapshot): WMutationResult {
        val newId = UUID.randomUUID().toString()
        val newBlock = WTextBoxBlock(id = newId, style = WBlockStyle(isFloating = false))
        val result = insertBlock(snapshot, newBlock)
        if (result !is WMutationResult.Success) return result
        // Put the cursor inside the new box so the user can type straight away.
        val point = WSelectionPoint(newId, 0)
        return result.copy(
            snapshot = result.snapshot.copy(
                cursor = WCursorState(position = point),
                selection = WSelectionManager.collapseTo(point)
            )
        )
    }

    /** Adds an empty drawing area after the cursor's block, unless one is already there. */
    fun ensureDrawingBlock(snapshot: WEditorSnapshot): WMutationResult {
        val blocks = snapshot.document.blocks
        val cursorIdx = blocks.indexOfFirst { it.id == snapshot.cursor.position.blockId }
        val nearby = listOfNotNull(blocks.getOrNull(cursorIdx), blocks.getOrNull(cursorIdx + 1))
        if (nearby.any { it is WDrawingBlock }) {
            return WMutationResult.Failure("Drawing area already present", WMutationType.INSERT_BLOCK)
        }
        return insertBlock(snapshot, WDrawingBlock(id = UUID.randomUUID().toString()))
    }

    fun updateTitle(snapshot: WEditorSnapshot, newTitle: String, cursorOffset: Int? = null): WMutationResult {
        val oldTitle = snapshot.document.metadata.title
        if (oldTitle == newTitle && (cursorOffset == null || snapshot.cursor.position.offset == cursorOffset)) {
            return WMutationResult.Success(snapshot, WMutationType.UPDATE_BLOCK, emptySet(), 0)
        }
        
        val updatedDocument = snapshot.document.copy(
            metadata = snapshot.document.metadata.copy(title = newTitle),
            revision = snapshot.document.revision + 1
        )
        
        // verifyAndRelocate handles drift by searching for anchor text
        val titleBlock = WTextBlock(id = "W_NOTE_TITLE", text = newTitle)
        val nextAnnotations = WAnnotationManager.verifyAndRelocate(snapshot.annotations, listOf(titleBlock))
        
        var nextCursor = snapshot.cursor
        if (cursorOffset != null) {
            nextCursor = nextCursor.copy(
                position = WSelectionPoint("W_NOTE_TITLE", cursorOffset),
                blinkVisible = true
            )
        }
        
        val nextSnapshot = snapshot.copy(
            document = updatedDocument,
            annotations = nextAnnotations.toImmutableList(),
            cursor = nextCursor,
            selection = if (cursorOffset != null) WSelectionManager.collapseTo(nextCursor.position) else snapshot.selection
        )
        
        return WMutationResult.Success(nextSnapshot, WMutationType.UPDATE_BLOCK, setOf("W_NOTE_TITLE"), 0)
    }

    fun annotateRange(snapshot: WEditorSnapshot, content: String, range: WSelectionRange, anchorText: String): WMutationResult {
        if (content.isBlank()) return WMutationResult.Failure("Annotation content cannot be empty", WMutationType.ANNOTATE_RANGE)
        
        val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
        if (normalized.anchor.blockId != normalized.focus.blockId) {
            return WMutationResult.Failure("Annotations currently only support a single block", WMutationType.ANNOTATE_RANGE)
        }

        if (normalized.anchor.blockId != "W_NOTE_TITLE") {
            val block = snapshot.document.blocks.find { it.id == normalized.anchor.blockId } ?: return WMutationResult.Failure("Block not found", WMutationType.ANNOTATE_RANGE)
            if (block !is WTextContainer) return WMutationResult.Failure("Selected block is not a text container", WMutationType.ANNOTATE_RANGE)
        }

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

    // --- Tab Tie Methods ---

    fun createTie(snapshot: WEditorSnapshot, destinationTabId: Long, range: WSelectionRange, anchorText: String): WMutationResult {
        val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
        val tie = com.tv.wnotes.ui.editor.engine.state.TabTie(
            sourceTabId = snapshot.document.id.toLongOrNull() ?: -1L,
            destinationTabId = destinationTabId,
            sourceBlockId = normalized.anchor.blockId,
            sourceStartOffset = normalized.anchor.offset,
            sourceEndOffset = normalized.focus.offset,
            destinationBlockId = "", 
            destinationStartOffset = 0,
            anchorText = anchorText
        )
        val nextSnapshot = snapshot.copy(
            ties = (snapshot.ties + tie).toImmutableList(),
            isTyingTabs = false,
            selection = WSelectionManager.collapseTo(normalized.focus),
            cursor = snapshot.cursor.copy(position = normalized.focus, isVisible = true)
        )
        return WMutationResult.Success(nextSnapshot, WMutationType.ANNOTATE_RANGE, emptySet(), 0)
    }

}

package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Pure business logic for managing text selection in WEditor.
 * Handles single/multi-block selection, normalization, and semantic snapping.
 */
object WSelectionManager {

    /**
     * Collapses the selection to a single point (cursor).
     */
    fun collapseTo(point: WSelectionPoint): WSelectionState {
        return WSelectionState(
            ranges = listOf(WSelectionRange(point, point)),
            isActive = false,
            depth = WSelectionDepth.NONE
        )
    }

    /**
     * Normalizes a range ensuring anchor offset <= focus offset if in the same block,
     * or anchor block comes before focus block in the document.
     */
    fun normalize(range: WSelectionRange, blocks: List<WBlock>): WSelectionRange {
        val anchorIndex = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val focusIndex = blocks.indexOfFirst { it.id == range.focus.blockId }

        return when {
            anchorIndex < focusIndex -> range
            anchorIndex > focusIndex -> WSelectionRange(range.focus, range.anchor, range.id)
            else -> {
                // Same block
                if (range.anchor.offset <= range.focus.offset) range
                else WSelectionRange(range.focus, range.anchor, range.id)
            }
        }
    }

    /**
     * Selects an entire word at the given point.
     */
    fun selectWord(point: WSelectionPoint, blocks: List<WBlock>): WSelectionState {
        val block = blocks.find { it.id == point.blockId }
        if (block !is WTextContainer) return collapseTo(point)
        val text = block.text
        if (text.isEmpty()) return collapseTo(point)

        val offset = point.offset.coerceIn(0, text.length)
        
        var start = offset
        while (start > 0 && !isSeparator(text[start - 1])) start--
        
        var end = offset
        while (end < text.length && !isSeparator(text[end])) end++

        // If we clicked exactly on a separator, we might want to just select the separator itself.
        val finalEnd = if (start == end && end < text.length) end + 1 else end

        val range = WSelectionRange(
            WSelectionPoint(point.blockId, start),
            WSelectionPoint(point.blockId, finalEnd)
        )
        return WSelectionState(
            ranges = listOf(range),
            activeRangeId = range.id,
            isActive = true,
            depth = WSelectionDepth.WORD
        )
    }

    /**
     * Selects an entire paragraph (block) at the given point.
     */
    fun selectParagraph(point: WSelectionPoint): WSelectionState {
        val range = WSelectionRange(
            WSelectionPoint(point.blockId, 0),
            WSelectionPoint(point.blockId, Int.MAX_VALUE)
        )
        return WSelectionState(
            ranges = listOf(range),
            activeRangeId = range.id,
            isActive = true,
            depth = WSelectionDepth.PARAGRAPH
        )
    }

    /**
     * Selects the entire document.
     */
    fun selectAll(blocks: List<WBlock>): WSelectionState {
        if (blocks.isEmpty()) return WSelectionState()
        
        val firstBlock = blocks.first()
        val lastBlock = blocks.last()
        
        val range = WSelectionRange(
            WSelectionPoint(firstBlock.id, 0),
            WSelectionPoint(lastBlock.id, Int.MAX_VALUE)
        )
        return WSelectionState(
            ranges = listOf(range),
            activeRangeId = range.id,
            isActive = true,
            depth = WSelectionDepth.CHARACTER
        )
    }

    /**
     * Extends the current selection to a new point.
     * If the mode is WORD, it snaps the focus to the nearest word boundary.
     */
    fun extendSelection(currentState: WSelectionState, newFocus: WSelectionPoint, blocks: List<WBlock>): WSelectionState {
        if (currentState.ranges.isEmpty()) return collapseTo(newFocus)
        
        val lastRange = currentState.ranges.last()
        
        val snappedFocus = if (currentState.depth == WSelectionDepth.WORD) {
            val range = getWordBoundaries(newFocus, blocks)
            // If dragging forward, snap to word end. If backward, snap to word start.
            // We compare normalized positions to determine direction relative to anchor.
            val normalizedDir = comparePoints(newFocus, lastRange.anchor, blocks)
            if (normalizedDir >= 0) range.focus else range.anchor
        } else {
            newFocus
        }

        val newRanges = currentState.ranges.toMutableList()
        newRanges[newRanges.size - 1] = lastRange.copy(focus = snappedFocus)
        
        return currentState.copy(
            ranges = newRanges, 
            isActive = true,
            depth = if (currentState.depth == WSelectionDepth.NONE) WSelectionDepth.CHARACTER else currentState.depth
        )
    }

    /**
     * Finds the start and end of the word at the given point.
     */
    fun getWordBoundaries(point: WSelectionPoint, blocks: List<WBlock>): WSelectionRange {
        val block = blocks.find { it.id == point.blockId }
        if (block !is WTextContainer) return WSelectionRange(point, point)
        val text = block.text
        if (text.isEmpty()) return WSelectionRange(point, point)

        val offset = point.offset.coerceIn(0, text.length)
        
        var start = offset
        while (start > 0 && !isSeparator(text[start - 1])) start--
        
        var end = offset
        while (end < text.length && !isSeparator(text[end])) end++

        // Handle case where we click on a separator
        if (start == end && end < text.length) end++

        return WSelectionRange(
            WSelectionPoint(point.blockId, start),
            WSelectionPoint(point.blockId, end)
        )
    }

    /**
     * Compares two points in the document.
     * Returns > 0 if p1 > p2, < 0 if p1 < p2, 0 if equal.
     */
    private fun comparePoints(p1: WSelectionPoint, p2: WSelectionPoint, blocks: List<WBlock>): Int {
        if (p1.blockId == p2.blockId) return p1.offset - p2.offset
        
        val idx1 = blocks.indexOfFirst { it.id == p1.blockId }
        val idx2 = blocks.indexOfFirst { it.id == p2.blockId }
        return idx1 - idx2
    }

    /**
     * Validates and clamps selection offsets against actual block content.
     */
    fun validate(state: WSelectionState, blocks: List<WBlock>): WSelectionState {
        val validatedRanges = state.ranges.map { range ->
            val anchor = clampPoint(range.anchor, blocks)
            val focus = clampPoint(range.focus, blocks)
            WSelectionRange(anchor, focus)
        }
        return state.copy(ranges = validatedRanges)
    }

    private fun clampPoint(point: WSelectionPoint, blocks: List<WBlock>): WSelectionPoint {
        val block = blocks.find { it.id == point.blockId } ?: return point
        val maxOffset = if (block is WTextContainer) block.text.length else 0
        return point.copy(offset = point.offset.coerceIn(0, maxOffset))
    }

    private fun isSeparator(c: Char): Boolean {
        return c.isWhitespace() || !c.isLetterOrDigit()
    }

    // Queries

    fun isCollapsed(state: WSelectionState): Boolean {
        if (state.ranges.size != 1) return false
        val range = state.ranges[0]
        return range.anchor == range.focus
    }

    fun contains(state: WSelectionState, blockId: String, offset: Int, blocks: List<WBlock>): Boolean {
        return state.ranges.any { range ->
            val normalized = normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            val currentIdx = blocks.indexOfFirst { it.id == blockId }

            when {
                currentIdx < startIdx || currentIdx > endIdx -> false
                currentIdx > startIdx && currentIdx < endIdx -> true
                startIdx == endIdx -> {
                    // Selection is entirely within one block
                    offset >= normalized.anchor.offset && offset <= normalized.focus.offset
                }
                currentIdx == startIdx -> offset >= normalized.anchor.offset
                currentIdx == endIdx -> offset <= normalized.focus.offset
                else -> false
            }
        }
    }

    /**
     * Checks if a point is strictly inside the selection (excluding boundaries).
     * Useful for drag-and-drop to allow dropping at the start or end of the selection.
     */
    fun containsExclusive(state: WSelectionState, blockId: String, offset: Int, blocks: List<WBlock>): Boolean {
        return state.ranges.any { range ->
            val normalized = normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            val currentIdx = blocks.indexOfFirst { it.id == blockId }

            when {
                currentIdx < startIdx || currentIdx > endIdx -> false
                currentIdx > startIdx && currentIdx < endIdx -> true
                startIdx == endIdx -> {
                    // Selection is entirely within one block
                    offset > normalized.anchor.offset && offset < normalized.focus.offset
                }
                currentIdx == startIdx -> offset > normalized.anchor.offset
                currentIdx == endIdx -> offset < normalized.focus.offset
                else -> false
            }
        }
    }

    fun containsAnyExclusive(ranges: List<WSelectionRange>, blockId: String, offset: Int, blocks: List<WBlock>): Boolean {
        return ranges.any { containsExclusive(WSelectionState(ranges = listOf(it), isActive = true), blockId, offset, blocks) }
    }

    fun intersectsBlock(state: WSelectionState, blockId: String, blocks: List<WBlock>): Boolean {
        return state.ranges.any { range ->
            val normalized = normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            val currentIdx = blocks.indexOfFirst { it.id == blockId }
            currentIdx in startIdx..endIdx
        }
    }

    fun isSingleBlock(state: WSelectionState): Boolean {
        if (state.ranges.size != 1) return false
        val range = state.ranges[0]
        return range.anchor.blockId == range.focus.blockId
    }

    /**
     * Implementation for Stage XX
     */
    fun normalizeSelectionOrder(ranges: List<WSelectionRange>, blocks: List<WBlock>): List<WSelectionRange> {
        return ranges.sortedWith { r1, r2 ->
            comparePoints(normalize(r1, blocks).anchor, normalize(r2, blocks).anchor, blocks)
        }
    }

    fun mergeOverlappingSelections(ranges: List<WSelectionRange>, blocks: List<WBlock>): List<WSelectionRange> {
        if (ranges.size <= 1) return ranges
        
        val result = mutableListOf<WSelectionRange>()
        
        for (range in ranges) {
            val normalizedNew = normalize(range, blocks)
            var merged = false
            
            for (i in result.indices) {
                val existing = result[i]
                val normalizedExisting = normalize(existing, blocks)
                
                // Check for overlap or adjacency
                // Two ranges overlap if:
                // next.anchor is before or at current.focus AND current.anchor is before or at next.focus
                val overlap = comparePoints(normalizedNew.anchor, normalizedExisting.focus, blocks) <= 0 &&
                             comparePoints(normalizedExisting.anchor, normalizedNew.focus, blocks) <= 0
                
                if (overlap) {
                    val newAnchor = if (comparePoints(normalizedNew.anchor, normalizedExisting.anchor, blocks) < 0)
                        normalizedNew.anchor else normalizedExisting.anchor
                    val newFocus = if (comparePoints(normalizedNew.focus, normalizedExisting.focus, blocks) > 0)
                        normalizedNew.focus else normalizedExisting.focus
                    
                    result[i] = WSelectionRange(newAnchor, newFocus, existing.id)
                    merged = true
                    break
                }
            }
            
            if (!merged) {
                result.add(range)
            }
        }
        
        return result
    }

    fun selectionAtPosition(state: WSelectionState, blockId: String, offset: Int, blocks: List<WBlock>): WSelectionRange? {
        if (!state.isActive) return null // Important: Don't intercept taps if selection isn't active (Stage XX fix)
        return state.ranges.find { range ->
            val normalized = normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            val currentIdx = blocks.indexOfFirst { it.id == blockId }

            when {
                currentIdx < startIdx || currentIdx > endIdx -> false
                currentIdx > startIdx && currentIdx < endIdx -> true
                startIdx == endIdx -> offset >= normalized.anchor.offset && offset <= normalized.focus.offset
                currentIdx == startIdx -> offset >= normalized.anchor.offset
                currentIdx == endIdx -> offset <= normalized.focus.offset
                else -> false
            }
        }
    }

    /**
     * Extracts plain text from all selected ranges, preserving newlines between blocks.
     * Hardened to handle multi-block, multi-line, and overlapping selections.
     */
    fun getSelectedText(state: WSelectionState, blocks: List<WBlock>): String {
        if (!state.isActive || state.ranges.isEmpty()) return ""
        
        // 1. Normalize and merge to avoid double-extracting text from overlapping ranges
        val mergedRanges = mergeOverlappingSelections(state.ranges, blocks)
        val sortedRanges = normalizeSelectionOrder(mergedRanges, blocks)
        
        val sb = StringBuilder()

        sortedRanges.forEachIndexed { rangeIndex, range ->
            val normalized = normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }

            if (startIdx != -1 && endIdx != -1) {
                for (i in startIdx..endIdx) {
                    val block = blocks[i]
                    if (block !is WTextContainer) continue

                    // Calculate offsets for this specific block
                    val textLen = block.text.length
                    val start = if (i == startIdx) normalized.anchor.offset.coerceIn(0, textLen) else 0
                    val end = if (i == endIdx) normalized.focus.offset.coerceIn(0, textLen) else textLen
                    
                    if (start < end) {
                        val text = block.text.substring(start, end)
                        sb.append(text)
                    }
                    
                    // Add newline if we are moving to another block within the SAME range
                    if (i < endIdx) {
                        sb.append("\n")
                    }
                }
            }
            
            // Join disjoint ranges with double newline for logical separation
            if (rangeIndex < sortedRanges.size - 1) {
                sb.append("\n\n")
            }
        }
        
        return sb.toString()
    }

    /**
     * Generates a concise title from raw text, adhering to UI length constraints.
     */
    fun generateTabTitle(text: String, maxLength: Int = 25): String {
        val singleLine = text.replace("\n", " ").replace("\r", "").trim()
        if (singleLine.isEmpty()) return "New Tab"
        if (singleLine.length <= maxLength) return singleLine
        
        return singleLine.take(maxLength - 3).trim() + "..."
    }
}

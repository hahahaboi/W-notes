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
            mode = WSelectionMode.NONE
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
            anchorIndex > focusIndex -> WSelectionRange(range.focus, range.anchor)
            else -> {
                // Same block
                if (range.anchor.offset <= range.focus.offset) range
                else WSelectionRange(range.focus, range.anchor)
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

        return WSelectionState(
            ranges = listOf(WSelectionRange(
                WSelectionPoint(point.blockId, start),
                WSelectionPoint(point.blockId, finalEnd)
            )),
            isActive = true,
            mode = WSelectionMode.WORD
        )
    }

    /**
     * Selects an entire paragraph (block) at the given point.
     */
    fun selectParagraph(point: WSelectionPoint): WSelectionState {
        return WSelectionState(
            ranges = listOf(WSelectionRange(
                WSelectionPoint(point.blockId, 0),
                WSelectionPoint(point.blockId, Int.MAX_VALUE)
            )),
            isActive = true,
            mode = WSelectionMode.PARAGRAPH
        )
    }

    /**
     * Selects the entire document.
     */
    fun selectAll(blocks: List<WBlock>): WSelectionState {
        if (blocks.isEmpty()) return WSelectionState()
        
        val firstBlock = blocks.first()
        val lastBlock = blocks.last()
        
        return WSelectionState(
            ranges = listOf(WSelectionRange(
                WSelectionPoint(firstBlock.id, 0),
                WSelectionPoint(lastBlock.id, Int.MAX_VALUE)
            )),
            isActive = true,
            mode = WSelectionMode.CHARACTER
        )
    }

    /**
     * Extends the current selection to a new point.
     */
    fun extendSelection(currentState: WSelectionState, newFocus: WSelectionPoint): WSelectionState {
        if (currentState.ranges.isEmpty()) return collapseTo(newFocus)
        
        val lastRange = currentState.ranges.last()
        val newRanges = currentState.ranges.toMutableList()
        newRanges[newRanges.size - 1] = lastRange.copy(focus = newFocus)
        
        return currentState.copy(
            ranges = newRanges, 
            isActive = true,
            mode = if (currentState.mode == WSelectionMode.NONE) WSelectionMode.CHARACTER else currentState.mode
        )
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

    fun intersectsBlock(state: WSelectionState, blockId: String, blocks: List<WBlock>): Boolean {
        return state.ranges.any { range ->
            val normalized = normalize(range, blocks)
            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
            val currentIdx = blocks.indexOfFirst { it.id == blockId }
            currentIdx in startIdx..endIdx
        }
    }
}

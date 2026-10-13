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
     * Hardened to never reorder or touch ranges with unresolvable block IDs.
     */
    fun normalize(range: WSelectionRange, blocks: List<WBlock>): WSelectionRange {
        val anchorIsTitle = range.anchor.blockId == "W_NOTE_TITLE"
        val focusIsTitle = range.focus.blockId == "W_NOTE_TITLE"

        // Title is a virtual block that always precedes document blocks.
        if (anchorIsTitle || focusIsTitle) {
            return if (comparePoints(range.anchor, range.focus, blocks) <= 0) {
                range
            } else {
                WSelectionRange(range.focus, range.anchor, range.id)
            }
        }

        val anchorIndex = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val focusIndex = blocks.indexOfFirst { it.id == range.focus.blockId }

        // Never attempt to reorder a range when one of its blocks no longer exists.
        // Reconciliation is responsible for removing such ranges.
        if (anchorIndex < 0 || focusIndex < 0) return range

        return when {
            anchorIndex < focusIndex -> range
            anchorIndex > focusIndex -> WSelectionRange(range.focus, range.anchor, range.id)
            range.anchor.offset <= range.focus.offset -> range
            else -> WSelectionRange(range.focus, range.anchor, range.id)
        }
    }

    /**
     * Selects an entire word at the given point.
     */
    fun selectWord(point: WSelectionPoint, blocks: List<WBlock>, title: String = ""): WSelectionState {
        val text = if (point.blockId == "W_NOTE_TITLE") {
            title
        } else {
            val block = blocks.find { it.id == point.blockId }
            if (block !is WTextContainer) return collapseTo(point)
            block.text
        }

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
     * Hardened to use actual text length, never Int.MAX_VALUE.
     */
    fun selectParagraph(
        point: WSelectionPoint,
        blocks: List<WBlock>,
        title: String = ""
    ): WSelectionState {
        val textLength = textLengthFor(point.blockId, blocks, title) ?: return collapseTo(point)
        val range = WSelectionRange(
            WSelectionPoint(point.blockId, 0),
            WSelectionPoint(point.blockId, textLength)
        )
        return WSelectionState(
            ranges = listOf(range),
            activeRangeId = range.id,
            isActive = true,
            depth = WSelectionDepth.PARAGRAPH
        )
    }

    /**
     * Selects the entire document, including the title.
     */
    fun selectAll(blocks: List<WBlock>, title: String = ""): WSelectionState {
        val firstBlock = blocks.firstOrNull { it is WTextContainer }
        val firstPoint = if (firstBlock != null) {
            WSelectionPoint(firstBlock.id, 0)
        } else {
            WSelectionPoint("W_NOTE_TITLE", 0)
        }
        
        val lastBlock = blocks.lastOrNull { it is WTextContainer }
        val lastPoint = if (lastBlock != null) {
            WSelectionPoint(lastBlock.id, (lastBlock as WTextContainer).text.length)
        } else {
            WSelectionPoint("W_NOTE_TITLE", title.length)
        }

        val range = WSelectionRange(firstPoint, lastPoint)
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
    fun extendSelection(currentState: WSelectionState, newFocus: WSelectionPoint, blocks: List<WBlock>, title: String = ""): WSelectionState {
        if (currentState.ranges.isEmpty()) return collapseTo(newFocus)

        val lastRange = currentState.ranges.last()

        val snappedFocus = if (currentState.depth == WSelectionDepth.WORD) {
            val range = getWordBoundaries(newFocus, blocks, title)
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
     * Hardened to never crash on missing block IDs or title.
     */
    fun getWordBoundaries(point: WSelectionPoint, blocks: List<WBlock>, title: String = ""): WSelectionRange {
        val text = if (point.blockId == "W_NOTE_TITLE") {
            title
        } else {
            val block = blocks.find { it.id == point.blockId }
            if (block !is WTextContainer) return WSelectionRange(point, point)
            block.text
        }

        if (text.isEmpty()) {
            val safeOffset = if (point.blockId == "W_NOTE_TITLE") 0 else point.offset.coerceAtLeast(0)
            return WSelectionRange(
                WSelectionPoint(point.blockId, safeOffset),
                WSelectionPoint(point.blockId, safeOffset)
            )
        }

        val offset = point.offset.coerceIn(0, text.length)

        var start = offset
        while (start > 0 && !isSeparator(text[start - 1])) start--

        var end = offset
        while (end < text.length && !isSeparator(text[end])) end++

        if (start == end && end < text.length) end++

        return WSelectionRange(
            WSelectionPoint(point.blockId, start),
            WSelectionPoint(point.blockId, end)
        )
    }

    /**
     * Compares two points in the document.
     * Returns > 0 if p1 > p2, < 0 if p1 < p2, 0 if equal.
     * Hardened to never treat unknown block IDs as valid positions.
     */
    fun comparePoints(p1: WSelectionPoint, p2: WSelectionPoint, blocks: List<WBlock>): Int {
        if (p1.blockId == p2.blockId) return p1.offset.compareTo(p2.offset)
        if (p1.blockId == "W_NOTE_TITLE") return -1
        if (p2.blockId == "W_NOTE_TITLE") return 1

        val idx1 = blocks.indexOfFirst { it.id == p1.blockId }
        val idx2 = blocks.indexOfFirst { it.id == p2.blockId }

        // Unknown blocks must not be treated as valid document positions.
        // Keep the comparison deterministic without throwing or producing huge/invalid ranges.
        if (idx1 < 0 || idx2 < 0) {
            return when {
                idx1 == idx2 -> 0
                idx1 < 0 -> -1
                else -> 1
            }
        }

        return idx1.compareTo(idx2)
    }

    /**
     * Removes ranges that no longer belong to the current document revision.
     * Invalid ranges are never coerced: doing so would highlight different text.
     */
    fun reconcile(state: WSelectionState, blocks: List<WBlock>, title: String = ""): WSelectionState {
        val validRanges = state.ranges.mapNotNull { range ->
            if (!isValidPoint(range.anchor, blocks, title) || !isValidPoint(range.focus, blocks, title)) {
                null
            } else {
                normalize(range, blocks)
            }
        }
        val activeRangeId = state.activeRangeId?.takeIf { id -> validRanges.any { it.id == id } }
            ?: validRanges.lastOrNull()?.id
        return state.copy(
            ranges = validRanges,
            activeRangeId = activeRangeId,
            isActive = state.isActive && validRanges.isNotEmpty()
        )
    }

    fun isValidPoint(point: WSelectionPoint, blocks: List<WBlock>, title: String = ""): Boolean {
        val textLength = textLengthFor(point.blockId, blocks, title) ?: return false
        return point.offset in 0..textLength
    }

    private fun textLengthFor(blockId: String, blocks: List<WBlock>, title: String): Int? = when (blockId) {
        "W_NOTE_TITLE" -> title.length
        else -> (blocks.find { it.id == blockId } as? WTextContainer)?.text?.length
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
     * Extracts blocks from the selection, preserving spans and block types.
     */
    fun getSelectedBlocks(state: WSelectionState, blocks: List<WBlock>, title: String = ""): List<WBlock> {
        if (!state.isActive || state.ranges.isEmpty()) return emptyList()

        val validRanges = state.ranges.mapNotNull { range ->
            if (isValidPoint(range.anchor, blocks, title) && isValidPoint(range.focus, blocks, title)) {
                normalize(range, blocks)
            } else {
                null
            }
        }

        if (validRanges.isEmpty()) return emptyList()

        val mergedRanges = mergeOverlappingSelections(validRanges, blocks)
        val sortedRanges = normalizeSelectionOrder(mergedRanges, blocks)
        val result = mutableListOf<WBlock>()

        sortedRanges.forEach { range ->
            val normalized = normalize(range, blocks)

            if (normalized.anchor.blockId == "W_NOTE_TITLE") {
                val start = normalized.anchor.offset.coerceIn(0, title.length)
                val end = if (normalized.focus.blockId == "W_NOTE_TITLE") {
                    normalized.focus.offset.coerceIn(0, title.length)
                } else {
                    title.length
                }.coerceIn(0, title.length)
                
                if (start < end) {
                    result.add(
                        com.tv.wnotes.ui.editor.engine.model.WTextBlock(
                            id = java.util.UUID.randomUUID().toString(),
                            text = title.substring(start, end),
                            type = com.tv.wnotes.ui.editor.engine.model.TextBlockType.H1
                        )
                    )
                }
                if (normalized.focus.blockId == "W_NOTE_TITLE") return@forEach
            }

            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }

            if (startIdx != -1 && endIdx != -1) {
                for (i in startIdx..endIdx) {
                    val block = blocks[i]
                    if (block !is WTextContainer) continue

                    val textLen = block.text.length
                    val start = if (i == startIdx) normalized.anchor.offset.coerceIn(0, textLen) else 0
                    val end = if (i == endIdx) normalized.focus.offset.coerceIn(0, textLen) else textLen

                    if (start < end) {
                        val text = block.text.substring(start, end)
                        val spans = block.spans.filter { it.start < end && it.end > start }
                            .map { span ->
                                val s = maxOf(span.start, start) - start
                                val e = minOf(span.end, end) - start
                                span.copy(start = s, end = e)
                            }
                        
                        val newBlock = when (block) {
                            is com.tv.wnotes.ui.editor.engine.model.WTextBlock -> block.copy(id = java.util.UUID.randomUUID().toString(), text = text, spans = spans)
                            is com.tv.wnotes.ui.editor.engine.model.WChecklistBlock -> block.copy(id = java.util.UUID.randomUUID().toString(), text = text, spans = spans)
                            else -> com.tv.wnotes.ui.editor.engine.model.WTextBlock(id = java.util.UUID.randomUUID().toString(), text = text, spans = spans)
                        }
                        result.add(newBlock)
                    }
                }
            }
        }

        return result
    }

    /**
     * Extracts plain text from all selected ranges, preserving newlines between blocks.
     * Hardened to handle multi-block, multi-line, and overlapping selections.
     */
    fun getSelectedText(state: WSelectionState, blocks: List<WBlock>): String {
        return getSelectedText(state, blocks, "")
    }

    fun getSelectedText(state: WSelectionState, blocks: List<WBlock>, title: String): String {
        if (!state.isActive || state.ranges.isEmpty()) return ""

        val validRanges = state.ranges.mapNotNull { range ->
            if (isValidPoint(range.anchor, blocks, title) && isValidPoint(range.focus, blocks, title)) {
                normalize(range, blocks)
            } else {
                null
            }
        }

        if (validRanges.isEmpty()) return ""

        val mergedRanges = mergeOverlappingSelections(validRanges, blocks)
        val sortedRanges = normalizeSelectionOrder(mergedRanges, blocks)
        val sb = StringBuilder()

        sortedRanges.forEachIndexed { rangeIndex, range ->
            val normalized = normalize(range, blocks)

            if (normalized.anchor.blockId == "W_NOTE_TITLE") {
                val start = normalized.anchor.offset.coerceIn(0, title.length)
                val end = normalized.focus.offset.coerceIn(0, title.length)
                if (start < end) sb.append(title.substring(start, end))

                if (normalized.focus.blockId != "W_NOTE_TITLE") {
                    sb.append("\n")
                } else if (rangeIndex < sortedRanges.size - 1) {
                    sb.append("\n\n")
                }

                if (normalized.focus.blockId == "W_NOTE_TITLE") return@forEachIndexed
            }

            val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
            val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }

            // A stale range can survive a document mutation between frames. Ignore it
            // instead of feeding invalid offsets into text-layout code elsewhere.
            if (startIdx < 0 || endIdx < 0 || startIdx > endIdx) return@forEachIndexed

            for (i in startIdx..endIdx) {
                val block = blocks[i]
                if (block !is WTextContainer) continue

                val textLen = block.text.length
                val start = if (i == startIdx) normalized.anchor.offset.coerceIn(0, textLen) else 0
                val end = if (i == endIdx) normalized.focus.offset.coerceIn(0, textLen) else textLen

                if (start < end) sb.append(block.text.substring(start, end))
                if (i < endIdx) sb.append("\n")
            }

            if (rangeIndex < sortedRanges.size - 1) sb.append("\n\n")
        }

        return sb.toString()
    }

    /**
     * Generates a concise label from raw text, adhering to UI length constraints.
     */
    fun generateLabel(text: String, maxLength: Int = 25): String {
        val singleLine = text.replace("\n", " ").replace("\r", "").trim()
        if (singleLine.isEmpty()) return "Snippet"
        if (singleLine.length <= maxLength) return singleLine
        
        return singleLine.take(maxLength - 3).trim() + "..."
    }
}

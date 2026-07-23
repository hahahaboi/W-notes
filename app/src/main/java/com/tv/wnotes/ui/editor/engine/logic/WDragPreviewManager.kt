package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Logic for building and managing the floating drag preview.
 */
object WDragPreviewManager {

    /**
     * Builds a preview state for the current selection.
     */
    fun buildPreview(
        snapshot: WEditorSnapshot,
        layoutProvider: WLayoutProvider,
        fingerPosition: Offset
    ): WDragPreviewState {
        val range = snapshot.selection.ranges.firstOrNull() ?: return WDragPreviewState()
        val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
        
        val fragment = captureFragment(snapshot.document, normalized)
        
        val selectionBounds = layoutProvider.getSelectionBounds()
        val combinedRect = if (selectionBounds.isEmpty()) Rect.Zero else {
            var minL = Float.MAX_VALUE
            var minT = Float.MAX_VALUE
            var maxR = -Float.MAX_VALUE
            var maxB = -Float.MAX_VALUE
            selectionBounds.forEach {
                minL = minOf(minL, it.left)
                minT = minOf(minT, it.top)
                maxR = maxOf(maxR, it.right)
                maxB = maxOf(maxB, it.bottom)
            }
            Rect(minL, minT, maxR, maxB)
        }

        // Calculate finger offset: Vector from finger to top-left of selection.
        // We ensure we don't divide by zero or use crazy values.
        val offset = if (combinedRect != Rect.Zero) {
            combinedRect.topLeft - fingerPosition
        } else {
            Offset.Zero
        }

        return WDragPreviewState(
            isVisible = true,
            content = fragment,
            position = fingerPosition,
            originalBounds = combinedRect,
            currentBounds = combinedRect,
            elevation = 8f,
            scale = 1.05f,
            opacity = 0.95f,
            fingerOffset = offset
        )
    }

    private fun captureFragment(document: WDocument, range: WSelectionRange): WDocumentFragment {
        val blocks = document.blocks
        val startIdx = blocks.indexOfFirst { it.id == range.anchor.blockId }
        val endIdx = blocks.indexOfFirst { it.id == range.focus.blockId }
        
        if (startIdx == -1 || endIdx == -1) return WDocumentFragment(emptyList())
        
        val extractedBlocks = mutableListOf<WBlock>()
        for (i in startIdx..endIdx) {
            val block = blocks[i]
            if (block !is WTextContainer) continue
            
            val start = if (i == startIdx) range.anchor.offset else 0
            val end = if (i == endIdx) range.focus.offset else block.text.length
            
            // Boundary safety
            val safeStart = start.coerceIn(0, block.text.length)
            val safeEnd = end.coerceIn(safeStart, block.text.length)
            
            val sliceText = block.text.substring(safeStart, safeEnd)
            val sliceSpans = block.spans.filter { it.start < safeEnd && it.end > safeStart }
                .map { it.copy(
                    start = (it.start - safeStart).coerceAtLeast(0),
                    end = (it.end - safeStart).coerceAtMost(sliceText.length)
                ) }

            val slicedBlock = block.copyWithText(sliceText, sliceSpans)
            extractedBlocks.add(slicedBlock)
        }
        
        return WDocumentFragment(blocks = extractedBlocks)
    }
}

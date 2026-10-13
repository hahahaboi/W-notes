package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Logic for calculating the predictive insertion caret during drag-and-drop.
 */
object WInsertionCaretManager {

    /**
     * Calculates the insertion caret state for a given drag position.
     */
    fun calculateInsertionState(
        fingerPosition: Offset,
        dragState: WDragState,
        layoutProvider: WLayoutProvider,
        blocks: List<WBlock>
    ): WInsertionCaretState {
        println("DROP STAGE 2: Pointer Position - $fingerPosition")
        if (dragState.phase == WDragPhase.IDLE || dragState.phase == WDragPhase.PREPARING_LIFT) {
            return WInsertionCaretState(isVisible = false)
        }

        // 1. Hit-test to find the semantic document position
        val hit = layoutProvider.hitTest(fingerPosition)
        println("DROP STAGE 3: Hit Test Result - $hit")
        if (hit == null) return WInsertionCaretState(isVisible = false)
        
        val point = WSelectionPoint(hit.blockId, hit.characterIndex)
        println("DROP STAGE 4: Calculated Block - ${hit.blockId}")
        println("DROP STAGE 5: Calculated Offset - ${hit.characterIndex}")
        
        // 2. Check if the target is valid (cannot drop inside the source selection)
        val isValid = if (dragState.selectedRanges.isNotEmpty()) {
            !WSelectionManager.containsAnyExclusive(
                ranges = dragState.selectedRanges,
                blockId = point.blockId,
                offset = point.offset,
                blocks = blocks
            )
        } else true

        // 3. Get the physical geometry for the insertion beam
        val beamRect = layoutProvider.getInsertionBeamForPoint(point) ?: Rect.Zero

        // 4. Handle Virtual Drop Area
        var finalRect = beamRect
        var virtualLineCount = 0
        var isVirtual = false

        if (hit.isBelowDocument) {
            val lastBlockId = blocks.lastOrNull()?.id
            if (lastBlockId != null) {
                // Approximate line height from the beam height or a default
                val lineHeight = if (beamRect.height > 0) beamRect.height else 60f 
                
                // Calculate how many lines below the document we are
                // hit.isBelowDocument was determined by comparing pointerY to blockLayouts.last().bottom
                // But the 'beamRect' from getInsertionBeamForPoint(point) was clamped to the last char.
                // We want the virtual caret to appear at the same X but at a new Y.
                
                // We need to estimate the Y offset from the last line.
                // For simplicity, we can use the pointer position relative to the clamped beam.
                val extraY = (fingerPosition.y - beamRect.bottom).coerceAtLeast(0f)
                virtualLineCount = (extraY / lineHeight).toInt() + 1
                
                if (virtualLineCount > 0) {
                    isVirtual = true
                    finalRect = beamRect.translate(0f, virtualLineCount * lineHeight)
                }
            }
        }

        println("DROP STAGE 11.5: isVirtual=$isVirtual, virtualLines=$virtualLineCount")

        return WInsertionCaretState(
            isVisible = true,
            insertionPoint = point,
            drawRect = finalRect,
            lineIndex = hit.lineIndex,
            affinity = hit.affinity,
            isValidTarget = isValid,
            virtualLineCount = virtualLineCount,
            isVirtual = isVirtual
        )
    }
}

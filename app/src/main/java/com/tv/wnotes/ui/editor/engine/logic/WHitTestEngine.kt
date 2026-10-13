package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult

/**
 * High-performance hit-testing engine for WEditor.
 * Converts physical screen coordinates into semantic document positions.
 */
object WHitTestEngine {

    /**
     * Performs a hit-test against a set of block layouts.
     * @param offset Editor-relative coordinates.
     * @param blockLayouts Sorted list of blocks with their layout and position info.
     */
    fun hitTest(
        offset: Offset,
        blockLayouts: List<WBlockLayoutInfo>
    ): WHitResult? {
        if (blockLayouts.isEmpty()) {
            println("HIT TEST: Failed - blockLayouts is empty")
            return null
        }

        // 1. Find the block containing the Y coordinate.
        var isBelow = false
        val targetBlock = findBlockAtY(offset.y, blockLayouts) ?: run {
            isBelow = offset.y > blockLayouts.last().relativeOffset.y + blockLayouts.last().height
            clampToNearestBlock(offset, blockLayouts)
        }
        println("HIT TEST: Target block found: ${targetBlock.blockId}, isBelow=$isBelow")

        val layout = targetBlock.layoutResult
        // Convert to block-local coordinates, accounting for indentation
        val localX = offset.x - targetBlock.relativeOffset.x - targetBlock.indentationPx
        val localY = offset.y - targetBlock.relativeOffset.y
        val localOffset = Offset(localX, localY)
        
        // 2. Determine the visual line index
        val lineIndex = layout.getLineForVerticalPosition(localOffset.y)
        
        // 3. Determine the character index
        val characterIndex = layout.getOffsetForPosition(localOffset)
        
        // 4. Determine cursor affinity (BEFORE or AFTER glyph)
        val cursorRect = layout.getCursorRect(characterIndex)
        val affinity = if (localOffset.x < cursorRect.left) {
            CursorAffinity.BEFORE
        } else {
            CursorAffinity.AFTER
        }

        val lineRight = layout.getLineRight(lineIndex)
        val lineLeft = layout.getLineLeft(lineIndex)
        val isOnText = !isBelow && localX >= lineLeft && localX <= lineRight

        return WHitResult(
            blockId = targetBlock.blockId,
            characterIndex = characterIndex,
            lineIndex = lineIndex,
            affinity = affinity,
            isBelowDocument = isBelow,
            isOnText = isOnText
        )
    }

    private fun findBlockAtY(y: Float, layouts: List<WBlockLayoutInfo>): WBlockLayoutInfo? {
        return layouts.find { info ->
            y >= info.relativeOffset.y && y <= info.relativeOffset.y + info.height
        }
    }

    private fun clampToNearestBlock(offset: Offset, layouts: List<WBlockLayoutInfo>): WBlockLayoutInfo {
        return if (offset.y < layouts.first().relativeOffset.y) {
            layouts.first()
        } else {
            layouts.last()
        }
    }
}

/**
 * Lightweight container for block layout geometry.
 */
data class WBlockLayoutInfo(
    val blockId: String,
    val layoutResult: TextLayoutResult,
    val relativeOffset: Offset,
    val height: Float,
    val indentationPx: Float = 0f
)

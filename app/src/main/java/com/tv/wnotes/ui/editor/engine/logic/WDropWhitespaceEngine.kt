package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange
import com.tv.wnotes.ui.editor.engine.state.WSmartWhitespaceTelemetry

/**
 * Smart Drop Whitespace Engine for WEditor.
 * Responsible for determining natural spacing during drag-and-drop operations.
 */
object WDropWhitespaceEngine {

    fun normalizeDropWhitespace(
        source: WSelectionRange,
        target: WSelectionPoint,
        document: WDocument
    ): WDropNormalizationResult {
        val targetBlock = document.blocks.find { it.id == target.blockId } as? WTextContainer
            ?: return WDropNormalizationResult(telemetry = WSmartWhitespaceTelemetry())

        val text = targetBlock.text
        val offset = target.offset.coerceIn(0, text.length)

        // 1. Destination Context
        val isAtStart = offset == 0
        val isAtEnd = offset == text.length
        val charBefore = if (offset > 0) text[offset - 1] else null
        val charAfter = if (offset < text.length) text[offset] else null

        // 2. Analyze Context
        val beforeIsWhitespace = charBefore?.isWhitespace() ?: true
        val afterIsWhitespace = charAfter?.isWhitespace() ?: true
        val beforeIsPunctuation = charBefore != null && isPunctuation(charBefore)
        val afterIsPunctuation = charAfter != null && isPunctuation(charAfter)
        val beforeIsOpeningPunctuation = charBefore != null && isOpeningPunctuation(charBefore)
        val afterIsClosingPunctuation = charAfter != null && isClosingPunctuation(charAfter)
        val beforeIsNewline = charBefore == '\n'
        val afterIsNewline = charAfter == '\n'

        val isBetweenWords = !isAtStart && !isAtEnd && !beforeIsWhitespace && !afterIsWhitespace && !beforeIsPunctuation && !afterIsPunctuation

        // 3. Determine Spacing
        var prefix = ""
        var suffix = ""

        // Rule: Never leading space at start of block or after newline or after opening punctuation
        if (!isAtStart && !beforeIsWhitespace && !beforeIsNewline && !beforeIsOpeningPunctuation) {
            prefix = " "
        }

        // Rule: Never trailing space at end of block or before newline or before closing punctuation
        if (!isAtEnd && !afterIsWhitespace && !afterIsNewline && !afterIsClosingPunctuation) {
            suffix = " "
        }

        // Rule 8: Empty blocks
        if (text.isEmpty()) {
            prefix = ""
            suffix = ""
        }

        return WDropNormalizationResult(
            prefix = prefix,
            suffix = suffix,
            telemetry = WSmartWhitespaceTelemetry(
                whitespaceBefore = if (beforeIsWhitespace) "YES" else "NO",
                whitespaceAfter = if (afterIsWhitespace) "YES" else "NO",
                beginningOfBlock = isAtStart,
                endOfBlock = isAtEnd,
                betweenWords = isBetweenWords,
                adjacentPunctuation = beforeIsPunctuation || afterIsPunctuation,
                adjacentNewline = beforeIsNewline || afterIsNewline,
                insertedLeadingSpace = prefix.isNotEmpty(),
                insertedTrailingSpace = suffix.isNotEmpty(),
                finalDestinationOffset = offset
            )
        )
    }

    private fun isPunctuation(c: Char): Boolean {
        return ".,!?:;)]}".contains(c)
    }

    private fun isOpeningPunctuation(c: Char): Boolean {
        return "([{".contains(c)
    }

    private fun isClosingPunctuation(c: Char): Boolean {
        return ")]}".contains(c)
    }
}

data class WDropNormalizationResult(
    val prefix: String = "",
    val suffix: String = "",
    val telemetry: WSmartWhitespaceTelemetry
)

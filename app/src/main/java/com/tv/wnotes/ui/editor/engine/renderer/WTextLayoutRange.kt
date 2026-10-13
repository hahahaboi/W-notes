package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult

/**
 * The only gateway for converting editor offsets into a Compose text-layout path.
 *
 * Editor selections may briefly refer to an older document revision while Compose
 * is producing a new [TextLayoutResult]. Those offsets must be rejected, not
 * coerced, because coercion turns a stale selection into a different selection.
 */
data class WTextLayoutRange(val start: Int, val end: Int)

fun TextLayoutResult.validRangeOrNull(start: Int, end: Int): WTextLayoutRange? {
    val textLength = layoutInput.text.length
    if (start < 0 || end < 0 || start > end || start > textLength || end > textLength) {
        return null
    }
    return WTextLayoutRange(start, end)
}

fun TextLayoutResult.validOffsetOrNull(offset: Int): Int? =
    offset.takeIf { it in 0..layoutInput.text.length }

fun TextLayoutResult.selectionPathOrNull(start: Int, end: Int): Path? {
    val range = validRangeOrNull(start, end) ?: return null
    if (range.start == range.end) return null
    return getPathForRange(range.start, range.end)
}

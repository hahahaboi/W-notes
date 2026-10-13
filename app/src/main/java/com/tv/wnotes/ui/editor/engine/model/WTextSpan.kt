package com.tv.wnotes.ui.editor.engine.model

import androidx.compose.runtime.Immutable

/**
 * Represents rich text formatting within a text block.
 */
@Immutable
data class WTextSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val attributes: Map<String, String> = emptyMap()
)

enum class SpanType {
    Bold, Italic, Underline, Strikethrough, 
    Link, Highlight, Code, Comment, Backlink,
    FontSize, FontFamily
}

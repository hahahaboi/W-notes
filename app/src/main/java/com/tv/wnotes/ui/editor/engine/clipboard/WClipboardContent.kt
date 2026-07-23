package com.tv.wnotes.ui.editor.engine.clipboard

import androidx.compose.runtime.Immutable
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextSpan

/**
 * Represents the internal rich representation of content in the clipboard.
 */
@Immutable
data class WClipboardContent(
    val version: Int = 1,
    val blocks: List<WBlock> = emptyList(),
    val plainText: String = "",
    val isPartialBlock: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

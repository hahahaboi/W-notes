package com.tv.wnotes.ui.editor.engine.model

import androidx.compose.runtime.Immutable

/**
 * Represents a slice of a document, containing one or more blocks or partial blocks.
 */
@Immutable
data class WDocumentFragment(
    val blocks: List<WBlock>
)

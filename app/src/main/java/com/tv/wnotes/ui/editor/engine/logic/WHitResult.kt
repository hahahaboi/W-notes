package com.tv.wnotes.ui.editor.engine.logic

/**
 * The semantic result of a hit-test operation.
 */
data class WHitResult(
    val blockId: String,
    val characterIndex: Int,
    val lineIndex: Int,
    val affinity: CursorAffinity,
    val isBelowDocument: Boolean = false
)

enum class CursorAffinity {
    BEFORE,
    AFTER
}

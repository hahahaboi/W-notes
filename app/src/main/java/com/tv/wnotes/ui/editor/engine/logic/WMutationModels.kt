package com.tv.wnotes.ui.editor.engine.logic

import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange

/**
 * Types of mutations supported by the WMutationEngine.
 */
enum class WMutationType {
    INSERT_TEXT,
    DELETE_RANGE,
    REPLACE_RANGE,
    MOVE_RANGE,
    SPLIT_BLOCK,
    MERGE_BLOCKS
}

/**
 * Encapsulates the result of a document mutation.
 */
sealed interface WMutationResult {
    data class Success(
        val snapshot: WEditorSnapshot,
        val mutationType: WMutationType,
        val affectedBlockIds: Set<String>,
        val affectedCharacterCount: Int
    ) : WMutationResult

    data class Failure(
        val reason: String,
        val mutationType: WMutationType
    ) : WMutationResult
}

/**
 * A request for a document transformation.
 */
sealed interface WMutationRequest {
    data class InsertText(val text: String) : WMutationRequest
    data object DeleteBackward : WMutationRequest
    data object DeleteForward : WMutationRequest
    data class MoveRange(val sourceRange: WSelectionRange, val targetPoint: WSelectionPoint) : WMutationRequest
    data object SplitBlock : WMutationRequest
}

package com.tv.wnotes.ui.editor.engine.state

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.model.WDocumentFragment

/**
 * State of the floating drag preview.
 */
@Immutable
data class WDragPreviewState(
    val isVisible: Boolean = false,
    val content: WDocumentFragment? = null,
    val position: Offset = Offset.Unspecified,
    val originalBounds: Rect = Rect.Zero,
    val currentBounds: Rect = Rect.Zero,
    val elevation: Float = 0f,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val fingerOffset: Offset = Offset.Zero
)

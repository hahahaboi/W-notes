package com.tv.wnotes.ui.editor.engine.state

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

enum class WSelectionHandleType {
    START,
    END
}

@Immutable
data class WSelectionHandleState(
    val type: WSelectionHandleType,
    val position: Offset = Offset.Unspecified,
    val isVisible: Boolean = false,
    val isPressed: Boolean = false
)

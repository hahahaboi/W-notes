package com.tv.wnotes.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot

/**
 * Single source of truth for the UI layer.
 * Wraps the existing engine state.
 */
@Stable
class WEditorState(
    private val snapshotState: State<WEditorSnapshot>
) {
    val snapshot: WEditorSnapshot get() = snapshotState.value
    
    val document get() = snapshot.document
    val selection get() = snapshot.selection
    val cursor get() = snapshot.cursor
    val drag get() = snapshot.drag
    val viewport get() = snapshot.viewport
}

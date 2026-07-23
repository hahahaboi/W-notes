package com.tv.wnotes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import com.tv.wnotes.ui.editor.engine.renderer.WDragAnimationController
import com.tv.wnotes.ui.editor.engine.renderer.WEditorRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WLayoutState
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot

/**
 * Dedicated drawing surface for WEditor.
 * Renders using the existing renderer package.
 */
@Composable
fun WEditorSurface(
    snapshot: WEditorSnapshot,
    layoutState: WLayoutState,
    editorCoordinates: LayoutCoordinates?,
    animController: WDragAnimationController,
    cursorBlinkVisible: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        WEditorRenderer(
            snapshot = snapshot,
            layoutState = layoutState,
            editorCoordinates = editorCoordinates,
            animController = animController,
            cursorBlinkVisible = cursorBlinkVisible,
            modifier = Modifier.fillMaxSize()
        )
    }
}

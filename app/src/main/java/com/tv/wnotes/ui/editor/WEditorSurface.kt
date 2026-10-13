package com.tv.wnotes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    scrollState: androidx.compose.foundation.ScrollState,
    title: String,
    onTitleChange: (String) -> Unit,
    gestureCoordinator: com.tv.wnotes.ui.editor.engine.logic.WGestureCoordinator,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onGestureDetected: (String) -> Unit,
    onCaretRendered: () -> Unit,
    onContentPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        WEditorRenderer(
            snapshot = snapshot,
            layoutState = layoutState,
            editorCoordinates = editorCoordinates,
            animController = animController,
            cursorBlinkVisible = cursorBlinkVisible,
            scrollState = scrollState,
            title = title,
            onTitleChange = onTitleChange,
            gestureCoordinator = gestureCoordinator,
            focusRequester = focusRequester,
            onGestureDetected = onGestureDetected,
            onCaretRendered = onCaretRendered,
            onContentPositioned = onContentPositioned,
            modifier = Modifier.fillMaxSize()
        )
    }
}

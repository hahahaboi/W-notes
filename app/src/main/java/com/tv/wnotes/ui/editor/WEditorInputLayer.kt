package com.tv.wnotes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.input.WShortcut
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.NavigationDirection


/**
 * High-priority input layer that captures keyboard/IME events.
 * Uses a hidden BasicTextField as a proxy to communicate with the Android IME.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WEditorInputLayer(
    host: WEditorHost,
    focusRequester: FocusRequester,
    onPlaced: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Hidden buffer: Two spaces to provide a reliable context for keyboards
    val emptyBuffer = "  "
    var textFieldValue by remember { mutableStateOf(TextFieldValue(emptyBuffer, selection = androidx.compose.ui.text.TextRange(1))) }

    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                // Return an empty rect to indicate we don't want to scroll anywhere
                return Rect.Zero
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // Do nothing: intercept and stop the bringIntoView request.
                // This prevents the "Expected BringIntoViewRequester to not be used before parents are placed" crash.
            }
        }
    }
    
    // Hidden Input Proxy: Transparent and fills the editor surface for reliable focus.
    // We add an empty pointerInput to ensure it doesn't process any touch events itself.
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0f }
            .pointerInput(Unit) {}
            .bringIntoViewResponder(responder)
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                if (newValue.text.length > textFieldValue.text.length) {
                    // For Stage 13 robustness, we take the last added character or the diff
                    val inserted = if (newValue.text.startsWith(emptyBuffer)) {
                        newValue.text.substring(emptyBuffer.length)
                    } else if (newValue.text.length > 1) {
                         newValue.text.substring(1, newValue.text.length - (emptyBuffer.length - 1))
                    } else {
                        newValue.text.filterIndexed { i, c -> i >= emptyBuffer.length || c != emptyBuffer[i] }
                    }
                    
                    if (inserted.isNotEmpty()) {
                        host.inputController.onCommitText(inserted)
                    }
                } else if (newValue.text.length < textFieldValue.text.length) {
                    host.inputController.onDeleteBackward()
                }
                
                // Always reset to buffer to keep the field "not empty"
                textFieldValue = TextFieldValue(emptyBuffer, selection = androidx.compose.ui.text.TextRange(1))
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .onPlaced { _ -> onPlaced() }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        handleHardwareKey(keyEvent, host)
                    } else false
                },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = androidx.compose.ui.text.input.ImeAction.Default
            ),
            keyboardActions = KeyboardActions.Default,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent)
        )
    }
}

private fun handleHardwareKey(event: KeyEvent, host: WEditorHost): Boolean {
    val isCtrl = event.isCtrlPressed
    val isShift = event.isShiftPressed
    val layoutProvider = host.gestureCoordinator.layoutProvider

    return when (event.key) {
        Key.Z -> {
            if (isCtrl) {
                if (isShift) host.inputController.onKeyboardShortcut(WShortcut.Redo)
                else host.inputController.onKeyboardShortcut(WShortcut.Undo)
                true
            } else false
        }
        Key.Y -> {
            if (isCtrl) {
                host.inputController.onKeyboardShortcut(WShortcut.Redo)
                true
            } else false
        }
        Key.A -> {
            if (isCtrl) {
                host.inputController.onKeyboardShortcut(WShortcut.SelectAll)
                true
            } else false
        }
        Key.C -> {
            if (isCtrl) {
                host.inputController.onKeyboardShortcut(WShortcut.Copy)
                true
            } else false
        }
        Key.X -> {
            if (isCtrl) {
                host.inputController.onKeyboardShortcut(WShortcut.Cut)
                true
            } else false
        }
        Key.V -> {
            if (isCtrl) {
                if (isShift) host.inputController.onKeyboardShortcut(WShortcut.PastePlainText)
                else host.inputController.onKeyboardShortcut(WShortcut.Paste)
                true
            } else false
        }
        Key.DirectionLeft -> {
            host.inputController.onNavigation(NavigationDirection.LEFT, layoutProvider, isShift)
            true
        }
        Key.DirectionRight -> {
            host.inputController.onNavigation(NavigationDirection.RIGHT, layoutProvider, isShift)
            true
        }
        Key.DirectionUp -> {
            host.inputController.onNavigation(NavigationDirection.UP, layoutProvider, isShift)
            true
        }
        Key.DirectionDown -> {
            host.inputController.onNavigation(NavigationDirection.DOWN, layoutProvider, isShift)
            true
        }
        Key.Enter -> {
            host.gestureCoordinator.onCommand(WEditorCommand.SplitBlock)
            true
        }
        Key.Backspace -> {
            host.inputController.onDeleteBackward()
            true
        }
        Key.Delete -> {
            host.gestureCoordinator.onCommand(WEditorCommand.DeleteForward)
            true
        }
        else -> false
    }
}

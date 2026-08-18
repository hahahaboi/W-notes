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
    modifier: Modifier = Modifier,
    onPlaced: () -> Unit = {},
) {
    // Hidden buffer: Two spaces to provide a reliable context for keyboards
    val emptyBuffer = "  "
    var textFieldValue by remember { mutableStateOf(TextFieldValue(emptyBuffer, selection = androidx.compose.ui.text.TextRange(1))) }
    
    // Track last input to prevent double-typing from some IMEs that send both KeyEvents and commitText
    var lastInsertedText by remember { mutableStateOf("") }
    var lastInsertedTime by remember { mutableLongStateOf(0L) }

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
                val newText = newValue.text
                val oldText = textFieldValue.text

                if (newText.length > oldText.length) {
                    // Robust extraction: remove all characters of the old text 
                    // from the new text to find exactly what was added.
                    val addedList = newText.toMutableList()
                    for (char in oldText) {
                        addedList.remove(char)
                    }
                    val inserted = addedList.joinToString("")
                    
                    if (inserted.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        // If we see the same text inserted twice within 50ms, it's likely a duplicate from the IME
                        if ((inserted != lastInsertedText) || (now - lastInsertedTime > 50)) {
                            host.inputController.onCommitText(inserted)
                            lastInsertedText = inserted
                            lastInsertedTime = now
                        }
                    }
                } else if (newText.length < oldText.length) {
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
                    handleHardwareKey(keyEvent, host) { inserted ->
                        val now = System.currentTimeMillis()
                        host.inputController.onCommitText(inserted)
                        lastInsertedText = inserted
                        lastInsertedTime = now
                    }
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

private fun handleHardwareKey(
    event: KeyEvent,
    host: WEditorHost,
    onTextHandled: (String) -> Unit
): Boolean {
    val isCtrl = event.isCtrlPressed
    val isShift = event.isShiftPressed
    val layoutProvider = host.gestureCoordinator.layoutProvider

    // Handle Numeric Keys to avoid double-typing on some devices/IMEs
    val digit = when (event.key) {
        Key.Zero, Key.NumPad0 -> "0"
        Key.One, Key.NumPad1 -> "1"
        Key.Two, Key.NumPad2 -> "2"
        Key.Three, Key.NumPad3 -> "3"
        Key.Four, Key.NumPad4 -> "4"
        Key.Five, Key.NumPad5 -> "5"
        Key.Six, Key.NumPad6 -> "6"
        Key.Seven, Key.NumPad7 -> "7"
        Key.Eight, Key.NumPad8 -> "8"
        Key.Nine, Key.NumPad9 -> "9"
        else -> null
    }

    if (digit != null && !isCtrl) {
        onTextHandled(digit)
        return true
    }

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

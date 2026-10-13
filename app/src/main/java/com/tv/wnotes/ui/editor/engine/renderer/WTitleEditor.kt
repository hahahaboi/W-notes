package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun WTitleEditor(
    snapshot: WEditorSnapshot,
    title: String,
    onTitleChange: (String) -> Unit,
    layoutState: WLayoutState,
    onCommand: (WEditorCommand) -> Unit,
    rootCoordinates: WEditorRootCoordinates? = null,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(title))
    }

    // Sync with external title changes
    LaunchedEffect(title) {
        if (textFieldValue.text != title) {
            textFieldValue = textFieldValue.copy(
                text = title,
                selection = TextRange(
                    textFieldValue.selection.start.coerceIn(0, title.length),
                    textFieldValue.selection.end.coerceIn(0, title.length)
                )
            )
        }
    }

    // Sync selection with WEditorSnapshot if it's in the title
    LaunchedEffect(snapshot.selection, snapshot.cursor.position, snapshot.document.metadata.title) {
        val engineTitle = snapshot.document.metadata.title
        // Only sync selection from engine if our local text is already in sync with engine
        // This prevents the engine from pulling the cursor back during active typing
        if (textFieldValue.text != engineTitle) return@LaunchedEffect

        val sel = snapshot.selection
        if (sel.isActive && sel.ranges.firstOrNull()?.anchor?.blockId == "W_NOTE_TITLE") {
            val range = sel.ranges.first()
            if (!WSelectionManager.isValidPoint(range.anchor, snapshot.document.blocks, title) ||
                !WSelectionManager.isValidPoint(range.focus, snapshot.document.blocks, title)
            ) return@LaunchedEffect
            val newSelection = TextRange(range.anchor.offset, range.focus.offset)
            if (textFieldValue.selection != newSelection) {
                textFieldValue = textFieldValue.copy(selection = newSelection)
            }
        } else if (!sel.isActive && snapshot.cursor.position.blockId == "W_NOTE_TITLE") {
            val cursor = snapshot.cursor.position
            if (!WSelectionManager.isValidPoint(cursor, snapshot.document.blocks, title)) return@LaunchedEffect
            val newSelection = TextRange(cursor.offset)
            if (textFieldValue.selection != newSelection) {
                textFieldValue = textFieldValue.copy(selection = newSelection)
            }
        }
    }

    // Remove redundant local selection sync to engine as onValueChange handles it

    val titleAnnotation = remember(snapshot.annotations) {
        snapshot.annotations.find { it.blockId == "W_NOTE_TITLE" }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val textStyle = MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Box(modifier = modifier) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = {
                val newOffset = if (it.selection.collapsed) it.selection.end else null
                
                // Only send UpdateTitle if the text actually changed
                if (it.text != textFieldValue.text) {
                    onCommand(WEditorCommand.UpdateTitle(it.text, newOffset))
                    onTitleChange(it.text)
                } else if (it.selection != textFieldValue.selection) {
                    // Just a selection change (tap/arrows)
                    if (it.selection.collapsed) {
                        onCommand(WEditorCommand.MoveCursor(WSelectionPoint("W_NOTE_TITLE", it.selection.end)))
                    } else {
                        val range = WSelectionRange(
                            WSelectionPoint("W_NOTE_TITLE", it.selection.start),
                            WSelectionPoint("W_NOTE_TITLE", it.selection.end)
                        )
                        onCommand(WEditorCommand.SetSelection(
                            com.tv.wnotes.ui.editor.engine.state.WSelectionState(
                                ranges = listOf(range),
                                isActive = true,
                                activeRangeId = range.id
                            )
                        ))
                    }
                }
                textFieldValue = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    rootCoordinates?.value?.let { docRoot ->
                        layoutState.titleOffset = docRoot.localPositionOf(coords, Offset.Zero)
                    } ?: run {
                        layoutState.titleOffset = coords.positionInParent()
                    }
                }
                .drawTitleAnnotationUnderline(
                    hasAnnotation = titleAnnotation != null,
                    color = MaterialTheme.colorScheme.primary
                )
                .pointerInput(titleAnnotation) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        }
                        
                        if (up != null) {
                            // Tap
                            if (titleAnnotation != null) {
                                // Tap on annotated title: Show Context
                                onCommand(WEditorCommand.ShowAnnotation(titleAnnotation.id))
                                up.consume()
                            }
                        } else {
                            // Long Press
                            menuOffset = down.position
                            showContextMenu = true
                        }
                    }
                },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            decorationBox = { innerTextField ->
                if (title.isEmpty()) {
                    Text(
                        "Title",
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }
                innerTextField()
            },
            onTextLayout = { layoutState.titleLayoutResult = it }
        )

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = with(density) { DpOffset(menuOffset.x.toDp(), 0.dp) }
        ) {
            DropdownMenuItem(
                text = { Text(if (titleAnnotation == null) "Add Context" else "Edit Context") },
                onClick = {
                    showContextMenu = false
                    if (titleAnnotation == null) {
                        onCommand(
                            WEditorCommand.StartCreatingAnnotation(
                                range = WSelectionRange(
                                    anchor = WSelectionPoint("W_NOTE_TITLE", 0),
                                    focus = WSelectionPoint("W_NOTE_TITLE", title.length)
                                ),
                                anchorText = title
                            )
                        )
                    } else {
                        onCommand(WEditorCommand.ShowAnnotation(titleAnnotation.id))
                    }
                }
            )
        }
    }
}

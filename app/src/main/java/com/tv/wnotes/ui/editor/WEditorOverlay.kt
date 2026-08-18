package com.tv.wnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import com.tv.wnotes.ui.editor.engine.renderer.WDragPreviewRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WInsertionCaretRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WDragAnimationController
import com.tv.wnotes.ui.editor.engine.state.*
import com.tv.wnotes.ui.editor.engine.model.SpanType
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand

/**
 * Separate layer responsible for Cursor, Selection, Handles, Drag Previews, etc.
 * Initially renders nothing.
 */
@Composable
fun WEditorOverlay(
    snapshot: WEditorSnapshot,
    layoutState: com.tv.wnotes.ui.editor.engine.logic.WLayoutProvider,
    animController: WDragAnimationController,
    onCommand: (WEditorCommand) -> Unit,
    contentRelativeOffset: () -> Offset,
    modifier: Modifier = Modifier
) {
    val isDragging = snapshot.drag.phase != WDragPhase.IDLE

    Box(modifier = modifier.fillMaxSize()) {

        // Drag Preview
        WDragPreviewRenderer(
            previewState = snapshot.drag.preview,
            snapshot = snapshot,
            animController = animController,
            modifier = Modifier.fillMaxSize()
        )

        // Multi-Selection Toolbar (Redesigned)
        if (snapshot.selection.isActive && !isDragging) {
            Box(modifier = Modifier.fillMaxSize()) {
                MultiSelectionToolbar(
                    snapshot = snapshot,
                    layoutState = layoutState,
                    onCommand = onCommand,
                    modifier = Modifier.imePadding()
                )
            }
        }

        // Annotation Input Popup
        if (snapshot.isCreatingAnnotation) {
            AnnotationInputPopup(
                snapshot = snapshot,
                onCommand = onCommand,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // View Annotation Popup
        snapshot.activeAnnotationId?.let { id ->
            val annotation = snapshot.annotations.find { it.id == id }
            if (annotation != null) {
                val geometry = layoutState.getAnnotationGeometry(id)
                if (geometry != null) {
                    val editorOffset = contentRelativeOffset()
                    
                    // Physical screen position of the annotated text (relative to overlay root)
                    val targetX = geometry.boundingRect.center.x + editorOffset.x
                    val targetY = geometry.boundingRect.top + editorOffset.y
                    
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                // Position popup above the text with a 8dp gap
                                // Clamp horizontally to keep it on screen
                                val popupWidth = 280.dp.toPx()
                                val popupHeight = 140.dp.toPx() // Estimate
                                
                                translationX = (targetX - popupWidth / 2)
                                    .coerceIn(16.dp.toPx(), size.width - popupWidth - 16.dp.toPx())
                                
                                val preferredY = targetY - popupHeight - 8.dp.toPx()
                                translationY = if (preferredY < 16.dp.toPx()) {
                                    // If too high, show below the text
                                    geometry.boundingRect.bottom + editorOffset.y + 8.dp.toPx()
                                } else {
                                    preferredY
                                }
                            }
                    ) {
                        WAnnotationPopup(
                            annotation = annotation,
                            onCommand = onCommand
                        )
                    }
                }
            }
        }

        // Add Mode Hint
        if (snapshot.selection.interactionMode == WSelectionInteraction.ADD_RANGE) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "Select another text",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Quick Marker Jump
        if (snapshot.isQuickJumpOpen) {
            WMarkerQuickJump(
                snapshot = snapshot,
                onCommand = onCommand
            )
        }
    }
}

@Composable
private fun MultiSelectionToolbar(
    snapshot: WEditorSnapshot,
    layoutState: com.tv.wnotes.ui.editor.engine.logic.WLayoutProvider,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = snapshot.selection
    var isExpanded by remember { mutableStateOf(false) }
    var rawOffset by remember { mutableStateOf(Offset.Zero) }
    var hasInitializedPosition by remember { mutableStateOf(false) }
    var parentSize by remember { mutableStateOf(Size.Zero) }
    var toolbarSize by remember { mutableStateOf(Size.Zero) }

    // Colors
    val toolbarBg = Color(0xFF121212)
    val contentColor = Color(0xFFD1D1D1)
    val dividerColor = Color(0xFF333333)

    val density = androidx.compose.ui.platform.LocalDensity.current

    // Initial position logic
    LaunchedEffect(state.isActive, snapshot.document.revision) {
        if (state.isActive && !hasInitializedPosition) {
            val bounds = layoutState.getSelectionBounds()
            if (bounds.isNotEmpty()) {
                val topBound = bounds.minByOrNull { it.top }?.top ?: 0f
                val centerX = bounds.map { it.center.x }.average().toFloat()
                
                // Position above the selection
                with(density) {
                    rawOffset = Offset(centerX - 100.dp.toPx(), topBound - 60.dp.toPx())
                }
                hasInitializedPosition = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { parentSize = it.size.toSize() }
    ) {
        Column(
            modifier = Modifier
                .offset {
                    if (!hasInitializedPosition && parentSize.width > 0) {
                        rawOffset = Offset(
                            (parentSize.width - toolbarSize.width) / 2,
                            parentSize.height - toolbarSize.height - 100.dp.toPx()
                        )
                        hasInitializedPosition = true
                    }
                    IntOffset(
                        rawOffset.x.roundToInt().coerceIn(0, (parentSize.width - toolbarSize.width).toInt().coerceAtLeast(0)),
                        rawOffset.y.roundToInt().coerceIn(0, (parentSize.height - toolbarSize.height).toInt().coerceAtLeast(0))
                    )
                }
                .onGloballyPositioned { toolbarSize = it.size.toSize() }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        rawOffset += dragAmount
                    }
                },
            horizontalAlignment = Alignment.End
        ) {
            // Main Toolbar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = toolbarBg,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ToolbarAction(Icons.Default.ContentCut, "Cut", contentColor) { onCommand(WEditorCommand.Cut) }
                    ToolbarSeparator(dividerColor)
                    ToolbarAction(Icons.Default.ContentCopy, "Copy", contentColor) { onCommand(WEditorCommand.Copy) }
                    ToolbarSeparator(dividerColor)
                    ToolbarAction(Icons.Default.ContentPaste, "Paste", contentColor) { onCommand(WEditorCommand.Paste) }
                    ToolbarSeparator(dividerColor)
                    ToolbarTextAction("I", contentColor, italic = true) { onCommand(WEditorCommand.ApplySpan(SpanType.Italic)) }
                    ToolbarSeparator(dividerColor)
                    ToolbarTextAction("B", contentColor, bold = true) { onCommand(WEditorCommand.ApplySpan(SpanType.Bold)) }
                    ToolbarSeparator(dividerColor)
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "More",
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Dropdown Menu (L-Shape)
            if (isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.width(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = toolbarBg,
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        DropdownItem(
                            icon = Icons.Default.Add,
                            label = if (state.interactionMode == WSelectionInteraction.ADD_RANGE) "Done" else "Add Selection",
                            color = contentColor
                        ) {
                            val nextMode = if (state.interactionMode == WSelectionInteraction.NORMAL)
                                WSelectionInteraction.ADD_RANGE
                            else
                                WSelectionInteraction.NORMAL
                            onCommand(WEditorCommand.SetSelectionInteraction(nextMode))
                            isExpanded = false
                        }
                        DropdownItem(
                            icon = Icons.Default.OpenInNew,
                            label = "Move to New Tab",
                            color = contentColor
                        ) {
                            onCommand(WEditorCommand.MakeTabFromSelection)
                            isExpanded = false
                        }
                        val canAnnotate = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.isSingleBlock(state)
                        DropdownItem(
                            icon = Icons.Default.Comment,
                            label = "Add Context",
                            color = contentColor,
                            enabled = canAnnotate
                        ) {
                            val range = state.ranges.firstOrNull()
                            if (range != null) {
                                val normalized = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, snapshot.document.blocks)
                                val anchorText = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.getSelectedText(
                                    state.copy(ranges = listOf(normalized)),
                                    snapshot.document.blocks
                                )
                                onCommand(WEditorCommand.StartCreatingAnnotation(normalized, anchorText))
                            }
                            isExpanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToolbarTextAction(
    text: String,
    tint: Color,
    bold: Boolean = false,
    italic: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            color = tint,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ToolbarSeparator(color: Color) {
    Box(
        modifier = Modifier
            .height(16.dp)
            .width(1.dp)
            .background(color)
    )
}

@Composable
private fun DropdownItem(
    icon: ImageVector,
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color.copy(alpha = alpha),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = alpha),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnnotationInputPopup(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pre-fill if editing
    val editingAnnotation = snapshot.activeAnnotationId?.let { id -> 
        snapshot.annotations.find { it.id == id } 
    }
    var text by remember { mutableStateOf(editingAnnotation?.content ?: "") }
    val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .widthIn(max = 300.dp)
            .padding(16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Add Context",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Enter context...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onCommand(WEditorCommand.CancelCreatingAnnotation) }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (editingAnnotation != null) {
                            onCommand(WEditorCommand.UpdateAnnotation(editingAnnotation.id, text))
                        } else {
                            val range = snapshot.pendingAnnotationRange
                            if (range != null) {
                                onCommand(WEditorCommand.CreateAnnotation(text, range, snapshot.pendingAnnotationAnchorText))
                            }
                        }
                        onCommand(WEditorCommand.CancelCreatingAnnotation)
                        onCommand(WEditorCommand.DismissAnnotation)
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

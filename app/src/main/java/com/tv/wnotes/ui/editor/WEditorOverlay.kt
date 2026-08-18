package com.tv.wnotes.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
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

        // Multi-Selection Toolbar (Stage XX)
        if (snapshot.selection.isActive && !isDragging) {
            MultiSelectionToolbar(
                snapshot = snapshot,
                onCommand = onCommand,
                onAddModeToggle = {
                    val nextMode = if (snapshot.selection.interactionMode == WSelectionInteraction.NORMAL)
                        WSelectionInteraction.ADD_RANGE
                    else
                        WSelectionInteraction.NORMAL
                    onCommand(WEditorCommand.SetSelectionInteraction(nextMode))
                },
                modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(bottom = 16.dp)
            )
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
    }
}

@Composable
private fun MultiSelectionToolbar(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    onAddModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = snapshot.selection
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, // Simplified from surfaceColorAtElevation
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isAddMode = state.interactionMode == WSelectionInteraction.ADD_RANGE
            
            TextButton(
                onClick = onAddModeToggle,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Text(
                    text = if (isAddMode) "Done" else "+ Selection",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (!isAddMode) {
                VerticalDivider(modifier = Modifier.height(16.dp))
                
                IconButton(
                    onClick = { onCommand(WEditorCommand.Cut) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onCommand(WEditorCommand.Copy) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onCommand(WEditorCommand.Paste) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onCommand(WEditorCommand.SelectAll) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select All", modifier = Modifier.size(18.dp))
                }

                val canAnnotate = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.isSingleBlock(state)
                TextButton(
                    onClick = { 
                        val range = state.ranges.firstOrNull()
                        if (range != null) {
                            val normalized = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, snapshot.document.blocks)
                            val anchorText = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.getSelectedText(
                                state.copy(ranges = listOf(normalized)),
                                snapshot.document.blocks
                            )
                            onCommand(WEditorCommand.StartCreatingAnnotation(normalized, anchorText))
                        }
                    },
                    enabled = canAnnotate,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text("Context", style = MaterialTheme.typography.labelLarge, color = if (canAnnotate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }

                VerticalDivider(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { onCommand(WEditorCommand.MakeTabFromSelection) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text("Make Tab", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }

                VerticalDivider(modifier = Modifier.height(16.dp))
                
                IconButton(
                    onClick = { onCommand(WEditorCommand.ApplySpan(SpanType.Bold)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("B", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(
                    onClick = { onCommand(WEditorCommand.ApplySpan(SpanType.Italic)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("I", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                }
                IconButton(
                    onClick = { onCommand(WEditorCommand.ApplySpan(SpanType.Highlight)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("H", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
                
                VerticalDivider(modifier = Modifier.height(16.dp))
                
                IconButton(
                    onClick = { 
                        // We need a specific Delete Selection command
                        onCommand(WEditorCommand.DeleteBackward) // This usually handles selection if active
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (state.ranges.size > 1) {
                VerticalDivider(modifier = Modifier.height(16.dp))
                Text(
                    text = "${state.ranges.size} Selected",
                    style = MaterialTheme.typography.labelSmall
                )
            }
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

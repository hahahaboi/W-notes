package com.tv.wnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.data.local.entity.MarkerType
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.WMarkerManager
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot

@Composable
fun WMarkerQuickJump(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val allMarkers = remember(snapshot.markers, snapshot.document.blocks) {
        WMarkerManager.sortMarkers(snapshot.markers, snapshot.document.blocks)
    }
    
    val filteredMarkers = remember(allMarkers, query) {
        if (query.isBlank()) allMarkers
        else allMarkers.filter { 
            it.label.contains(query, ignoreCase = true) || 
            it.anchorText.contains(query, ignoreCase = true) 
        }
    }

    var selectedIndex by remember(filteredMarkers) { 
        mutableIntStateOf(if (filteredMarkers.isNotEmpty()) 0 else -1) 
    }
    
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Auto-scroll list when arrow-key navigating
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != -1) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = { onCommand(WEditorCommand.CloseQuickJump) }) {
        Surface(
            modifier = modifier
                .width(400.dp)
                .heightIn(max = 500.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (filteredMarkers.isNotEmpty()) {
                                    selectedIndex = (selectedIndex + 1).coerceAtMost(filteredMarkers.lastIndex)
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (filteredMarkers.isNotEmpty()) {
                                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                }
                                true
                            }
                            Key.Enter -> {
                                if (selectedIndex != -1) {
                                    onCommand(WEditorCommand.NavigateToMarker(filteredMarkers[selectedIndex].id))
                                    onCommand(WEditorCommand.CloseQuickJump)
                                }
                                true
                            }
                            Key.Escape -> {
                                onCommand(WEditorCommand.CloseQuickJump)
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Search Header
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Jump to marker...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filteredMarkers.isNotEmpty()) {
                            Text(
                                text = "${if (selectedIndex == -1) 0 else selectedIndex + 1} of ${filteredMarkers.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                if (filteredMarkers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allMarkers.isEmpty()) "No markers defined" else "No matches found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(filteredMarkers, key = { _, m -> m.id }) { index, marker ->
                            QuickJumpItem(
                                marker = marker,
                                isSelected = index == selectedIndex
                            ) {
                                onCommand(WEditorCommand.NavigateToMarker(marker.id))
                                onCommand(WEditorCommand.CloseQuickJump)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickJumpItem(
    marker: TextMarker,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(getMarkerTypeColor(marker.type, MaterialTheme.colorScheme))
            )
            
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val snippet = marker.anchorText.take(60).replace("\n", " ")
                Text(
                    text = if (marker.anchorText.length > 60) "$snippet..." else snippet,
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

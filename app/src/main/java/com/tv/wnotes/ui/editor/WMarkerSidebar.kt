package com.tv.wnotes.ui.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.data.local.entity.MarkerType
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.logic.WMarkerManager
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot

@Composable
fun WMarkerSidebar(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val allMarkers = remember(snapshot.markers, snapshot.document.blocks) {
        WMarkerManager.sortMarkers(snapshot.markers, snapshot.document.blocks)
    }
    
    var currentFilter by remember { mutableStateOf<MarkerType?>(null) }
    
    val filteredMarkers = remember(allMarkers, currentFilter) {
        if (currentFilter == null) allMarkers
        else allMarkers.filter { it.type == currentFilter }
    }

    val groupedMarkers = remember(filteredMarkers, currentFilter) {
        if (currentFilter != null) listOf(null to filteredMarkers)
        else {
            val groups = filteredMarkers.groupBy { it.type }
            listOf(MarkerType.IMPORTANT, MarkerType.TODO, MarkerType.REFERENCE, MarkerType.DEFAULT).mapNotNull { type ->
                groups[type]?.let { type to it }
            }
        }
    }

    val selection = snapshot.selection
    val hasSelection = selection.isActive && !WSelectionManager.isCollapsed(selection)
    
    var markerToRename by remember { mutableStateOf<TextMarker?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(110.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { contentDescription = "Text Marker Sidebar" }
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Markers",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    val selectedText = WSelectionManager.getSelectedText(selection, snapshot.document.blocks)
                    val label = WSelectionManager.generateTabTitle(selectedText, 25)
                    onCommand(WEditorCommand.CreateMarker(label, selection.ranges.first(), selectedText))
                },
                enabled = hasSelection,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Create marker from selected text" }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = currentFilter == null,
                onClick = { currentFilter = null },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
            )
            MarkerType.entries.forEach { type ->
                FilterChip(
                    selected = currentFilter == type,
                    onClick = { currentFilter = type },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        if (allMarkers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No markers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Select text and tap + to add.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        } else if (filteredMarkers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                groupedMarkers.forEach { (type, markersInGroup) ->
                    if (currentFilter == null && type != null) {
                        item(key = "header_$type") {
                            Text(
                                text = type.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = getMarkerTypeColor(type, MaterialTheme.colorScheme).copy(alpha = 0.8f),
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    
                    items(markersInGroup, key = { it.id }) { marker ->
                        val isNavigating = snapshot.navigationHighlightRange?.let {
                            it.anchor.blockId == marker.startBlockId && it.anchor.offset == marker.startOffset &&
                            it.focus.blockId == marker.endBlockId && it.focus.offset == marker.endOffset
                        } ?: false
                        
                        val isActive = snapshot.activeMarkerId == marker.id

                        MarkerPill(
                            marker = marker,
                            isActive = isActive || isNavigating,
                            onClick = { onCommand(WEditorCommand.NavigateToMarker(marker.id)) },
                            onRename = { markerToRename = marker },
                            onChangeType = { onCommand(WEditorCommand.UpdateMarkerType(marker.id, it)) },
                            onDelete = { onCommand(WEditorCommand.DeleteMarker(marker.id)) }
                        )
                    }
                }
            }
        }
    }
    
    markerToRename?.let { marker ->
        RenameMarkerDialog(
            initialLabel = marker.label,
            onDismiss = { markerToRename = null },
            onConfirm = { newLabel ->
                onCommand(WEditorCommand.UpdateMarkerLabel(marker.id, newLabel))
                markerToRename = null
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MarkerPill(
    marker: TextMarker,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onChangeType: (MarkerType) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showTypeSubMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .semantics { contentDescription = "Navigate to marker: ${marker.label}" }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Indicator
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(getMarkerTypeColor(marker.type, MaterialTheme.colorScheme))
                )
                
                Spacer(Modifier.width(6.dp))

                Text(
                    text = marker.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer 
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (isActive) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showMenu = false
                    onRename()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Change Type") },
                onClick = {
                    showTypeSubMenu = true
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        DropdownMenu(
            expanded = showTypeSubMenu,
            onDismissRequest = { showTypeSubMenu = false }
        ) {
            MarkerType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onChangeType(type)
                        showTypeSubMenu = false
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(getMarkerTypeColor(type, MaterialTheme.colorScheme))
                        )
                    }
                )
            }
        }
    }
}

fun getMarkerTypeColor(type: MarkerType, colorScheme: ColorScheme): Color {
    return when (type) {
        MarkerType.DEFAULT -> colorScheme.outline
        MarkerType.IMPORTANT -> Color(0xFFD4AF37) // Gold
        MarkerType.TODO -> colorScheme.primary
        MarkerType.REFERENCE -> Color(0xFF9C27B0) // Purple
    }
}

@Composable
private fun RenameMarkerDialog(
    initialLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialLabel) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Marker") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text("Label") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

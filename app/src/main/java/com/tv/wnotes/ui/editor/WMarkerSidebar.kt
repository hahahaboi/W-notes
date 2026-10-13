package com.tv.wnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tv.wnotes.data.local.entity.MarkerType
import com.tv.wnotes.data.local.entity.TextMarker
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.WMarkerManager
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange

enum class SidebarTab {
    MARKERS, CONTEXT
}

@Composable
fun WMarkerSidebar(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SidebarTab.MARKERS) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val searchMatches = remember(snapshot.document.blocks, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else findMatches(snapshot.document.blocks, searchQuery)
    }

    val safeMatchIndex = if (searchMatches.isNotEmpty()) currentMatchIndex.coerceIn(0, searchMatches.lastIndex) else -1
    val activeMatch = if (safeMatchIndex != -1) searchMatches[safeMatchIndex] else null

    LaunchedEffect(searchMatches, safeMatchIndex) {
        if (searchQuery.isBlank()) {
            onCommand(WEditorCommand.SetSearchHighlights(emptyList(), null))
        } else {
            onCommand(WEditorCommand.SetSearchHighlights(searchMatches, activeMatch))
        }
    }

    LaunchedEffect(searchQuery) {
        currentMatchIndex = 0
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(160.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { contentDescription = "Finder Sidebar" }
    ) {
        // Header (Renamed from "Markers" to "Finder")
        Text(
            text = "Finder",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Tab Navigation
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.height(32.dp)
        ) {
            SidebarTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = if (tab == SidebarTab.MARKERS) "Finder" else "Context",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SidebarTab.MARKERS -> FinderTabContent(
                    snapshot = snapshot,
                    onCommand = onCommand,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchMatches = searchMatches,
                    currentMatchIndex = safeMatchIndex,
                    onPreviousMatch = {
                        if (searchMatches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                        }
                    },
                    onNextMatch = {
                        if (searchMatches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                        }
                    }
                )
                SidebarTab.CONTEXT -> ContextTabContent(snapshot, onCommand)
            }
        }
    }
}

internal fun findMatches(blocks: List<WBlock>, query: String): List<WSelectionRange> {
    if (query.isBlank()) return emptyList()
    val results = mutableListOf<WSelectionRange>()
    val lowerQuery = query.lowercase()
    for (block in blocks) {
        if (block is WTextContainer) {
            val text = block.text
            val lowerText = text.lowercase()
            var index = lowerText.indexOf(lowerQuery)
            while (index != -1) {
                val endIndex = index + query.length
                results.add(
                    WSelectionRange(
                        anchor = WSelectionPoint(block.id, index),
                        focus = WSelectionPoint(block.id, endIndex)
                    )
                )
                index = lowerText.indexOf(lowerQuery, index + 1)
            }
        }
    }
    return results
}

@Composable
private fun FinderTabContent(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchMatches: List<WSelectionRange>,
    currentMatchIndex: Int,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit
) {
    val allMarkers = remember(snapshot.markers, snapshot.document.blocks) {
        WMarkerManager.sortMarkers(snapshot.markers, snapshot.document.blocks)
    }

    val selection = snapshot.selection
    val hasSelection = selection.isActive && !WSelectionManager.isCollapsed(selection)
    var markerToRename by remember { mutableStateOf<TextMarker?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Find...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall,
            shape = RoundedCornerShape(8.dp)
        )

        if (searchQuery.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchMatches.isEmpty()) "No matches" else "${currentMatchIndex + 1} of ${searchMatches.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (searchMatches.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searchMatches.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPreviousMatch,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onNextMatch,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Markers",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    val selectedText = WSelectionManager.getSelectedText(selection, snapshot.document.blocks)
                    val label = WSelectionManager.generateLabel(selectedText, 25)
                    onCommand(WEditorCommand.CreateMarker(label, selection.ranges.first(), selectedText))
                },
                enabled = hasSelection,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create marker",
                    modifier = Modifier.size(20.dp),
                    tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        if (allMarkers.isEmpty()) {
            EmptyState("No markers yet", "Select text and tap + to add.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(allMarkers, key = { it.id }) { marker ->
                    val isNavigating = snapshot.navigationHighlightRange?.let {
                        (it.anchor.blockId == marker.startBlockId && it.anchor.offset == marker.startOffset &&
                        it.focus.blockId == marker.endBlockId && it.focus.offset == marker.endOffset)
                    } ?: false
                    
                    val isActive = snapshot.activeMarkerId == marker.id

                    SidebarMarkerPill(
                        marker = marker,
                        isActive = isActive || isNavigating,
                        onClick = { onCommand(WEditorCommand.NavigateToMarker(marker.id)) },
                        onRename = { markerToRename = marker },
                        onChangeType = { type: MarkerType -> onCommand(WEditorCommand.UpdateMarkerType(marker.id, type)) }
                    ) {
                        onCommand(WEditorCommand.DeleteMarker(marker.id))
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

@Composable
private fun ContextTabContent(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit
) {
    val annotations = snapshot.annotations
    
    if (annotations.isEmpty()) {
        EmptyState("No context added yet", "Use 'Add Context' from the selection toolbar.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(annotations, key = { it.id }) { annotation ->
                val isActive = snapshot.activeAnnotationId == annotation.id
                
                SidebarItemPill(
                    title = annotation.anchorText,
                    subtitle = annotation.content,
                    isActive = isActive,
                    onClick = {
                        val point = WSelectionPoint(annotation.blockId, annotation.startOffset)
                        val range = WSelectionRange(point, WSelectionPoint(annotation.blockId, annotation.endOffset))
                        onCommand(WEditorCommand.MoveCursor(point))
                        onCommand(WEditorCommand.SetNavigationHighlight(range))
                        onCommand(WEditorCommand.ShowAnnotation(annotation.id))
                    },
                    icon = Icons.Default.ChatBubbleOutline
                )
            }
        }
    }
}

@Composable
private fun SidebarItemPill(
    title: String,
    subtitle: String? = null,
    isActive: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer 
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SidebarMarkerPill(
    marker: TextMarker,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onChangeType: (MarkerType) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(value = false) }
    var showTypeSubMenu by remember { mutableStateOf(value = false) }

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
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
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
                                .clip(CircleShape)
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
        MarkerType.IMPORTANT -> colorScheme.onSurface
        MarkerType.TODO -> colorScheme.primary
        MarkerType.REFERENCE -> colorScheme.onSurfaceVariant
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

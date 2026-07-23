package com.tv.wnotes.ui.paths

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathDetailScreen(
    pathId: Long,
    pathName: String,
    linkPathViewModel: LinkPathViewModel,
    folderViewModel: FolderViewModel,
    noteViewModel: NoteViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val databaseNotes by remember(pathId) { linkPathViewModel.getNotesInPath(pathId) }.collectAsState(initial = emptyList())
    var editableNotes by remember { mutableStateOf(emptyList<Note>()) }
    
    LaunchedEffect(databaseNotes) {
        editableNotes = databaseNotes
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredNotes = remember(editableNotes, searchQuery) {
        if (searchQuery.isBlank()) editableNotes
        else editableNotes.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.content.contains(searchQuery, ignoreCase = true) 
        }
    }

    var showEditNotesDialog by remember { mutableStateOf(false) }
    val allNotes by noteViewModel.allNotes.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val density = LocalDensity.current

    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var deltaX by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pathName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "${editableNotes.size} Notes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditNotesDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Notes")
                    }
                    TextButton(onClick = {
                        linkPathViewModel.updateOrder(pathId, editableNotes)
                    }) {
                        Text("Save Order")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search in path...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isEmpty()) "No notes in this path" else "No matches found")
                }
            } else {
                if (searchQuery.isEmpty()) {
                    Text(
                        "Hold card to drag and reorder sequence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(filteredNotes, key = { _, note -> note.id }) { index, note ->
                        val isDragging = draggingItemIndex == index
                        val zIndex = if (isDragging) 1f else 0f
                        val translationX = if (isDragging) deltaX else 0f
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .zIndex(zIndex)
                                .graphicsLayer { this.translationX = translationX }
                                .pointerInput(filteredNotes, searchQuery) {
                                    if (searchQuery.isEmpty()) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingItemIndex = index },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                deltaX += dragAmount.x
                                                
                                                val itemWidthPx = with(density) { 200.dp.toPx() }
                                                val targetIndex = if (deltaX > itemWidthPx / 2) index + 1 else if (deltaX < -itemWidthPx / 2) index - 1 else index

                                                if (targetIndex in editableNotes.indices && targetIndex != index) {
                                                    val newList = editableNotes.toMutableList()
                                                    val movedNote = newList.removeAt(index)
                                                    newList.add(targetIndex, movedNote)
                                                    editableNotes = newList
                                                    draggingItemIndex = targetIndex
                                                    deltaX = 0f
                                                }
                                            },
                                            onDragEnd = { draggingItemIndex = null; deltaX = 0f },
                                            onDragCancel = { draggingItemIndex = null; deltaX = 0f }
                                        )
                                    }
                                }
                        ) {
                            PathNoteCard(
                                note = note,
                                folderName = folderNames[note.folderId],
                                onClick = { onNoteClick(note.id) },
                                isDragging = isDragging
                            )
                            
                            if (index < filteredNotes.size - 1) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 8.dp).size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEditNotesDialog) {
            val selectedNotes = remember { mutableStateListOf<Long>().apply { 
                addAll(editableNotes.map { it.id })
            } }

            AlertDialog(
                onDismissRequest = { showEditNotesDialog = false },
                title = { Text("Edit Stapled Notes") },
                text = {
                    Column {
                        Text("Select notes to staple together:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.height(300.dp)) {
                            LazyColumn {
                                items(allNotes) { note ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            if (selectedNotes.contains(note.id)) selectedNotes.remove(note.id)
                                            else selectedNotes.add(note.id)
                                        }.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = selectedNotes.contains(note.id), onCheckedChange = null)
                                        Text(note.title.ifEmpty { "Untitled" }, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newNoteList = allNotes.filter { selectedNotes.contains(it.id) }
                        editableNotes = newNoteList
                        linkPathViewModel.updateOrder(pathId, newNoteList)
                        showEditNotesDialog = false
                    }) { Text("Update") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditNotesDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PathNoteCard(
    note: Note,
    folderName: String?,
    onClick: () -> Unit,
    isDragging: Boolean
) {
    val elevation by animateDpAsState(if (isDragging) 12.dp else 4.dp, label = "elev")
    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")

    Card(
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(1f)
            .scale(scale)
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Text(
                text = note.title.ifEmpty { "Untitled" },
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                fontSize = 11.sp,
                maxLines = 4,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            folderName?.let {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "📁 $it",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

package com.tv.wnotes.ui.components

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.pref.PreferenceManager
import com.tv.wnotes.ui.components.MultiSelectFloatingActions
import com.tv.wnotes.ui.components.RenameNoteDialog
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteWorkspaceScreen(
    sectionKey: String,
    title: String,
    notes: List<Note>,
    folderNames: Map<Long, String>,
    pathNames: Map<Long, String>,
    emptyMessage: String,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    noteViewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onOpenDrawer: () -> Unit,
    isTrashSection: Boolean = false
) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val sortName by preferenceManager.sectionSort(sectionKey).collectAsState(initial = SectionSortOption.RECENTLY_EDITED.name)
    val sortOption = SectionSortOption.fromName(sortName)
    val scope = rememberCoroutineScope()

    val selectedIds by noteViewModel.selectedNoteIds.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showPathSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Note?>(null) }
    var searchQuery by rememberSaveable(sectionKey) { mutableStateOf("") }
    var contextMenuNoteId by remember { mutableStateOf<Long?>(null) }
    val gridState = rememberSaveable(sectionKey, saver = LazyGridState.Saver) { LazyGridState() }

    LaunchedEffect(selectedIds) {
        if (selectedIds.size > 1) {
            contextMenuNoteId = null
        }
    }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.content.contains(searchQuery, ignoreCase = true) 
        }
    }

    val sortedNotes = remember(filteredNotes, sortOption, folderNames) {
        sortNotes(filteredNotes, sortOption) { folderId -> folderId?.let(folderNames::get) }
    }
    
    val allNotesList by noteViewModel.allNotes.collectAsState()
    val selectedNotes = remember(allNotesList, selectedIds) { 
        allNotesList.filter { it.id in selectedIds } 
    }
    val folders by folderViewModel.allFolders.collectAsState()
    val paths by linkPathViewModel.allPaths.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedIds.isEmpty()) title else "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        if (selectedIds.isEmpty()) {
                            Text(
                                text = "${sortedNotes.size} Notes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedIds.isNotEmpty()) {
                            noteViewModel.clearSelection()
                        } else {
                            onOpenDrawer()
                        }
                    }) {
                        if (selectedIds.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        } else {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                },
                actions = {
                    if (selectedIds.isEmpty()) {
                        TextButton(onClick = { showSortMenu = true }) {
                            Text("Sort ▼")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SectionSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        scope.launch { preferenceManager.saveSectionSort(sectionKey, option.name) }
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = { noteViewModel.clearSelection() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (notes.isNotEmpty() || searchQuery.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search in $title...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }

                if (sortedNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (searchQuery.isEmpty()) emptyMessage else "No matches found in this section")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedNotes, key = { it.id }) { note ->
                            Box {
                                SwipeSelectableNoteCard(
                                    note = note,
                                    folderName = note.folderId?.let(folderNames::get),
                                    pathName = pathNames[note.id],
                                    selected = note.id in selectedIds,
                                    isSelectionMode = selectedIds.isNotEmpty(),
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) noteViewModel.toggleSelection(note.id)
                                        else onNoteClick(note.id)
                                    },
                                    onLongPress = { 
                                        if (selectedIds.isEmpty()) {
                                            noteViewModel.addToSelection(note.id)
                                            contextMenuNoteId = note.id
                                        } else {
                                            noteViewModel.toggleSelection(note.id)
                                        }
                                    },
                                    onSwipeRight = { noteViewModel.toggleStar(note) },
                                    onSwipeLeft = { if (isTrashSection) noteViewModel.deletePermanently(note) else noteViewModel.moveToTrash(note) }
                                )
                                
                                DropdownMenu(
                                    expanded = contextMenuNoteId == note.id,
                                    onDismissRequest = { contextMenuNoteId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (note.isStarred) "Unstar" else "Star") },
                                        onClick = {
                                            noteViewModel.toggleStar(note)
                                            contextMenuNoteId = null
                                            noteViewModel.clearSelection()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                        onClick = {
                                            noteViewModel.togglePin(note)
                                            contextMenuNoteId = null
                                            noteViewModel.clearSelection()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            showRenameDialog = note
                                            contextMenuNoteId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        onClick = {
                                            noteViewModel.duplicate(note)
                                            contextMenuNoteId = null
                                            noteViewModel.clearSelection()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add to Folder") },
                                        onClick = {
                                            showFolderSheet = true
                                            contextMenuNoteId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Staple") },
                                        onClick = {
                                            showPathSheet = true
                                            contextMenuNoteId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = {
                                            shareNotes(context, listOf(note))
                                            contextMenuNoteId = null
                                            noteViewModel.clearSelection()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (note.isDeleted) "Delete Permanently" else "Move to Trash") },
                                        onClick = {
                                            if (note.isDeleted) noteViewModel.deletePermanently(note) else noteViewModel.moveToTrash(note)
                                            contextMenuNoteId = null
                                            noteViewModel.clearSelection()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            MultiSelectFloatingActions(
                visible = selectedIds.isNotEmpty(),
                onStar = { 
                    noteViewModel.starNotes(selectedNotes)
                    noteViewModel.clearSelection()
                },
                onTrash = {
                    if (isTrashSection) noteViewModel.deletePermanently(selectedNotes)
                    else noteViewModel.moveNotesToTrash(selectedNotes)
                    noteViewModel.clearSelection()
                },
                onShare = {
                    shareNotes(context, selectedNotes)
                    noteViewModel.clearSelection()
                },
                onFolder = { showFolderSheet = true },
                onPath = { showPathSheet = true },
                onPin = {
                    selectedNotes.forEach { noteViewModel.togglePin(it) }
                    noteViewModel.clearSelection()
                },
                onDuplicate = {
                    selectedNotes.forEach { noteViewModel.duplicate(it) }
                    noteViewModel.clearSelection()
                }
            )
        }
    }

    if (showFolderSheet) {
        FolderAssignmentSheet(
            folders = folders,
            onDismiss = { showFolderSheet = false },
            onAssign = { 
                noteViewModel.moveNotesToFolder(selectedNotes, it)
                noteViewModel.clearSelection() 
            },
            onCreateFolder = { name, onCreated -> folderViewModel.addFolder(name, null, onCreated) }
        )
    }

    if (showPathSheet) {
        StapleAssignmentSheet(
            paths = paths,
            onDismiss = { showPathSheet = false },
            onAssign = { pId ->
                if (pId == null) {
                    selectedIds.forEach { linkPathViewModel.unstapleNote(it) }
                } else {
                    linkPathViewModel.stapleNotes(selectedIds.toList(), pId)
                }
                noteViewModel.clearSelection()
            },
            onCreatePath = { name, onCreated -> 
                linkPathViewModel.createStaple(name, selectedNotes, onCreated)
                noteViewModel.clearSelection()
            }
        )
    }

    if (showRenameDialog != null) {
        RenameNoteDialog(
            initialTitle = showRenameDialog!!.title,
            onDismiss = { showRenameDialog = null },
            onRename = { newTitle ->
                noteViewModel.rename(showRenameDialog!!, newTitle)
                showRenameDialog = null
                noteViewModel.clearSelection()
            }
        )
    }
}

private fun shareNotes(context: android.content.Context, notes: List<Note>) {
    if (notes.isEmpty()) return
    val text = notes.joinToString("\n---\n") { "${it.title}\n${it.content}" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Notes"))
}

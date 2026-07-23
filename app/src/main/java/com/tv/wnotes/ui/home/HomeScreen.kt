package com.tv.wnotes.ui.home

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.pref.PreferenceManager
import com.tv.wnotes.ui.components.*
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onNavigateToEditor: (Long?) -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToLinkPaths: () -> Unit,
    onNavigateToStarred: () -> Unit,
    onNavigateToAllNotes: () -> Unit,
    onNavigateToUncategorized: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val allNotesList by viewModel.allNotes.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedIds by viewModel.selectedNoteIds.collectAsState()

    val selectedNotes = remember(allNotesList, selectedIds) { 
        allNotesList.filter { it.id in selectedIds } 
    }

    val preferenceManager = remember(context) { PreferenceManager(context) }
    val sortName by preferenceManager.sectionSort("home").collectAsState(initial = SectionSortOption.RECENTLY_EDITED.name)
    val sortOption = SectionSortOption.fromName(sortName)
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuNoteId by remember { mutableStateOf<Long?>(null) }
    var showRenameDialog by remember { mutableStateOf<Note?>(null) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showPathSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } }

    val displayNotes = remember(allNotesList, searchResults, searchQuery, sortOption, folderNames) {
        val baseList = if (searchQuery.isNotEmpty()) searchResults else allNotesList
        sortNotes(baseList, sortOption) { folderId -> folderId?.let(folderNames::get) }
    }

    LaunchedEffect(selectedIds) {
        if (selectedIds.size > 1) contextMenuNoteId = null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = if (selectedIds.isEmpty()) "W Notes" else "${selectedIds.size} selected",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedIds.isNotEmpty()) viewModel.clearSelection() else onOpenDrawer()
                    }) {
                        Icon(if (selectedIds.isNotEmpty()) Icons.Default.Add else Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "Recent Notes" else "Search Results",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box {
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
                                        scope.launch {
                                            preferenceManager.saveSectionSort("home", option.name)
                                        }
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (displayNotes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isEmpty()) 
                                    "No notes yet. Tap the + button to create your first note." 
                                    else "No results found.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayNotes, key = { it.id }) { note ->
                                Box {
                                        SwipeSelectableNoteCard(
                                            note = note,
                                            folderName = folderNames[note.folderId],
                                            pathName = pathNames[note.id],
                                            selected = note.id in selectedIds,
                                            isSelectionMode = selectedIds.isNotEmpty(),
                                            onClick = {
                                                if (selectedIds.isNotEmpty()) viewModel.toggleSelection(note.id)
                                                else onNavigateToEditor(note.id)
                                            },
                                            onLongPress = {
                                                viewModel.addToSelection(note.id)
                                                contextMenuNoteId = note.id
                                            },
                                            onSwipeRight = { viewModel.toggleStar(note) },
                                            onSwipeLeft = { viewModel.moveToTrash(note) }
                                        )

                                    DropdownMenu(
                                        expanded = contextMenuNoteId == note.id,
                                        onDismissRequest = { contextMenuNoteId = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (note.isStarred) "Unstar" else "Star") },
                                            onClick = {
                                                viewModel.toggleStar(note)
                                                contextMenuNoteId = null
                                                viewModel.clearSelection()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                            onClick = {
                                                viewModel.togglePin(note)
                                                contextMenuNoteId = null
                                                viewModel.clearSelection()
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
                                                viewModel.duplicate(note)
                                                contextMenuNoteId = null
                                                viewModel.clearSelection()
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
                                                viewModel.clearSelection()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Move to Trash") },
                                            onClick = {
                                                viewModel.moveToTrash(note)
                                                contextMenuNoteId = null
                                                viewModel.clearSelection()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MultiSelectFloatingActions(
                visible = selectedIds.size > 1,
                onStar = { 
                    viewModel.starNotes(selectedNotes)
                    viewModel.clearSelection()
                },
                onTrash = {
                    viewModel.moveNotesToTrash(selectedNotes)
                    viewModel.clearSelection()
                },
                onShare = {
                    shareNotes(context, selectedNotes)
                    viewModel.clearSelection()
                },
                onFolder = { showFolderSheet = true },
                onPath = { showPathSheet = true },
                onPin = {
                    selectedNotes.forEach { viewModel.togglePin(it) }
                    viewModel.clearSelection()
                },
                onDuplicate = {
                    selectedNotes.forEach { viewModel.duplicate(it) }
                    viewModel.clearSelection()
                }
            )
        }
    }

    if (showFolderSheet) {
        FolderAssignmentSheet(
            folders = folders,
            onDismiss = { showFolderSheet = false },
            onAssign = { 
                viewModel.moveNotesToFolder(selectedNotes, it)
                viewModel.clearSelection() 
            },
            onCreateFolder = { name, onCreated -> folderViewModel.addFolder(name, null, onCreated) }
        )
    }

    if (showPathSheet) {
        StapleAssignmentSheet(
            paths = linkPathViewModel.allPaths.collectAsState().value,
            onDismiss = { showPathSheet = false },
            onAssign = { pId ->
                if (pId == null) {
                    selectedIds.forEach { linkPathViewModel.unstapleNote(it) }
                } else {
                    linkPathViewModel.stapleNotes(selectedIds.toList(), pId)
                }
                viewModel.clearSelection()
            },
            onCreatePath = { name, onCreated -> 
                linkPathViewModel.createStaple(name, selectedNotes, onCreated)
                viewModel.clearSelection()
            }
        )
    }

    if (showRenameDialog != null) {
        RenameNoteDialog(
            initialTitle = showRenameDialog!!.title,
            onDismiss = { showRenameDialog = null },
            onRename = { newTitle ->
                viewModel.rename(showRenameDialog!!, newTitle)
                showRenameDialog = null
                viewModel.clearSelection()
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

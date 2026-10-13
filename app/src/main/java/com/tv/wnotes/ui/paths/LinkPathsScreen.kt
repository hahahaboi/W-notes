package com.tv.wnotes.ui.paths

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.util.NoteDisplayUtils
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LinkPathsScreen(
    viewModel: LinkPathViewModel,
    noteViewModel: NoteViewModel,
    onBack: () -> Unit = {},
    onPathClick: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    drawerProgress: Float,
) {
    val paths by viewModel.allPaths.collectAsState()
    var showAddDialog by remember { mutableStateOf(value = false) }
    var newPathName by remember { mutableStateOf("") }
    var showNewNoteInput by remember { mutableStateOf(false) }
    var newNoteTitleForStaple by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPathIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var contextMenuPathId by remember { mutableStateOf<Long?>(null) }
    var showRenameStapleDialog by remember { mutableStateOf<LinkPath?>(null) }

    val filteredPaths = remember(paths, searchQuery) {
        if (searchQuery.isBlank()) paths
        else paths.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val allNotes by noteViewModel.allNotes.collectAsState() 
    val selectedNotes = remember { mutableStateListOf<Long>() }
    val listState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (selectedPathIds.isEmpty()) "Stapled" else "${selectedPathIds.size} selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPathIds.isNotEmpty()) selectedPathIds = emptySet() else onOpenDrawer()
                    }) {
                        if (selectedPathIds.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        } else {
                            com.tv.wnotes.ui.components.WAnimatedIcon(
                                progress = drawerProgress,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    if (selectedPathIds.isNotEmpty()) {
                        IconButton(onClick = {
                            selectedPathIds.forEach { id ->
                                paths.find { it.id == id }?.let { viewModel.deletePath(it) }
                            }
                            selectedPathIds = emptySet()
                        }) {
                            Text("🗑")
                        }
                        TextButton(onClick = { selectedPathIds = emptySet() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Staple")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search stapled notes...") },
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

            if (filteredPaths.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isEmpty()) "Nothing stapled yet" else "No matches found")
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPaths, key = { it.id }) { path ->
                            val isSelected = path.id in selectedPathIds
                            Box {
                                PathPreviewBox(
                                    path = path,
                                    viewModel = viewModel,
                                    onClick = {
                                        if (selectedPathIds.isNotEmpty()) {
                                            selectedPathIds = if (isSelected) selectedPathIds - path.id else selectedPathIds + path.id
                                        } else {
                                            onPathClick(path.id, path.name)
                                        }
                                    },
                                    onDoubleTap = {
                                        if (selectedPathIds.isEmpty()) {
                                        selectedPathIds = selectedPathIds + path.id
                                            contextMenuPathId = path.id
                                        } else {
                                            selectedPathIds = if (isSelected) selectedPathIds - path.id else selectedPathIds + path.id
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedPathIds.isEmpty()) {
                                        selectedPathIds = selectedPathIds + path.id
                                            contextMenuPathId = path.id
                                        } else {
                                            selectedPathIds = if (isSelected) selectedPathIds - path.id else selectedPathIds + path.id
                                        }
                                    },
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                )

                                DropdownMenu(
                                    expanded = contextMenuPathId == path.id,
                                    onDismissRequest = { contextMenuPathId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            showRenameStapleDialog = path
                                            contextMenuPathId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select") },
                                        onClick = {
                                        selectedPathIds = selectedPathIds + path.id
                                            contextMenuPathId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            viewModel.deletePath(path)
                                            contextMenuPathId = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    com.tv.wnotes.ui.components.WScrollbar(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showAddDialog = false
                    showNewNoteInput = false
                    newNoteTitleForStaple = ""
                    selectedNotes.clear()
                    newPathName = ""
                },
                title = { Text("New Staple") },
                text = {
                    Column {
                        TextField(
                            value = newPathName,
                            onValueChange = { newPathName = it },
                            placeholder = { Text("Staple name (e.g. Music Theory)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Notes:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            
                            AnimatedVisibility(
                                visible = showNewNoteInput,
                                enter = expandHorizontally(),
                                exit = shrinkHorizontally()
                            ) {
                                OutlinedTextField(
                                    value = newNoteTitleForStaple,
                                    onValueChange = { newNoteTitleForStaple = it },
                                    placeholder = { Text("Note title") },
                                    modifier = Modifier.width(150.dp).padding(end = 8.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            IconButton(
                                onClick = { showNewNoteInput = !showNewNoteInput },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (showNewNoteInput) Icons.Default.Clear else Icons.Default.Add,
                                    contentDescription = "Add new note",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        val dialogListState = androidx.compose.foundation.lazy.rememberLazyListState()
                        Box(modifier = Modifier.height(200.dp)) {
                            LazyColumn(state = dialogListState) {
                                items(allNotes) { note ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            if (selectedNotes.contains(note.id)) selectedNotes.remove(note.id)
                                            else selectedNotes.add(note.id)
                                        }.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedNotes.contains(note.id),
                                            onCheckedChange = null
                                        )
                                        Text(NoteDisplayUtils.resolveNoteDisplayData(note).displayTitle, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                            com.tv.wnotes.ui.components.WScrollbar(
                                listState = dialogListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newPathName.isNotBlank() && (selectedNotes.isNotEmpty() || newNoteTitleForStaple.isNotBlank())) {
                            val notesToLinkIds = selectedNotes.toMutableList()
                            
                            if (newNoteTitleForStaple.isNotBlank()) {
                                // We need to create the note first. 
                                // Since createNoteAndReturnId is suspend, we wrap this in a coroutine scope.
                                // NoteViewModel already has a launch in addNote, but here we need the ID.
                                scope.launch {
                                    val newNoteId = noteViewModel.createNoteAndReturnId(newNoteTitleForStaple, "")
                                    notesToLinkIds.add(newNoteId)
                                    viewModel.createStapleWithIds(newPathName, notesToLinkIds)
                                    
                                    newPathName = ""
                                    selectedNotes.clear()
                                    newNoteTitleForStaple = ""
                                    showNewNoteInput = false
                                    showAddDialog = false
                                }
                            } else {
                                viewModel.createStapleWithIds(newPathName, notesToLinkIds)
                                newPathName = ""
                                selectedNotes.clear()
                                showAddDialog = false
                            }
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showAddDialog = false
                        showNewNoteInput = false
                        newNoteTitleForStaple = ""
                        selectedNotes.clear()
                        newPathName = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRenameStapleDialog != null) {
            var value by remember { mutableStateOf(showRenameStapleDialog!!.name) }
            AlertDialog(
                onDismissRequest = { showRenameStapleDialog = null },
                title = { Text("Rename Staple") },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = { Text("Staple name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (value.isNotBlank()) {
                            viewModel.renameStaple(showRenameStapleDialog!!, value.trim())
                            showRenameStapleDialog = null
                        }
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameStapleDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PathPreviewBox(
    path: LinkPath,
    viewModel: LinkPathViewModel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    containerColor: Color
) {
    val notes by viewModel.getNotesInPath(path.id).collectAsState(initial = emptyList())
    
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleTap
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = path.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                notes.take(3).forEachIndexed { index, note ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = NoteDisplayUtils.resolveNoteDisplayData(note).displayTitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                    if (index < notes.take(3).size - 1 || notes.size > 3) {
                        Text("📎", fontSize = 12.sp)
                    }
                }
                if (notes.size > 3) {
                    Text("...", fontSize = 12.sp)
                }
            }
        }
    }
}

package com.tv.wnotes.ui.paths

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Menu
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
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LinkPathsScreen(
    viewModel: LinkPathViewModel,
    noteViewModel: NoteViewModel,
    onBack: () -> Unit,
    onPathClick: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val paths by viewModel.allPaths.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newPathName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPathIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var contextMenuPathId by remember { mutableStateOf<Long?>(null) }

    val filteredPaths = remember(paths, searchQuery) {
        if (searchQuery.isBlank()) paths
        else paths.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val allNotes by noteViewModel.allNotes.collectAsState() 
    val selectedNotes = remember { mutableStateListOf<Long>() }

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
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            )

                            DropdownMenu(
                                expanded = contextMenuPathId == path.id,
                                onDismissRequest = { contextMenuPathId = null }
                            ) {
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
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
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
                        Text("Select Notes:", fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.height(200.dp)) {
                            LazyColumn {
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
                                        Text(note.title.ifEmpty { "Untitled" }, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newPathName.isNotBlank() && selectedNotes.isNotEmpty()) {
                            val notesToLink = allNotes.filter { selectedNotes.contains(it.id) }
                            viewModel.createStaple(newPathName, notesToLink)
                            newPathName = ""
                            selectedNotes.clear()
                            showAddDialog = false
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
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
                            text = note.title.ifEmpty { "Untitled" },
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

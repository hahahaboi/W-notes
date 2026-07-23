package com.tv.wnotes.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.tv.wnotes.ui.components.FolderAssignmentSheet
import com.tv.wnotes.ui.components.FolderPreviewCard
import com.tv.wnotes.ui.components.MultiSelectFloatingActions
import com.tv.wnotes.viewmodel.FolderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    parentId: Long? = null,
    viewModel: FolderViewModel,
    onBack: () -> Unit,
    onFolderClick: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    onViewNotes: (Long, String) -> Unit
) {
    val allFolders by viewModel.allFolders.collectAsState()
    
    val folders = remember(allFolders, parentId) {
        allFolders.filter { it.parentFolderId == parentId }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    var contextMenuFolderId by remember { mutableStateOf<Long?>(null) }

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders
        else folders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    var showFolderAssignmentSheet by remember { mutableStateOf(false) }
    val selectedFolders = remember(allFolders, selectedFolderIds) {
        allFolders.filter { it.id in selectedFolderIds }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (selectedFolderIds.isEmpty()) {
                            if (parentId == null) "Folders" else "Subfolders"
                        } else "${selectedFolderIds.size} selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFolderIds.isNotEmpty()) {
                            viewModel.clearSelection()
                        } else if (parentId != null) {
                            onBack()
                        } else {
                            onOpenDrawer()
                        }
                    }) {
                        if (selectedFolderIds.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        } else if (parentId != null) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        } else {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                },
                actions = {
                    if (selectedFolderIds.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search folders...") },
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

            if (filteredFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isEmpty()) "No folders yet" else "No matching folders found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredFolders, key = { it.id }) { folder ->
                        val notes by viewModel.getNotesInFolder(folder.id).collectAsState(initial = emptyList())
                        val isSelected = folder.id in selectedFolderIds

                        Box {
                            FolderPreviewCard(
                                folder = folder,
                                notes = notes,
                                onClick = {
                                    if (selectedFolderIds.isNotEmpty()) {
                                        viewModel.toggleSelection(folder.id)
                                    } else {
                                        onFolderClick(folder.id, folder.name)
                                    }
                                },
                                onLongClick = {
                                    if (selectedFolderIds.isEmpty()) {
                                        viewModel.toggleSelection(folder.id)
                                        contextMenuFolderId = folder.id
                                    } else {
                                        viewModel.toggleSelection(folder.id)
                                    }
                                },
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            )

                            DropdownMenu(
                                expanded = contextMenuFolderId == folder.id,
                                onDismissRequest = { contextMenuFolderId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Notes") },
                                    onClick = {
                                        onViewNotes(folder.id, folder.name)
                                        contextMenuFolderId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        viewModel.toggleSelection(folder.id)
                                        contextMenuFolderId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Folder") },
                                    onClick = {
                                        showFolderAssignmentSheet = true
                                        contextMenuFolderId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        viewModel.deleteFolder(folder)
                                        contextMenuFolderId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        MultiSelectFloatingActions(
            visible = selectedFolderIds.isNotEmpty(),
            onStar = {}, 
            onTrash = {
                selectedFolders.forEach { viewModel.deleteFolder(it) }
                viewModel.clearSelection()
            },
            onShare = {}, 
            onFolder = { showFolderAssignmentSheet = true },
            onPath = null,
            onPin = null,
            onDuplicate = null
        )

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("New Folder") },
                text = {
                    TextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.addFolder(newFolderName, parentId)
                            newFolderName = ""
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

    if (showFolderAssignmentSheet) {
        FolderAssignmentSheet(
            folders = allFolders.filter { f -> 
                f.id !in selectedFolderIds && !isDescendantOfAny(f, selectedFolderIds, allFolders)
            },
            onDismiss = { showFolderAssignmentSheet = false },
            onAssign = { targetId ->
                viewModel.moveFoldersToFolder(selectedFolderIds, targetId)
                viewModel.clearSelection()
                showFolderAssignmentSheet = false
            },
            onCreateFolder = { name, onCreated ->
                viewModel.addFolder(name, parentId, onCreated)
            }
        )
    }
}

private fun isDescendantOfAny(folder: com.tv.wnotes.data.local.entity.Folder, potentialParents: Set<Long>, allFolders: List<com.tv.wnotes.data.local.entity.Folder>): Boolean {
    var current: com.tv.wnotes.data.local.entity.Folder? = folder
    while (current?.parentFolderId != null) {
        if (current.parentFolderId in potentialParents) return true
        current = allFolders.find { it.id == current.parentFolderId }
    }
    return false
}

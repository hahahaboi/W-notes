package com.tv.wnotes.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.ui.components.NoteWorkspaceScreen
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@Composable
fun FolderDetailScreen(
    folderId: Long,
    folderName: String,
    folderViewModel: FolderViewModel,
    noteViewModel: NoteViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onFolderClick: (Long, String) -> Unit,
    onNavigateToGroup: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToEditor: (Long?) -> Unit,
    drawerProgress: Float
) {
    val folderItems by folderViewModel.getItemsInFolder(folderId).collectAsState(initial = emptyList())
    val allFolders by folderViewModel.allFolders.collectAsState()
    val subfolders = remember(allFolders, folderId) {
        allFolders.filter { it.parentFolderId == folderId }
    }
    
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val groupPathInfo by linkPathViewModel.allGroupPathInfo.collectAsState()

    var showAddSubfolderDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var newSubfolderName by remember { mutableStateOf("") }

    NoteWorkspaceScreen(
        sectionKey = "folder_$folderId",
        title = folderName,
        items = folderItems,
        subfolders = subfolders,
        folderNames = remember(folders) { folders.associate { it.id to it.name } },
        pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
        groupPathNames = remember(groupPathInfo) { groupPathInfo.associate { it.groupId to it.pathName } },
        emptyMessage = "This folder is empty.",
        onBack = onBack,
        onNoteClick = onNoteClick,
        onFolderClick = onFolderClick,
        onNavigateToGroup = onNavigateToGroup,
        noteViewModel = noteViewModel,
        folderViewModel = folderViewModel,
        linkPathViewModel = linkPathViewModel,
        onOpenDrawer = onOpenDrawer,
        drawerProgress = drawerProgress,
        onCreateSubfolder = { showAddSubfolderDialog = true },
        onRename = { showRenameFolderDialog = true },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(folderId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    )

    if (showAddSubfolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubfolderDialog = false },
            title = { Text("New Subfolder") },
            text = {
                TextField(
                    value = newSubfolderName,
                    onValueChange = { newSubfolderName = it },
                    placeholder = { Text("Subfolder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSubfolderName.isNotBlank()) {
                            folderViewModel.addFolder(newSubfolderName, folderId)
                            newSubfolderName = ""
                            showAddSubfolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubfolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameFolderDialog) {
        var value by remember { mutableStateOf(folderName) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (value.isNotBlank()) {
                            val folder = allFolders.find { it.id == folderId }
                            if (folder != null) {
                                folderViewModel.renameFolder(folder, value.trim())
                            }
                            showRenameFolderDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

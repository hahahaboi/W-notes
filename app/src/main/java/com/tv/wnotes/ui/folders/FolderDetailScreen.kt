package com.tv.wnotes.ui.folders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.components.FolderPreviewCard
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
    onOpenDrawer: () -> Unit
) {
    val allNotes by folderViewModel.getNotesInFolder(folderId).collectAsState(initial = emptyList())
    val allFolders by folderViewModel.allFolders.collectAsState()
    val subfolders = remember(allFolders, folderId) {
        allFolders.filter { it.parentFolderId == folderId }
    }
    
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (subfolders.isNotEmpty()) {
            Text(
                text = "Subfolders",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subfolders) { folder ->
                    val subfolderNotes by folderViewModel.getNotesInFolder(folder.id).collectAsState(initial = emptyList())
                    FolderPreviewCard(
                        folder = folder,
                        notes = subfolderNotes,
                        onClick = { onFolderClick(folder.id, folder.name) }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        NoteWorkspaceScreen(
            sectionKey = "folder_$folderId",
            title = folderName,
            notes = allNotes,
            folderNames = remember(folders) { folders.associate { it.id to it.name } },
            pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
            emptyMessage = "No notes in this folder.",
            onBack = onBack,
            onNoteClick = onNoteClick,
            noteViewModel = noteViewModel,
            folderViewModel = folderViewModel,
            linkPathViewModel = linkPathViewModel,
            onOpenDrawer = onOpenDrawer
        )
    }
}

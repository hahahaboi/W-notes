package com.tv.wnotes.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tv.wnotes.ui.components.NoteWorkspaceScreen
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@Composable
fun UncategorizedNotesScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val notes by viewModel.uncategorizedNotes.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()

    NoteWorkspaceScreen(
        sectionKey = "uncategorized",
        title = "Uncategorized",
        notes = notes,
        folderNames = remember(folders) { folders.associate { it.id to it.name } },
        pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
        emptyMessage = "No uncategorized notes.",
        onBack = onBack,
        onNoteClick = onNoteClick,
        noteViewModel = viewModel,
        folderViewModel = folderViewModel,
        linkPathViewModel = linkPathViewModel,
        onOpenDrawer = onOpenDrawer
    )
}

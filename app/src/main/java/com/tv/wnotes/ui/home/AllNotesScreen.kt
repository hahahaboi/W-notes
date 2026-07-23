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
fun AllNotesScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val notes by viewModel.allNotes.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()

    NoteWorkspaceScreen(
        sectionKey = "all_notes",
        title = "All Notes",
        notes = notes,
        folderNames = remember(folders) { folders.associate { it.id to it.name } },
        pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
        emptyMessage = "No notes yet. Tap the + button to create your first note.",
        onBack = onBack,
        onNoteClick = onNoteClick,
        noteViewModel = viewModel,
        folderViewModel = folderViewModel,
        linkPathViewModel = linkPathViewModel,
        onOpenDrawer = onOpenDrawer
    )
}

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
fun TrashNotesScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onNavigateToGroup: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    drawerProgress: Float
) {
    val trashItems by viewModel.trashItems.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val groupPathInfo by linkPathViewModel.allGroupPathInfo.collectAsState()

    NoteWorkspaceScreen(
        sectionKey = "trash",
        title = "Trash",
        items = trashItems,
        folderNames = remember(folders) { folders.associate { it.id to it.name } },
        pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
        groupPathNames = remember(groupPathInfo) { groupPathInfo.associate { it.groupId to it.pathName } },
        emptyMessage = "Deleted items appear here.",
        onBack = onBack,
        onNoteClick = onNoteClick,
        onNavigateToGroup = onNavigateToGroup,
        noteViewModel = viewModel,
        folderViewModel = folderViewModel,
        linkPathViewModel = linkPathViewModel,
        onOpenDrawer = onOpenDrawer,
        drawerProgress = drawerProgress,
        isTrashSection = true
    )
}

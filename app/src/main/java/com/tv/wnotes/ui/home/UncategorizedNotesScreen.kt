package com.tv.wnotes.ui.home

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tv.wnotes.ui.components.NoteWorkspaceScreen
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import com.tv.wnotes.viewmodel.UncategorizedFilter

@Composable
fun UncategorizedNotesScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onNavigateToGroup: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToEditor: (Long?) -> Unit,
    drawerProgress: Float
) {
    val items by viewModel.uncategorizedItems.collectAsState()
    val currentFilter by viewModel.currentUncategorizedFilter.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val groupPathInfo by linkPathViewModel.allGroupPathInfo.collectAsState()

    NoteWorkspaceScreen(
        sectionKey = "uncategorized",
        title = "Uncategorized",
        items = items,
        folderNames = remember(folders) { folders.associate { it.id to it.name } },
        pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
        groupPathNames = remember(groupPathInfo) { groupPathInfo.associate { it.groupId to it.pathName } },
        emptyMessage = "No notes found matching this filter.",
        onBack = onBack,
        onNoteClick = onNoteClick,
        onNavigateToGroup = onNavigateToGroup,
        noteViewModel = viewModel,
        folderViewModel = folderViewModel,
        linkPathViewModel = linkPathViewModel,
        onOpenDrawer = onOpenDrawer,
        drawerProgress = drawerProgress,
        filterOptions = remember { UncategorizedFilter.entries.map { it.label } },
        selectedFilterIndex = currentFilter.ordinal,
        onFilterClick = { index ->
            viewModel.setUncategorizedFilter(UncategorizedFilter.entries[index])
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    )
}

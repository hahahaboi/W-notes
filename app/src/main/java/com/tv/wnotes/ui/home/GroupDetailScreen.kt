package com.tv.wnotes.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.ui.components.NoteWorkspaceScreen
import com.tv.wnotes.ui.components.WScrollbar
import com.tv.wnotes.ui.components.WScrollbarOrientation
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: Long,
    groupName: String,
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    drawerProgress: Float
) {
    val groupWithNotes by viewModel.getGroupWithNotesById(groupId).collectAsState(initial = null)
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val groupPathInfo by linkPathViewModel.allGroupPathInfo.collectAsState()

    var isReordering by remember { mutableStateOf(false) }
    var editableNotes by remember { mutableStateOf(emptyList<Note>()) }
    
    LaunchedEffect(groupWithNotes) {
        if (!isReordering) {
            editableNotes = groupWithNotes?.orderedNotes ?: emptyList()
        }
    }

    val density = LocalDensity.current
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var deltaX by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(groupWithNotes?.group?.name ?: groupName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isReordering) {
                        TextButton(onClick = {
                            viewModel.updateGroupOrder(groupId, editableNotes.map { it.id })
                            isReordering = false
                        }) {
                            Text("Save Order")
                        }
                    } else {
                        IconButton(onClick = { isReordering = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Reorder")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isReordering) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Hold card to drag and reorder sequence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )

                val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(editableNotes, key = { _, note -> note.id }) { index, note ->
                            val isDragging = draggingItemIndex == index
                            val zIndex = if (isDragging) 1f else 0f
                            val translationX = if (isDragging) deltaX else 0f
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .zIndex(zIndex)
                                    .graphicsLayer { this.translationX = translationX }
                                    .pointerInput(editableNotes) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingItemIndex = index },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                deltaX += dragAmount.x
                                                
                                                val itemWidthPx = with(density) { 200.dp.toPx() }
                                                val targetIndex = if (deltaX > itemWidthPx / 2) index + 1 else if (deltaX < -itemWidthPx / 2) index - 1 else index

                                                if (targetIndex in editableNotes.indices && targetIndex != index) {
                                                    val newList = editableNotes.toMutableList()
                                                    val movedNote = newList.removeAt(index)
                                                    newList.add(targetIndex, movedNote)
                                                    editableNotes = newList
                                                    draggingItemIndex = targetIndex
                                                    deltaX = 0f
                                                }
                                            },
                                            onDragEnd = { draggingItemIndex = null; deltaX = 0f },
                                            onDragCancel = { draggingItemIndex = null; deltaX = 0f }
                                        )
                                    }
                            ) {
                                com.tv.wnotes.ui.paths.PathNoteCard(
                                    note = note,
                                    folderName = folders.find { it.id == note.folderId }?.name,
                                    onClick = { },
                                    isDragging = isDragging
                                )
                                
                                if (index < editableNotes.size - 1) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 8.dp).size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    WScrollbar(
                        listState = rowState,
                        orientation = WScrollbarOrientation.HORIZONTAL,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }
        } else {
            NoteWorkspaceScreen(
                sectionKey = "group_$groupId",
                title = groupWithNotes?.group?.name ?: groupName,
                items = remember(groupWithNotes) { groupWithNotes?.orderedNotes?.map { HomeItem.NoteItem(it) } ?: emptyList() },
                folderNames = remember(folders) { folders.associate { it.id to it.name } },
                pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } },
                groupPathNames = remember(groupPathInfo) { groupPathInfo.associate { it.groupId to it.pathName } },
                emptyMessage = "No notes in this group.",
                onBack = onBack,
                onNoteClick = onNoteClick,
                noteViewModel = viewModel,
                folderViewModel = folderViewModel,
                linkPathViewModel = linkPathViewModel,
                onOpenDrawer = { /* Not needed here as we have back button */ },
                drawerProgress = drawerProgress
            )
        }
    }
}

package com.tv.wnotes.ui.components

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.pref.PreferenceManager
import com.tv.wnotes.ui.home.HomeItem
import com.tv.wnotes.util.SmartSearchEngine
import com.tv.wnotes.ui.components.MultiSelectFloatingActions
import com.tv.wnotes.ui.components.RenameNoteDialog
import com.tv.wnotes.ui.components.SquareFolderCard
import com.tv.wnotes.ui.components.PinDialog
import com.tv.wnotes.ui.components.PinMode
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import com.tv.wnotes.viewmodel.SelectionState
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteWorkspaceScreen(
    sectionKey: String,
    title: String,
    items: List<HomeItem>,
    folderNames: Map<Long, String>,
    pathNames: Map<Long, String>,
    groupPathNames: Map<Long, String> = emptyMap(),
    emptyMessage: String,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    noteViewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToGroup: ((Long, String) -> Unit)? = null,
    drawerProgress: Float = 0f,
    isTrashSection: Boolean = false,
    filterOptions: List<String> = emptyList(),
    selectedFilterIndex: Int = -1,
    onFilterClick: (Int) -> Unit = {},
    subfolders: List<Folder> = emptyList(),
    onFolderClick: (Long, String) -> Unit = { _, _ -> },
    onCreateSubfolder: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    val sortName by preferenceManager.sectionSort(sectionKey).collectAsState(initial = SectionSortOption.RECENTLY_EDITED.name)
    val sortOption = SectionSortOption.fromName(sortName)
    val scope = rememberCoroutineScope()

    val selectedIds by noteViewModel.selectedNoteIds.collectAsState()
    val selectedGroupIds by noteViewModel.selectedGroupIds.collectAsState()
    val selectionState by noteViewModel.selectionState.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showPathSheet by remember { mutableStateOf(false) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewStapleDialog by remember { mutableStateOf(false) }
    var showGroupRenameDialog by remember { mutableStateOf<Long?>(null) }
    var showRenameDialog by remember { mutableStateOf<Note?>(null) }
    var searchQuery by rememberSaveable(sectionKey) { mutableStateOf("") }
    var contextMenuNoteId by remember { mutableStateOf<Long?>(null) }
    var contextMenuGroupId by remember { mutableStateOf<Long?>(null) }
    var showQuickNoteInput by remember { mutableStateOf(false) }
    var quickNoteTargetId by remember { mutableStateOf<Long?>(null) }
    val gridState = rememberSaveable(sectionKey, saver = LazyGridState.Saver) { LazyGridState() }
    
    var openingFolderId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(selectionState) {
        if (selectionState !is SelectionState.SingleNote) {
            showInfoPanel = false
            showQuickNoteInput = false
            quickNoteTargetId = null
        }
        if (selectionState.totalCount > 1) {
            contextMenuNoteId = null
            contextMenuGroupId = null
        }
    }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { item ->
            when (item) {
                is HomeItem.NoteItem -> {
                    item.note.title.contains(searchQuery, ignoreCase = true) || 
                    item.note.content.contains(searchQuery, ignoreCase = true)
                }
                is HomeItem.GroupItem -> {
                    item.groupWithNotes.group.name.contains(searchQuery, ignoreCase = true) ||
                    item.groupWithNotes.notes.any { 
                        it.title.contains(searchQuery, ignoreCase = true) || 
                        it.content.contains(searchQuery, ignoreCase = true)
                    }
                }
            }
        }
    }

    val sortedItems = remember(filteredItems, sortOption, folderNames) {
        filteredItems.sortedWith(
            compareByDescending<HomeItem> { item ->
                when (item) {
                    is HomeItem.NoteItem -> item.note.isPinned
                    is HomeItem.GroupItem -> item.groupWithNotes.group.isPinned
                }
            }.thenByDescending { it.timestamp }
        )
    }

    // Unpack groups for non-home screens
    val isHomeScreen = sectionKey == "home"
    
    val allNotesList by noteViewModel.allNotes.collectAsState()
    val selectedNotes = remember(allNotesList, selectedIds) { 
        allNotesList.filter { it.id in selectedIds } 
    }
    val folders by folderViewModel.allFolders.collectAsState()
    val folderStats by folderViewModel.folderStats.collectAsState()
    val paths by linkPathViewModel.allPaths.collectAsState()
    val lockedPin by noteViewModel.lockedPin.collectAsState()

    var noteToLock by remember { mutableStateOf<Note?>(null) }
    var showPinSetupForNote by remember { mutableStateOf(false) }
    var showPinEntryForNote by remember { mutableStateOf(false) }
    var showPinReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectionState is SelectionState.None) title else "${selectionState.totalCount} selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        if (selectionState is SelectionState.None) {
                            val totalItems = sortedItems.size + subfolders.size
                            Text(
                                text = "$totalItems Items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionState !is SelectionState.None) {
                            noteViewModel.clearSelection()
                        } else {
                            onOpenDrawer()
                        }
                    }) {
                        if (selectionState !is SelectionState.None) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        } else {
                            WAnimatedIcon(
                                progress = drawerProgress,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    if (selectionState is SelectionState.None) {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            if (sectionKey == "locked") {
                                DropdownMenuItem(
                                    text = { Text("Reset Password") },
                                    onClick = {
                                        showPinReset = true
                                        showMoreMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Sort") },
                                onClick = { 
                                    showSortMenu = true
                                    showMoreMenu = false
                                }
                            )
                            if (onCreateSubfolder != null) {
                                DropdownMenuItem(
                                    text = { Text("New Subfolder") },
                                    onClick = {
                                        onCreateSubfolder()
                                        showMoreMenu = false
                                    }
                                )
                            }
                            if (onRename != null) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        onRename()
                                        showMoreMenu = false
                                    }
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SectionSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        scope.launch { preferenceManager.saveSectionSort(sectionKey, option.name) }
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = { noteViewModel.clearSelection() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = { floatingActionButton?.invoke() }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (items.isNotEmpty() || searchQuery.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search items...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }

                if (filterOptions.isNotEmpty()) {
                    val filterScrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(filterScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filterOptions.forEachIndexed { index, label ->
                                FilterChip(
                                    selected = selectedFilterIndex == index,
                                    onClick = { 
                                        noteViewModel.clearSelection()
                                        onFilterClick(index) 
                                    },
                                    label = { Text(label) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        WScrollbar(
                            scrollState = filterScrollState,
                            orientation = WScrollbarOrientation.HORIZONTAL,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp) // Adjusted for clear space
                        )
                    }
                }

                if (sortedItems.isEmpty() && subfolders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (searchQuery.isEmpty()) emptyMessage else "No matches found in this section")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (subfolders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(subfolders, key = { "folder_${it.id}" }) { folder ->
                                SquareFolderCard(
                                    folder = folder,
                                    noteCount = folderStats[folder.id]?.noteCount ?: 0,
                                    isOpen = openingFolderId == folder.id,
                                    onClick = { 
                                        if (openingFolderId == null) {
                                            openingFolderId = folder.id
                                            scope.launch {
                                                kotlinx.coroutines.delay(600)
                                                onFolderClick(folder.id, folder.name)
                                                openingFolderId = null
                                            }
                                        }
                                    }
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    if (sortedItems.isNotEmpty()) {
                                        Text(
                                            text = "Items",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        sortedItems.forEach { item ->
                            when (item) {
                                is HomeItem.NoteItem -> {
                                    item(key = item.id) {
                                        val note = item.note
                                        Box {
                                            SwipeSelectableNoteCard(
                                                note = note,
                                                folderName = note.folderId?.let(folderNames::get),
                                                pathName = pathNames[note.id],
                                                selected = note.id in selectedIds,
                                                isSelectionMode = selectionState !is SelectionState.None,
                                                onClick = {
                                                    if (selectionState !is SelectionState.None) noteViewModel.toggleSelection(note.id)
                                                    else onNoteClick(note.id)
                                                },
                                                onLongPress = { 
                                                    if (selectionState is SelectionState.None) {
                                                        noteViewModel.addToSelection(note.id)
                                                        contextMenuNoteId = note.id
                                                    } else {
                                                        noteViewModel.toggleSelection(note.id)
                                                    }
                                                },
                                                onSwipeRight = { noteViewModel.toggleStar(note) },
                                                onSwipeLeft = { if (isTrashSection) noteViewModel.deletePermanently(note) else noteViewModel.moveToTrash(note) }
                                            )
                                            
                                            DropdownMenu(
                                                expanded = contextMenuNoteId == note.id,
                                                onDismissRequest = { contextMenuNoteId = null },
                                                shape = com.tv.wnotes.ui.theme.ActionMenuShape
                                            ) {
                                                DropdownMenuItem(text = { Text(if (note.isStarred) "Unstar" else "Star") }, onClick = { noteViewModel.toggleStar(note); contextMenuNoteId = null; noteViewModel.clearSelection() })
                                                DropdownMenuItem(text = { Text(if (note.isPinned) "Unpin" else "Pin") }, onClick = { noteViewModel.togglePin(note); contextMenuNoteId = null; noteViewModel.clearSelection() })
                                                DropdownMenuItem(text = { Text("Rename") }, onClick = { showRenameDialog = note; contextMenuNoteId = null })
                                                DropdownMenuItem(text = { Text("Info") }, onClick = { contextMenuNoteId = null; showInfoPanel = true; if (note.id !in selectedIds) { noteViewModel.clearSelection(); noteViewModel.addToSelection(note.id) } })
                                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { noteViewModel.duplicate(note); contextMenuNoteId = null; noteViewModel.clearSelection() })
                                                DropdownMenuItem(text = { Text("Add to Folder") }, onClick = { showFolderSheet = true; contextMenuNoteId = null })
                                                DropdownMenuItem(text = { Text("New Folder") }, onClick = { showNewFolderDialog = true; contextMenuNoteId = null })
                                                DropdownMenuItem(text = { Text("Group") }, onClick = { showGroupNameDialog = true; contextMenuNoteId = null })
                                                DropdownMenuItem(text = { Text("Staple") }, onClick = { showPathSheet = true; contextMenuNoteId = null })
                                                DropdownMenuItem(text = { Text("New Staple") }, onClick = { showNewStapleDialog = true; contextMenuNoteId = null })
                                                DropdownMenuItem(text = { Text(if (note.isLocked) "Unlock" else "Lock") }, onClick = { contextMenuNoteId = null; if (note.isLocked) noteViewModel.toggleLock(note) else { noteToLock = note; if (lockedPin == null) showPinSetupForNote = true else showPinEntryForNote = true }; noteViewModel.clearSelection() })
                                                DropdownMenuItem(text = { Text("Share") }, onClick = { shareNotes(context, listOf(note)); contextMenuNoteId = null; noteViewModel.clearSelection() })
                                                DropdownMenuItem(text = { Text(if (note.isDeleted) "Delete Permanently" else "Move to Trash") }, onClick = { if (note.isDeleted) noteViewModel.deletePermanently(note) else noteViewModel.moveToTrash(note); contextMenuNoteId = null; noteViewModel.clearSelection() })
                                            }

                                            if (contextMenuNoteId == note.id) {
                                                ContextualQuickNoteButton(
                                                    onQuickNote = {
                                                        quickNoteTargetId = note.id
                                                        showQuickNoteInput = true
                                                        contextMenuNoteId = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                is HomeItem.GroupItem -> {
                                    val groupWithNotes = item.groupWithNotes
                                    if (isHomeScreen) {
                                        item(key = item.id, span = { GridItemSpan(maxLineSpan) }) {
                                            Box {
                                                GroupCollageCard(
                                                    groupWithNotes = groupWithNotes,
                                                    selected = groupWithNotes.group.id in selectedGroupIds,
                                                    folderName = groupWithNotes.group.folderId?.let(folderNames::get),
                                                    pathName = groupPathNames[groupWithNotes.group.id],
                                                    onClick = { 
                                                        if (selectionState !is SelectionState.None) {
                                                            noteViewModel.toggleGroupSelection(groupWithNotes.group.id)
                                                        } else if (onNavigateToGroup != null) {
                                                            onNavigateToGroup(groupWithNotes.group.id, groupWithNotes.group.name)
                                                        }
                                                    },
                                                    onLongPress = {
                                                        if (selectionState is SelectionState.None) {
                                                            noteViewModel.addGroupToSelection(groupWithNotes.group.id)
                                                            contextMenuGroupId = groupWithNotes.group.id
                                                        } else {
                                                            noteViewModel.toggleGroupSelection(groupWithNotes.group.id)
                                                        }
                                                    },
                                                    onNoteClick = { note, _, _ -> onNoteClick(note.id) },
                                                    onPreviewStart = { _, _, _, _, _ -> },
                                                    onPreviewChange = { _, _ -> },
                                                    onPreviewEnd = { }
                                                )

                                                DropdownMenu(
                                                    expanded = contextMenuGroupId == groupWithNotes.group.id,
                                                    onDismissRequest = { contextMenuGroupId = null },
                                                    shape = com.tv.wnotes.ui.theme.ActionMenuShape
                                                ) {
                                                    DropdownMenuItem(text = { Text("Ungroup") }, onClick = { noteViewModel.ungroup(groupWithNotes.group.id); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text(if (groupWithNotes.group.isStarred) "Unstar" else "Star") }, onClick = { noteViewModel.toggleGroupStar(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text(if (groupWithNotes.group.isPinned) "Unpin" else "Pin") }, onClick = { noteViewModel.toggleGroupPin(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showGroupRenameDialog = groupWithNotes.group.id; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { noteViewModel.duplicateGroup(groupWithNotes); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Add to Folder") }, onClick = { showFolderSheet = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("New Folder") }, onClick = { showNewFolderDialog = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("Staple") }, onClick = { showPathSheet = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("New Staple") }, onClick = { showNewStapleDialog = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text(if (groupWithNotes.group.isLocked) "Unlock" else "Lock") }, onClick = { noteViewModel.toggleGroupLock(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Share") }, onClick = { shareNotes(context, groupWithNotes.notes); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { noteViewModel.moveGroupToTrash(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                }
                                            }
                                        }
                                    } else {
                                        // UNPACKED Grid View
                                        item(key = "header_${groupWithNotes.group.id}", span = { GridItemSpan(maxLineSpan) }) {
                                            Box {
                                                GroupHeader(
                                                    group = groupWithNotes.group,
                                                    selected = groupWithNotes.group.id in selectedGroupIds,
                                                    onLongPress = {
                                                        if (selectionState is SelectionState.None) {
                                                            noteViewModel.addGroupToSelection(groupWithNotes.group.id)
                                                            contextMenuGroupId = groupWithNotes.group.id
                                                        } else {
                                                            noteViewModel.toggleGroupSelection(groupWithNotes.group.id)
                                                        }
                                                    },
                                                    onClick = {
                                                        if (selectionState !is SelectionState.None) {
                                                            noteViewModel.toggleGroupSelection(groupWithNotes.group.id)
                                                        } else if (onNavigateToGroup != null) {
                                                            onNavigateToGroup(groupWithNotes.group.id, groupWithNotes.group.name)
                                                        }
                                                    }
                                                )

                                                DropdownMenu(
                                                    expanded = contextMenuGroupId == groupWithNotes.group.id,
                                                    onDismissRequest = { contextMenuGroupId = null },
                                                    shape = com.tv.wnotes.ui.theme.ActionMenuShape
                                                ) {
                                                    DropdownMenuItem(text = { Text("Ungroup") }, onClick = { noteViewModel.ungroup(groupWithNotes.group.id); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text(if (groupWithNotes.group.isStarred) "Unstar" else "Star") }, onClick = { noteViewModel.toggleGroupStar(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text(if (groupWithNotes.group.isPinned) "Unpin" else "Pin") }, onClick = { noteViewModel.toggleGroupPin(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showGroupRenameDialog = groupWithNotes.group.id; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { noteViewModel.duplicateGroup(groupWithNotes); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Add to Folder") }, onClick = { showFolderSheet = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("New Folder") }, onClick = { showNewFolderDialog = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("Staple") }, onClick = { showPathSheet = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text("New Staple") }, onClick = { showNewStapleDialog = true; contextMenuGroupId = null })
                                                    DropdownMenuItem(text = { Text(if (groupWithNotes.group.isLocked) "Unlock" else "Lock") }, onClick = { noteViewModel.toggleGroupLock(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Share") }, onClick = { shareNotes(context, groupWithNotes.notes); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                    DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { noteViewModel.moveGroupToTrash(groupWithNotes.group); contextMenuGroupId = null; noteViewModel.clearSelection() })
                                                }
                                            }
                                        }

                                        items(groupWithNotes.orderedNotes, key = { "note_${it.id}_in_${groupWithNotes.group.id}" }) { note ->
                                            Box {
                                                SwipeSelectableNoteCard(
                                                    note = note,
                                                    folderName = note.folderId?.let(folderNames::get),
                                                    pathName = pathNames[note.id],
                                                    selected = note.id in selectedIds,
                                                    isSelectionMode = selectionState !is SelectionState.None,
                                                    onClick = {
                                                        if (selectionState !is SelectionState.None) noteViewModel.toggleSelection(note.id)
                                                        else onNoteClick(note.id)
                                                    },
                                                    onLongPress = { 
                                                        if (selectionState is SelectionState.None) {
                                                            noteViewModel.addToSelection(note.id)
                                                            contextMenuNoteId = note.id
                                                        } else {
                                                            noteViewModel.toggleSelection(note.id)
                                                        }
                                                    },
                                                    onSwipeRight = { noteViewModel.toggleStar(note) },
                                                    onSwipeLeft = { if (isTrashSection) noteViewModel.deletePermanently(note) else noteViewModel.moveToTrash(note) }
                                                )

                                                if (contextMenuNoteId == note.id) {
                                                    ContextualQuickNoteButton(
                                                        onQuickNote = {
                                                            quickNoteTargetId = note.id
                                                            showQuickNoteInput = true
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            WScrollbar(
                gridState = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            )

            MultiSelectFloatingActions(
                visible = selectionState !is SelectionState.None,
                onStar = { 
                    noteViewModel.starNotes(selectedNotes)
                    scope.launch {
                        selectedGroupIds.forEach { groupId ->
                            noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                noteViewModel.toggleGroupStar(it)
                            }
                        }
                    }
                    noteViewModel.clearSelection()
                },
                onTrash = {
                    if (isTrashSection) noteViewModel.deletePermanently(selectedNotes)
                    else noteViewModel.moveNotesToTrash(selectedNotes)
                    scope.launch {
                        selectedGroupIds.forEach { groupId ->
                            noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                noteViewModel.moveGroupToTrash(it)
                            }
                        }
                    }
                    noteViewModel.clearSelection()
                },
                onShare = {
                    scope.launch {
                        val allToShare = selectedNotes.toMutableList()
                        selectedGroupIds.forEach { groupId ->
                            noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.notes?.let {
                                allToShare.addAll(it)
                            }
                        }
                        shareNotes(context, allToShare)
                        noteViewModel.clearSelection()
                    }
                },
                onFolder = { showFolderSheet = true },
                onPath = { showPathSheet = true },
                onPin = {
                    selectedNotes.forEach { noteViewModel.togglePin(it) }
                    selectedGroupIds.forEach { groupId ->
                        scope.launch {
                            noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                noteViewModel.toggleGroupPin(it)
                            }
                        }
                    }
                    noteViewModel.clearSelection()
                },
                onDuplicate = {
                    selectedNotes.forEach { noteViewModel.duplicate(it) }
                    selectedGroupIds.forEach { groupId ->
                        scope.launch {
                            noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.let {
                                noteViewModel.duplicateGroup(it)
                            }
                        }
                    }
                    noteViewModel.clearSelection()
                },
                selectedCount = selectionState.totalCount,
                onInfo = if (selectionState is SelectionState.SingleNote || selectionState is SelectionState.SingleGroup) ({ showInfoPanel = !showInfoPanel }) else null,
                onGroup = if (!isTrashSection && sectionKey != "locked" && (selectionState is SelectionState.SingleNote || selectionState is SelectionState.MultipleNotes)) ({
                    showGroupNameDialog = true
                }) else null,
                showInfoPanel = showInfoPanel,
                onCloseInfo = { showInfoPanel = false },
                infoContent = @Composable {
                    if (selectionState is SelectionState.SingleNote) {
                        val singleNoteState = selectionState as SelectionState.SingleNote
                        val noteId = singleNoteState.noteId
                        val note = allNotesList.find { it.id == noteId }
                        note?.let {
                            val words = if (it.content.isBlank()) 0 else it.content.trim().split(Regex("\\s+")).size
                            Column {
                                Text("Words: $words", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Created:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.createdAt)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else if (selectionState is SelectionState.SingleGroup) {
                        val groupId = (selectionState as SelectionState.SingleGroup).groupId
                        val groupFlow = remember(groupId) { noteViewModel.getGroupWithNotesById(groupId) }
                        val groupWithNotes by groupFlow.collectAsState(initial = null)
                        
                        groupWithNotes?.let {
                            val noteCount = it.notes.size
                            Column {
                                Text("Notes: $noteCount", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Created:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.group.createdAt)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            )

            if (showQuickNoteInput && quickNoteTargetId != null) {
                QuickNoteInput(
                    onDismiss = { text ->
                        if (text.isNotBlank()) {
                            noteViewModel.appendQuickNote(quickNoteTargetId!!, text)
                        }
                        showQuickNoteInput = false
                        quickNoteTargetId = null
                        noteViewModel.clearSelection()
                    }
                )
            }
        }
    }

    if (showGroupNameDialog) {
        var groupName by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        
        AlertDialog(
            onDismissRequest = { showGroupNameDialog = false },
            title = { Text("Create Group") },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Enter group name") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            },
            confirmButton = {
                TextButton(
                    enabled = groupName.isNotBlank(),
                    onClick = {
                        if (groupName.isNotBlank()) {
                            noteViewModel.createGroup(groupName.trim(), selectedIds.toList())
                            noteViewModel.clearSelection()
                            showGroupNameDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showGroupNameDialog = false 
                    noteViewModel.clearSelection()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFolderSheet) {
        FolderAssignmentSheet(
            folders = folders,
            onDismiss = { showFolderSheet = false },
            onAssign = { folderId ->
                noteViewModel.moveNotesToFolder(selectedNotes, folderId)
                selectedGroupIds.forEach { groupId ->
                    scope.launch {
                        noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                            noteViewModel.moveGroupToFolder(it, folderId)
                        }
                    }
                }
                noteViewModel.clearSelection() 
            },
            onCreateFolder = { name, onCreated -> folderViewModel.addFolder(name, null, onCreated) }
        )
    }

    if (showPathSheet) {
        StapleAssignmentSheet(
            paths = paths,
            onDismiss = { showPathSheet = false },
            onAssign = { pId ->
                scope.launch {
                    if (pId == null) {
                        selectedIds.forEach { linkPathViewModel.unstapleNote(it) }
                        selectedGroupIds.forEach { groupId ->
                            linkPathViewModel.unstapleGroup(groupId)
                        }
                    } else {
                        selectedIds.forEach { linkPathViewModel.stapleNote(it, pId) }
                        selectedGroupIds.forEach { linkPathViewModel.stapleGroup(it, pId) }
                    }
                    noteViewModel.clearSelection()
                }
            },
            onCreatePath = { name, onCreated -> 
                scope.launch {
                    val allItemsToStaple = mutableListOf<HomeItem>()
                    allItemsToStaple.addAll(selectedNotes.map { HomeItem.NoteItem(it) })
                    for (groupId in selectedGroupIds) {
                        noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.let { groupWithNotes ->
                            groupWithNotes.orderedNotes.forEach { note ->
                                allItemsToStaple.add(HomeItem.NoteItem(note))
                            }
                        }
                    }
                    linkPathViewModel.createStaple(name, allItemsToStaple, onCreated)
                    noteViewModel.clearSelection()
                }
            }
        )
    }

    if (showGroupRenameDialog != null) {
        val groupId = showGroupRenameDialog!!
        val groupWithNotes by noteViewModel.getGroupWithNotesById(groupId).collectAsState(initial = null)
        var newName by remember(groupWithNotes) { mutableStateOf(groupWithNotes?.group?.name ?: "") }
        
        AlertDialog(
            onDismissRequest = { showGroupRenameDialog = null },
            title = { Text("Rename Group") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Enter group name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            noteViewModel.renameGroup(groupId, newName)
                            showGroupRenameDialog = null
                            noteViewModel.clearSelection()
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog != null) {
        RenameNoteDialog(
            initialTitle = showRenameDialog!!.title,
            onDismiss = { showRenameDialog = null },
            onRename = { newTitle ->
                noteViewModel.rename(showRenameDialog!!, newTitle)
                showRenameDialog = null
                noteViewModel.clearSelection()
            }
        )
    }

    if (showPinSetupForNote) {
        PinDialog(
            mode = PinMode.SET,
            onDismiss = { showPinSetupForNote = false },
            onConfirm = {
                noteViewModel.setLockedPin(it)
                noteToLock?.let { note -> noteViewModel.toggleLock(note) }
                showPinSetupForNote = false
                noteToLock = null
            }
        )
    }

    if (showPinEntryForNote) {
        PinDialog(
            mode = PinMode.ENTER,
            correctPin = lockedPin,
            onDismiss = { showPinEntryForNote = false },
            onConfirm = {
                noteToLock?.let { note -> noteViewModel.toggleLock(note) }
                showPinEntryForNote = false
                noteToLock = null
            }
        )
    }

    if (showPinReset) {
        PinDialog(
            mode = PinMode.RESET,
            correctPin = lockedPin,
            onDismiss = { showPinReset = false },
            onConfirm = {
                noteViewModel.setLockedPin(it)
                showPinReset = false
            }
        )
    }

    if (showNewFolderDialog) {
        CreateNameDialog(
            title = "New Folder",
            placeholder = "Folder name",
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                scope.launch {
                    folderViewModel.addFolder(name, null) { id ->
                        noteViewModel.moveNotesToFolder(selectedNotes, id)
                        selectedGroupIds.forEach { groupId ->
                            scope.launch {
                                noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                    noteViewModel.moveGroupToFolder(it, id)
                                }
                            }
                        }
                    }
                    noteViewModel.clearSelection()
                    showNewFolderDialog = false
                }
            }
        )
    }

    if (showNewStapleDialog) {
        CreateNameDialog(
            title = "New Staple",
            placeholder = "Staple name",
            onDismiss = { showNewStapleDialog = false },
            onCreate = { name ->
                scope.launch {
                    val allItemsToStaple = mutableListOf<HomeItem>()
                    allItemsToStaple.addAll(selectedNotes.map { HomeItem.NoteItem(it) })
                    for (groupId in selectedGroupIds) {
                        noteViewModel.getGroupWithNotesById(groupId).firstOrNull()?.let { groupWithNotes ->
                            groupWithNotes.orderedNotes.forEach { note ->
                                allItemsToStaple.add(HomeItem.NoteItem(note))
                            }
                        }
                    }
                    linkPathViewModel.createStaple(name, allItemsToStaple)
                    noteViewModel.clearSelection()
                    showNewStapleDialog = false
                }
            }
        )
    }
}

private fun shareNotes(context: android.content.Context, notes: List<Note>) {
    if (notes.isEmpty()) return
    val text = notes.joinToString("\n---\n") { "${it.title}\n${it.content}" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Notes"))
}

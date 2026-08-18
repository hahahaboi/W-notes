package com.tv.wnotes.ui.home

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.pref.PreferenceManager
import com.tv.wnotes.ui.components.*
import com.tv.wnotes.util.TitleUtils
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import com.tv.wnotes.viewmodel.SelectionState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class NoteAnimationData(
    val note: Note,
    val initialGeometry: CardGeometry,
    val containerOffset: Offset,
    val isReturning: Boolean = false,
    val isNavigatedAway: Boolean = false
)

enum class PreviewState { SCRUBBING, PERSISTENT, EXPANDING }

data class PreviewNoteData(
    val note: Note,
    val geometry: CardGeometry,
    val containerOffset: Offset,
    val state: PreviewState = PreviewState.SCRUBBING,
    val allNotes: List<Note> = emptyList(),
    val geometries: Map<Long, CardGeometry> = emptyMap()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onNavigateToEditor: (Long?) -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToLinkPaths: () -> Unit,
    onNavigateToStarred: () -> Unit,
    onNavigateToAllNotes: () -> Unit,
    onNavigateToUncategorized: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToGroup: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    drawerProgress: Float
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val homeItems by viewModel.homeItems.collectAsState()
    val allNotesList by viewModel.allNotes.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val notePathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val groupPathInfo by linkPathViewModel.allGroupPathInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectionState by viewModel.selectionState.collectAsState()
    val selectedIds by viewModel.selectedNoteIds.collectAsState()
    val selectedGroupIds by viewModel.selectedGroupIds.collectAsState()
    val lockedPin by viewModel.lockedPin.collectAsState()

    val selectedNotes = remember(allNotesList, selectedIds) { 
        allNotesList.filter { it.id in selectedIds } 
    }

    var animatingNoteData by remember { mutableStateOf<NoteAnimationData?>(null) }
    var previewNoteData by remember { mutableStateOf<PreviewNoteData?>(null) }

    // Track return from navigation
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                animatingNoteData?.let { data ->
                    if (data.isNavigatedAway) {
                        animatingNoteData = data.copy(isReturning = true, isNavigatedAway = false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val preferenceManager = remember(context) { PreferenceManager(context) }
    val sortName by preferenceManager.sectionSort("home").collectAsState(initial = SectionSortOption.RECENTLY_EDITED.name)
    val sortOption = SectionSortOption.fromName(sortName)
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuNoteId by remember { mutableStateOf<Long?>(null) }
    var contextMenuGroupId by remember { mutableStateOf<Long?>(null) }
    var showRenameDialog by remember { mutableStateOf<Note?>(null) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showPathSheet by remember { mutableStateOf(false) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewStapleDialog by remember { mutableStateOf(false) }
    var showGroupRenameDialog by remember { mutableStateOf<Long?>(null) }

    var noteToLock by remember { mutableStateOf<Note?>(null) }
    var showPinSetupForNote by remember { mutableStateOf(false) }
    var showPinEntryForNote by remember { mutableStateOf(false) }
    var showQuickNoteInput by remember { mutableStateOf(false) }
    var quickNoteTargetId by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val folderNames = remember(folders) { folders.associateBy { it.id } }
    val pathNames = remember(notePathInfo) { notePathInfo.associate { it.noteId to it.pathName } }
    val groupPathNames = remember(groupPathInfo) { groupPathInfo.associate { it.groupId to it.pathName } }

    val displayItems: List<HomeItem> = remember(homeItems, searchResults, searchQuery) {
        if (searchQuery.isNotEmpty()) {
            searchResults
        } else {
            homeItems
        }
    }

    LaunchedEffect(selectionState) {
        if (selectionState !is SelectionState.SingleNote) {
            showInfoPanel = false
            showQuickNoteInput = false
            quickNoteTargetId = null
        }
        if (selectionState.totalCount > 1) contextMenuNoteId = null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    val totalSelected = selectedIds.size + selectedGroupIds.size
                    val titleText = when {
                        totalSelected == 0 -> "W Notes"
                        else -> "$totalSelected selected"
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) {
                            viewModel.clearSelection()
                        } else {
                            onOpenDrawer()
                        }
                    }) {
                        if (selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                    if (selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) {
                        TextButton(onClick = { 
                            viewModel.clearSelection() 
                        }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search by text, 'mention ...', or '100 words'") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "Recent Notes" else "Search Results",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Text("Sort ▼")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SectionSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        scope.launch {
                                            preferenceManager.saveSectionSort("home", option.name)
                                        }
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (displayItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isEmpty()) 
                                    "No notes yet. Tap the + button to create your first note." 
                                    else "No results found.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 170.dp),
                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = displayItems, 
                                    key = { item: HomeItem -> item.id },
                                    span = { item: HomeItem ->
                                        when (item) {
                                            is HomeItem.GroupItem -> GridItemSpan(maxLineSpan)
                                            is HomeItem.NoteItem -> GridItemSpan(1)
                                        }
                                    }
                                ) { item: HomeItem ->
                                    when (item) {
                                        is HomeItem.NoteItem -> {
                                            val note = item.note
                                            Box {
                                                SwipeSelectableNoteCard(
                                                    note = note,
                                                    folderName = folderNames[note.folderId]?.name,
                                                    pathName = pathNames[note.id],
                                                    selected = note.id in selectedIds,
                                                    isSelectionMode = selectedIds.isNotEmpty(),
                                                    onClick = {
                                                        if (selectedIds.isNotEmpty()) {
                                                            viewModel.toggleSelection(note.id)
                                                        } else {
                                                            onNavigateToEditor(note.id)
                                                        }
                                                    },
                                                    onLongPress = {
                                                        viewModel.addToSelection(note.id)
                                                        contextMenuNoteId = note.id
                                                    },
                                                    onSwipeRight = { viewModel.toggleStar(note) },
                                                    onSwipeLeft = { viewModel.moveToTrash(note) }
                                                )

                                                DropdownMenu(
                                                    expanded = contextMenuNoteId == note.id,
                                                    onDismissRequest = { contextMenuNoteId = null },
                                                    shape = com.tv.wnotes.ui.theme.ActionMenuShape
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(if (note.isStarred) "Unstar" else "Star") },
                                                        onClick = {
                                                            viewModel.toggleStar(note)
                                                            contextMenuNoteId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                                        onClick = {
                                                            viewModel.togglePin(note)
                                                            contextMenuNoteId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Rename") },
                                                        onClick = {
                                                            showRenameDialog = note
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Duplicate") },
                                                        onClick = {
                                                            viewModel.duplicate(note)
                                                            contextMenuNoteId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Add to Folder") },
                                                        onClick = {
                                                            showFolderSheet = true
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("New Folder") },
                                                        onClick = {
                                                            showNewFolderDialog = true
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Group") },
                                                        onClick = {
                                                            showGroupNameDialog = true
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Staple") },
                                                        onClick = {
                                                            showPathSheet = true
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("New Staple") },
                                                        onClick = {
                                                            showNewStapleDialog = true
                                                            contextMenuNoteId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (note.isLocked) "Unlock" else "Lock") },
                                                        onClick = {
                                                            contextMenuNoteId = null
                                                            if (note.isLocked) {
                                                                viewModel.toggleLock(note)
                                                            } else {
                                                                noteToLock = note
                                                                if (lockedPin == null) {
                                                                    showPinSetupForNote = true
                                                                } else {
                                                                    showPinEntryForNote = true
                                                                }
                                                            }
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Share") },
                                                        onClick = {
                                                            shareNotes(context, listOf(note))
                                                            contextMenuNoteId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Move to Trash") },
                                                        onClick = {
                                                            viewModel.moveToTrash(note)
                                                            contextMenuNoteId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
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
                                        is HomeItem.GroupItem -> {
                                            val groupWithNotes = item.groupWithNotes
                                            Box {
                                                GroupCollageCard(
                                                    groupWithNotes = groupWithNotes,
                                                    selected = groupWithNotes.group.id in selectedGroupIds,
                                                    folderName = folderNames[groupWithNotes.group.folderId]?.name,
                                                    pathName = groupPathNames[groupWithNotes.group.id],
                                                    onClick = { 
                                                        if (selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) {
                                                            viewModel.toggleGroupSelection(groupWithNotes.group.id)
                                                        } else {
                                                            onNavigateToGroup(groupWithNotes.group.id, groupWithNotes.group.name)
                                                        }
                                                    },
                                                    onLongPress = {
                                                        viewModel.addGroupToSelection(groupWithNotes.group.id)
                                                        contextMenuGroupId = groupWithNotes.group.id
                                                    },
                                                    onNoteClick = { note, geometry, containerOffset ->
                                                        animatingNoteData = NoteAnimationData(note, geometry, containerOffset)
                                                    },
                                                    onPreviewStart = { note, geometry, containerOffset, allNotes, geometries ->
                                                        previewNoteData = PreviewNoteData(note, geometry, containerOffset, PreviewState.SCRUBBING, allNotes, geometries)
                                                    },
                                                    onPreviewChange = { note, geometry ->
                                                        previewNoteData = previewNoteData?.copy(note = note, geometry = geometry, state = PreviewState.SCRUBBING)
                                                    },
                                                    onPreviewEnd = {
                                                        previewNoteData = previewNoteData?.copy(state = PreviewState.PERSISTENT)
                                                    }
                                                )

                                                DropdownMenu(
                                                    expanded = contextMenuGroupId == groupWithNotes.group.id,
                                                    onDismissRequest = { contextMenuGroupId = null },
                                                    shape = com.tv.wnotes.ui.theme.ActionMenuShape
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Ungroup") },
                                                        onClick = {
                                                            viewModel.ungroup(groupWithNotes.group.id)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (groupWithNotes.group.isStarred) "Unstar" else "Star") },
                                                        onClick = {
                                                            viewModel.toggleGroupStar(groupWithNotes.group)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (groupWithNotes.group.isPinned) "Unpin" else "Pin") },
                                                        onClick = {
                                                            viewModel.toggleGroupPin(groupWithNotes.group)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Rename") },
                                                        onClick = {
                                                            showGroupRenameDialog = groupWithNotes.group.id
                                                            contextMenuGroupId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Duplicate") },
                                                        onClick = {
                                                            viewModel.duplicateGroup(groupWithNotes)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Info") },
                                                        onClick = {
                                                            contextMenuGroupId = null
                                                            showInfoPanel = true
                                                            if (groupWithNotes.group.id !in selectedGroupIds) {
                                                                viewModel.clearSelection()
                                                                viewModel.addGroupToSelection(groupWithNotes.group.id)
                                                            }
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Add to Folder") },
                                                        onClick = {
                                                            showFolderSheet = true
                                                            contextMenuGroupId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("New Folder") },
                                                        onClick = {
                                                            showNewFolderDialog = true
                                                            contextMenuGroupId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Staple") },
                                                        onClick = {
                                                            showPathSheet = true
                                                            contextMenuGroupId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("New Staple") },
                                                        onClick = {
                                                            showNewStapleDialog = true
                                                            contextMenuGroupId = null
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (groupWithNotes.group.isLocked) "Unlock" else "Lock") },
                                                        onClick = {
                                                            viewModel.toggleGroupLock(groupWithNotes.group)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Share") },
                                                        onClick = {
                                                            shareNotes(context, groupWithNotes.notes)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Move to Trash") },
                                                        onClick = {
                                                            viewModel.moveGroupToTrash(groupWithNotes.group)
                                                            contextMenuGroupId = null
                                                            viewModel.clearSelection()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            WScrollbar(
                                gridState = gridState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }

            MultiSelectFloatingActions(
                visible = selectionState !is SelectionState.None,
                onStar = { 
                    viewModel.starNotes(selectedNotes)
                    // Also star selected groups
                    scope.launch {
                        selectedGroupIds.forEach { groupId ->
                            viewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                viewModel.toggleGroupStar(it)
                            }
                        }
                    }
                    viewModel.clearSelection()
                },
                onTrash = {
                    viewModel.moveNotesToTrash(selectedNotes)
                    // Also trash selected groups
                    scope.launch {
                        selectedGroupIds.forEach { groupId ->
                            viewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                viewModel.moveGroupToTrash(it)
                            }
                        }
                    }
                    viewModel.clearSelection()
                },
                onShare = {
                    // Combine all notes from selected notes and selected groups
                    scope.launch {
                        val allToShare = selectedNotes.toMutableList()
                        selectedGroupIds.forEach { groupId ->
                            viewModel.getGroupWithNotesById(groupId).firstOrNull()?.notes?.let {
                                allToShare.addAll(it)
                            }
                        }
                        shareNotes(context, allToShare)
                        viewModel.clearSelection()
                    }
                },
                onFolder = { showFolderSheet = true },
                onPath = { showPathSheet = true },
                onPin = {
                    selectedNotes.forEach { viewModel.togglePin(it) }
                    selectedGroupIds.forEach { groupId ->
                        scope.launch {
                            viewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                viewModel.toggleGroupPin(it)
                            }
                        }
                    }
                    viewModel.clearSelection()
                },
                onDuplicate = {
                    selectedNotes.forEach { viewModel.duplicate(it) }
                    selectedGroupIds.forEach { groupId ->
                        scope.launch {
                            viewModel.getGroupWithNotesById(groupId).firstOrNull()?.let {
                                viewModel.duplicateGroup(it)
                            }
                        }
                    }
                    viewModel.clearSelection()
                },
                selectedCount = selectionState.totalCount,
                onInfo = if (selectionState is SelectionState.SingleNote || selectionState is SelectionState.SingleGroup) ({ showInfoPanel = !showInfoPanel }) else null,
                onGroup = if (selectionState is SelectionState.SingleNote || selectionState is SelectionState.MultipleNotes) ({
                    showGroupNameDialog = true
                }) else null,
                showInfoPanel = showInfoPanel,
                onCloseInfo = { showInfoPanel = false },
                infoContent = {
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
                        val groupFlow = remember(groupId) { viewModel.getGroupWithNotesById(groupId) }
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
        }

        if (showQuickNoteInput && quickNoteTargetId != null) {
            QuickNoteInput(
                onDismiss = { text ->
                    val targetId = quickNoteTargetId
                    if (targetId != null && text.isNotBlank()) {
                        viewModel.appendQuickNote(targetId, text)
                    }
                    showQuickNoteInput = false
                    quickNoteTargetId = null
                    viewModel.clearSelection()
                }
            )
        }

        // Pop-out Animation Overlay
        animatingNoteData?.let { data ->
            NotePopOutOverlay(
                data = data,
                onAnimationComplete = {
                    if (!data.isReturning) {
                        onNavigateToEditor(data.note.id)
                        // Mark as navigated away so we can trigger return animation on resume
                        animatingNoteData = data.copy(isNavigatedAway = true)
                    } else {
                        animatingNoteData = null
                    }
                }
            )
        }

        // Preview Overlay
        previewNoteData?.let { data ->
            NotePreviewOverlay(
                data = data,
                onDismiss = { previewNoteData = null },
                onNoteChange = { note, geometry ->
                    previewNoteData = previewNoteData?.copy(note = note, geometry = geometry)
                },
                onExpand = { 
                    previewNoteData = data.copy(state = PreviewState.EXPANDING)
                },
                onExpansionComplete = {
                    onNavigateToEditor(data.note.id)
                    // Prepare return animation data
                    animatingNoteData = NoteAnimationData(
                        note = data.note,
                        initialGeometry = data.geometry,
                        containerOffset = data.containerOffset,
                        isNavigatedAway = true
                    )
                    previewNoteData = null
                }
            )
        }
    }

    if (showGroupRenameDialog != null) {
        val groupId = showGroupRenameDialog!!
        val groupWithNotes by viewModel.getGroupWithNotesById(groupId).collectAsState(initial = null)
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
                            viewModel.renameGroup(groupId, newName)
                            showGroupRenameDialog = null
                            viewModel.clearSelection()
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
                            viewModel.createGroup(groupName.trim(), selectedIds.toList())
                            viewModel.clearSelection()
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
                    viewModel.clearSelection()
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
                viewModel.moveNotesToFolder(selectedNotes, folderId)
                selectedGroupIds.forEach { groupId ->
                    scope.launch {
                        viewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                            viewModel.moveGroupToFolder(it, folderId)
                        }
                    }
                }
                viewModel.clearSelection() 
            },
            onCreateFolder = { name, onCreated -> folderViewModel.addFolder(name, null, onCreated) }
        )
    }

    if (showPathSheet) {
        StapleAssignmentSheet(
            paths = linkPathViewModel.allPaths.collectAsState().value,
            onDismiss = { showPathSheet = false },
            onAssign = { pId ->
                scope.launch {
                    if (pId == null) {
                        selectedIds.forEach { linkPathViewModel.unstapleNote(it) }
                        selectedGroupIds.forEach { linkPathViewModel.unstapleGroup(it) }
                    } else {
                        selectedIds.forEach { linkPathViewModel.stapleNote(it, pId) }
                        selectedGroupIds.forEach { linkPathViewModel.stapleGroup(it, pId) }
                    }
                    viewModel.clearSelection()
                }
            },
            onCreatePath = { name, onCreated -> 
                scope.launch {
                    val allItemsToStaple = mutableListOf<HomeItem>()
                    allItemsToStaple.addAll(selectedNotes.map { HomeItem.NoteItem(it) })
                    for (groupId in selectedGroupIds) {
                        viewModel.getGroupWithNotesById(groupId).firstOrNull()?.let { groupWithNotes ->
                            // Flatten group into individual notes for the Staple
                            groupWithNotes.orderedNotes.forEach { note ->
                                allItemsToStaple.add(HomeItem.NoteItem(note))
                            }
                        }
                    }
                    linkPathViewModel.createStaple(name, allItemsToStaple, onCreated)
                    viewModel.clearSelection()
                }
            }
        )
    }

    if (showRenameDialog != null) {
        RenameNoteDialog(
            initialTitle = showRenameDialog!!.title,
            onDismiss = { showRenameDialog = null },
            onRename = { newTitle ->
                viewModel.rename(showRenameDialog!!, newTitle)
                showRenameDialog = null
                viewModel.clearSelection()
            }
        )
    }

    if (showPinSetupForNote) {
        PinDialog(
            mode = PinMode.SET,
            onDismiss = { showPinSetupForNote = false },
            onConfirm = {
                viewModel.setLockedPin(it)
                noteToLock?.let { note -> viewModel.toggleLock(note) }
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
                noteToLock?.let { note -> viewModel.toggleLock(note) }
                showPinEntryForNote = false
                noteToLock = null
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
                        viewModel.moveNotesToFolder(selectedNotes, id)
                        selectedGroupIds.forEach { groupId ->
                            scope.launch {
                                viewModel.getGroupWithNotesById(groupId).firstOrNull()?.group?.let {
                                    viewModel.moveGroupToFolder(it, id)
                                }
                            }
                        }
                    }
                    viewModel.clearSelection()
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
                        viewModel.getGroupWithNotesById(groupId).firstOrNull()?.let { groupWithNotes ->
                            // Flatten group into individual notes for the Staple
                            groupWithNotes.orderedNotes.forEach { note ->
                                allItemsToStaple.add(HomeItem.NoteItem(note))
                            }
                        }
                    }
                    linkPathViewModel.createStaple(name, allItemsToStaple)
                    viewModel.clearSelection()
                    showNewStapleDialog = false
                }
            }
        )
    }
}

@Composable
fun NotePopOutOverlay(
    data: NoteAnimationData,
    onAnimationComplete: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp }
    val screenHeight = with(density) { configuration.screenHeightDp.dp }

    val animProgress = remember { Animatable(if (data.isReturning) 1f else 0f) }

    LaunchedEffect(data) {
        if (data.isReturning) {
            animProgress.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        } else {
            animProgress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }
        onAnimationComplete()
    }

    val progress = animProgress.value

    // Layout constants for full screen state (editor-like)
    val targetWidth = screenWidth
    val targetHeight = screenHeight
    val targetX = 0.dp
    val targetY = 0.dp
    val targetRotation = 0f
    
    // Starting constants
    val startWidth = data.initialGeometry.width
    val startHeight = data.initialGeometry.height
    // Offset relative to the whole screen
    val startX = with(density) { data.containerOffset.x.toDp() } + data.initialGeometry.xOffset
    val startY = with(density) { data.containerOffset.y.toDp() } + data.initialGeometry.yOffset
    val startRotation = data.initialGeometry.rotation

    // Interpolate
    val currentWidth = lerp(startWidth, targetWidth, progress)
    val currentHeight = lerp(startHeight, targetHeight, progress)
    val currentX = lerp(startX, targetX, progress)
    val currentY = lerp(startY, targetY, progress)
    val currentRotation = lerp(startRotation, targetRotation, progress)
    
    // 3D Flip logic: rotate around Y axis from 0 to 360 (or just 180) during middle of animation
    val rotationY = if (progress < 0.5f) {
        lerp(0f, 180f, progress * 2)
    } else {
        lerp(180f, 360f, (progress - 0.5f) * 2)
    }

    val elevation = lerp(3.dp, 16.dp, progress)
    val alpha = if (data.isReturning) progress else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = progress * 0.3f)) // Dim background
    ) {
        Surface(
            modifier = Modifier
                .offset(x = currentX, y = currentY)
                .size(width = currentWidth, height = currentHeight)
                .graphicsLayer {
                    this.rotationZ = currentRotation
                    this.rotationY = rotationY
                    this.cameraDistance = 12f * density.density
                    this.alpha = alpha
                }
                .shadow(elevation, RoundedCornerShape(lerp(8.dp, 0.dp, progress))),
            shape = RoundedCornerShape(lerp(8.dp, 0.dp, progress)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (progress < 0.2f) {
                    // Show miniature content initially
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Text(
                            text = TitleUtils.getShortTitle(data.note.title, 2),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Transition to full note preview content
                    // Map progress 0.2-1.0 to 0.0-1.0 for the content interpolation
                    val contentProgress = ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    PreviewContent(note = data.note, progress = contentProgress)
                }
            }
        }
    }
}

fun lerp(start: Dp, end: Dp, fraction: Float): Dp {
    return start + (end - start) * fraction
}

fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

fun lerpText(start: TextUnit, end: TextUnit, fraction: Float): TextUnit {
    return (start.value + (end.value - start.value) * fraction).sp
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

@Composable
fun NotePreviewOverlay(
    data: PreviewNoteData,
    onDismiss: () -> Unit,
    onNoteChange: (Note, CardGeometry) -> Unit,
    onExpand: () -> Unit,
    onExpansionComplete: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    val expansionProgress = remember { Animatable(0f) }
    var measuredHeight by remember { mutableStateOf(0.dp) }

    val currentOnExpansionComplete by rememberUpdatedState(onExpansionComplete)
    val currentOnExpand by rememberUpdatedState(onExpand)

    val pagerState = rememberPagerState(
        initialPage = data.allNotes.indexOfFirst { it.id == data.note.id }.coerceAtLeast(0)
    ) { data.allNotes.size }

    // Sync pager back to parent state for expansion/return logic
    LaunchedEffect(pagerState.currentPage, data.state, data.allNotes) {
        if (data.state == PreviewState.PERSISTENT && pagerState.currentPage < data.allNotes.size) {
            val note = data.allNotes[pagerState.currentPage]
            val geometry = data.geometries[note.id] ?: data.geometry
            onNoteChange(note, geometry)
        }
    }
    
    LaunchedEffect(data.state) {
        if (data.state == PreviewState.EXPANDING) {
            expansionProgress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
            currentOnExpansionComplete()
        }
    }

    val progress = expansionProgress.value
    
    // Stable preview geometry - significantly smaller
    val stableWidth = (screenWidth * 0.65f).coerceAtMost(280.dp)
    val stableCornerRadius = 16.dp
    
    // Fullscreen geometry
    val targetWidth = screenWidth
    val targetHeight = screenHeight
    val targetCornerRadius = 0.dp
    
    // Anchor high in the top half
    val verticalBias = lerp(-0.75f, 0f, progress)
    
    // Interpolate
    val currentWidth = lerp(stableWidth, targetWidth, progress)
    
    // Clamp the preview height strictly for the non-expanding state
    val clampedMeasuredHeight = measuredHeight.coerceAtMost(220.dp)
    
    val currentHeight = if (progress > 0f && measuredHeight > 0.dp) {
        lerp(clampedMeasuredHeight, targetHeight, progress)
    } else {
        clampedMeasuredHeight
    }
    val currentCornerRadius = lerp(stableCornerRadius, targetCornerRadius, progress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color.Black.copy(alpha = (0.08f + progress * 0.32f).coerceIn(0f, 0.4f)))
            .then(
                if (data.state != PreviewState.SCRUBBING) {
                    Modifier.pointerInput(data.state) {
                        detectTapGestures { onDismiss() }
                    }
                } else Modifier
            ),
        contentAlignment = BiasAlignment(0f, verticalBias)
    ) {
        Card(
            modifier = Modifier
                .width(currentWidth)
                .then(
                    if (progress > 0f) Modifier.height(currentHeight) 
                    else Modifier.wrapContentHeight().onGloballyPositioned { coords ->
                        measuredHeight = with(density) { coords.size.height.toDp() }
                    }
                )
                .padding(lerp(16.dp, 0.dp, progress))
                .shadow(lerp(12.dp, 0.dp, progress), RoundedCornerShape(currentCornerRadius))
                .then(
                    if (data.state == PreviewState.PERSISTENT) {
                        Modifier.pointerInput(data.note.id) {
                            detectTapGestures { currentOnExpand() }
                        }
                    } else Modifier
                ),
            shape = RoundedCornerShape(currentCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = lerp(8.dp, 0.dp, progress))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (progress == 0f) {
                    if (data.state == PreviewState.SCRUBBING || data.allNotes.isEmpty()) {
                        Crossfade(
                            targetState = data.note, 
                            label = "preview",
                            animationSpec = tween(300)
                        ) { note ->
                            PreviewContent(note = note, progress = 0f)
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = data.state == PreviewState.PERSISTENT
                        ) { page ->
                            val note = data.allNotes[page]
                            PreviewContent(
                                note = note, 
                                progress = 0f,
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    if (page == pagerState.currentPage) {
                                        measuredHeight = with(density) { coords.size.height.toDp() }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    PreviewContent(note = data.note, progress = progress)
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(note: Note, progress: Float, modifier: Modifier = Modifier) {
    val titleSize = lerpText(16.sp, 28.sp, progress)
    val contentSize = lerpText(12.sp, 18.sp, progress)
    val padding = lerp(12.dp, 32.dp, progress)
    val lineSpacing = lerpText(16.sp, 26.sp, progress)

    Column(
        modifier = modifier
            .padding(padding)
            .fillMaxWidth()
    ) {
        Text(
            text = note.title.ifEmpty { "Untitled" },
            style = MaterialTheme.typography.headlineSmall,
            fontSize = titleSize,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = if (progress > 0.5f) 5 else 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(lerp(6.dp, 20.dp, progress)))
        Text(
            text = note.content,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = contentSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (progress > 0.5f) 50 else 6,
            overflow = TextOverflow.Ellipsis,
            lineHeight = lineSpacing
        )
    }
}

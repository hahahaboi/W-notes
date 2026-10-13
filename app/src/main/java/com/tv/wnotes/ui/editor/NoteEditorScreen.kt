package com.tv.wnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.deriveTabLabel
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.NoteTab
import com.tv.wnotes.ui.components.FolderAssignmentSheet
import com.tv.wnotes.ui.components.RenameNoteDialog
import com.tv.wnotes.ui.components.StapledNoteNavigationBar
import com.tv.wnotes.ui.home.HomeItem
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tv.wnotes.ui.editor.engine.logic.*
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    pathId: Long? = null,
    folderId: Long? = null,
    initialTabId: Long? = null,
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNavigateToNote: ((Long, Long?) -> Unit)? = null
) {
    var noteTitle by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf(folderId) }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    var editableNoteId by remember { mutableStateOf(noteId) }
    
    var showOverflowMenu by remember { mutableStateOf(value = false) }
    var showFolderSheet by remember { mutableStateOf(value = false) }
    var showRenameDialog by remember { mutableStateOf(value = false) }
    var showDialTitleDialog by remember { mutableStateOf(value = false) }
    var isDialSearching by remember { mutableStateOf(value = false) }
    var dialSearchQuery by remember { mutableStateOf("") }
    var dialSearchIndex by remember { mutableIntStateOf(0) }

    val folders by folderViewModel.allFolders.collectAsState()

    // Notes the user edited most recently, for the navigation dial's swipe page.
    val allNotes by viewModel.allNotes.collectAsState()
    val recentNotes = remember(allNotes, editableNoteId) {
        allNotes
            .filter { it.id != editableNoteId }
            .sortedByDescending { it.updatedAt }
            .take(3)
    }

    // --- STAPLED NOTE NAVIGATION STATE ---
    val notePaths by remember(editableNoteId) {
        if (editableNoteId != null) {
            linkPathViewModel.getPathsForNote(editableNoteId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val currentPathId: Long? = remember(pathId, notePaths) {
        pathId ?: notePaths.firstOrNull()?.id
    }

    val stapleItems by remember(currentPathId) {
        if (currentPathId != null) {
            linkPathViewModel.getItemsInPath(currentPathId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val stapleNotes = remember(stapleItems) {
        val list = mutableListOf<Note>()
        stapleItems.forEach { item ->
            when (item) {
                is HomeItem.NoteItem -> list.add(item.note)
                is HomeItem.GroupItem -> {
                    item.groupWithNotes.orderedNotes.forEach { note ->
                        list.add(note)
                    }
                }
            }
        }
        list
    }

    val currentStapleIndex = remember(stapleNotes, editableNoteId) {
        if (editableNoteId != null) stapleNotes.indexOfFirst { it.id == editableNoteId }
        else -1
    }

    val prevStapleNote = remember(stapleNotes, currentStapleIndex) {
        if (currentStapleIndex > 0) stapleNotes.getOrNull(currentStapleIndex - 1) else null
    }

    val nextStapleNote = remember(stapleNotes, currentStapleIndex) {
        if (currentStapleIndex >= 0 && currentStapleIndex < stapleNotes.size - 1) stapleNotes.getOrNull(currentStapleIndex + 1) else null
    }

    val showStapleNav = currentPathId != null && stapleNotes.isNotEmpty() && currentStapleIndex != -1

    val stapleCenterTitle = remember(noteTitle, currentNote, stapleNotes, currentStapleIndex) {
        if (noteTitle.isNotBlank()) {
            noteTitle
        } else {
            currentNote?.title?.ifBlank { null }
                ?: (if (currentStapleIndex >= 0) stapleNotes.getOrNull(currentStapleIndex)?.title?.ifBlank { null } else null)
                ?: "Untitled Note"
        }
    }

    // --- MULTI-TAB STATE ---
    val tabsFlow = remember(editableNoteId) {
        if (editableNoteId != null) viewModel.getTabsForNote(editableNoteId!!)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val tabs by tabsFlow.collectAsState(initial = emptyList())
    val tabTitles = remember { mutableStateMapOf<Long, String>() }

    val scope = rememberCoroutineScope()
    
    var activeTabId by remember(initialTabId) { mutableStateOf<Long?>(initialTabId) }

    val tabSnapshots = remember { mutableStateMapOf<Long, WEditorSnapshot>() }
    val editorHosts = remember { mutableStateMapOf<Long, WEditorHost>() }

    
    var showTabActionsMenu by remember { mutableStateOf(false) }
    var isStackedWorkspaceOpen by remember { mutableStateOf(false) }
    var tabToDeleteId by remember { mutableStateOf<Long?>(null) }
    
    var showAddToTabDialog by remember { mutableStateOf(false) }
    var selectedBlocksForAction by remember { mutableStateOf<List<WBlock>>(emptyList()) }

    var selectedTabIds by remember { mutableStateOf(setOf<Long>()) }
    var isTabSelectionMode by remember { mutableStateOf(false) }
    var showMergeConfirm by remember { mutableStateOf(false) }
    var showDeleteTabsConfirm by remember { mutableStateOf(false) }
    var showLimitReachedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty()) {
            tabs.forEach { tab ->
                if (!tabTitles.containsKey(tab.id)) {
                    tabTitles[tab.id] = tab.title ?: ""
                }
            }
            if (activeTabId == null || tabs.none { it.id == activeTabId }) {
                activeTabId = tabs.first().id
            }
        }
    }

    val tabListState = rememberLazyListState()
    
    LaunchedEffect(activeTabId) {
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index != -1) {
            tabListState.animateScrollToItem(index)
        }
    }

    @Composable
    fun GetOrCreateHost(tab: NoteTab): WEditorHost {
        val initialDoc = remember(tab.id) {
            // One block per line so paragraph formatting (headings, lists, alignment) applies per line.
            WDocument(
                id = tab.id.toString(),
                blocks = tab.content.split("\n")
                    .mapIndexed { i, line -> WTextBlock(id = if (i == 0) "init" else "init-$i", text = line) }
                    .toImmutableList()
            )
        }
        val host = rememberWEditorHost(
            initialDocument = initialDoc,
            initialSnapshot = tabSnapshots[tab.id],
            onCreateTabWithSelection = { blocks ->
                editableNoteId?.let { noteId ->
                    scope.launch {
                        val plainText = blocks.filterIsInstance<WTextContainer>().joinToString("\n") { it.text }
                        val newId = viewModel.addTabAndReturnId(noteId, content = plainText)
                        
                        if (newId != -1L) {
                            // Pre-warm the snapshot with rich blocks for the session
                            val doc = WDocument(id = newId.toString(), blocks = blocks.toImmutableList())
                            val tempState = WEditorState(initialDocument = doc)
                            tabSnapshots[newId] = tempState.snapshot.value
                            
                            activeTabId = newId
                        } else {
                            showLimitReachedDialog = true
                        }
                    }
                }
            },
            onAddToTab = { blocks ->
                selectedBlocksForAction = blocks
                showAddToTabDialog = true
            },
            onCommand = { cmd ->
                when (cmd) {
                    else -> {}
                }
            }
        )
        
        DisposableEffect(tab.id, host) {
            editorHosts[tab.id] = host
            onDispose {
                editorHosts.remove(tab.id)
            }
        }
        
        val snapshot by host.snapshot
        LaunchedEffect(snapshot) {
            tabSnapshots[tab.id] = snapshot
        }

        LaunchedEffect(snapshot.document.revision) {
            val currentContent = snapshot.document.blocks
                .asSequence()
                .filterIsInstance<WTextContainer>()
                .joinToString("\n") { it.text }
            
            if (tab.content != currentContent) {
                delay(800)
                viewModel.updateTab(tab.copy(content = currentContent))
            }
        }

        // Debounced tab title sync to DB
        val currentTitle = tabTitles[tab.id] ?: tab.title ?: ""
        LaunchedEffect(currentTitle) {
            if (currentTitle != tab.title) {
                delay(1000)
                viewModel.updateTabTitle(tab.id, currentTitle)
            }
        }
        
        return host
    }

    tabs.forEach { tab ->
        key(tab.id) {
            GetOrCreateHost(tab)
        }
    }

    LaunchedEffect(noteId) {
        if (noteId != null && noteId != -1L) {
            val note = viewModel.getNoteById(noteId)
            if (note != null) {
                currentNote = note
                editableNoteId = note.id
                noteTitle = note.title
                selectedFolderId = note.folderId
            }
        } else if (editableNoteId == null) {
            val newId = viewModel.createNoteAndReturnId("", "", folderId)
            editableNoteId = newId
            currentNote = viewModel.getNoteById(newId)
            selectedFolderId = folderId
            
            if (pathId != null) {
                linkPathViewModel.stapleNote(newId, pathId)
            }
        }
    }

    LaunchedEffect(noteTitle, selectedFolderId) {
        if (editableNoteId == null) return@LaunchedEffect
        if (noteTitle == currentNote?.title && selectedFolderId == currentNote?.folderId) return@LaunchedEffect
        
        delay(1000)
        
        if (currentNote?.title != noteTitle || currentNote?.folderId != selectedFolderId) {
            viewModel.updateNoteTitleAndFolder(editableNoteId!!, noteTitle, selectedFolderId)
            currentNote = currentNote?.copy(title = noteTitle, folderId = selectedFolderId)
        }
    }

    BackHandler(enabled = isStackedWorkspaceOpen || isTabSelectionMode) {
        if (isStackedWorkspaceOpen) {
            isStackedWorkspaceOpen = false
        } else if (isTabSelectionMode) {
            isTabSelectionMode = false
            selectedTabIds = emptySet()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isStackedWorkspaceOpen) {
                            isStackedWorkspaceOpen = false
                        } else {
                            onBack()
                        }
                    }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") 
                    }
                },
                actions = {
                    val folder = folders.find { it.id == selectedFolderId }
                    FolderBadge(label = folder?.name ?: "Uncategorized", icon = Icons.Outlined.Folder)
                    
                    IconButton(onClick = { 
                        currentNote?.let { viewModel.toggleStar(it) }
                    }) {
                        Icon(
                            imageVector = if (currentNote?.isStarred == true) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = "Star",
                            tint = if (currentNote?.isStarred == true) MaterialTheme.colorScheme.onSurface else LocalContentColor.current
                        )
                    }

                    IconButton(onClick = { showFolderSheet = true }) {
                        Icon(Icons.Outlined.Link, "Link")
                    }

                    IconButton(onClick = { showOverflowMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showOverflowMenu = false; showRenameDialog = true })
                    }
                }
            )
        },
        bottomBar = {
            if (editableNoteId != null) {
                BottomTabRow(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    tabTitles = tabTitles,
                    isSelectionMode = isTabSelectionMode,
                    selectedTabIds = selectedTabIds,
                    onTabSelected = { id ->
                        if (isTabSelectionMode) {
                            selectedTabIds = if (selectedTabIds.contains(id)) {
                                selectedTabIds - id
                            } else {
                                selectedTabIds + id
                            }
                            if (selectedTabIds.isEmpty()) {
                                isTabSelectionMode = false
                            }
                        } else {
                            activeTabId = id
                        }
                    },
                    onAddTab = { showTabActionsMenu = true },
                    onDirectAddTab = {
                        editableNoteId?.let { noteId ->
                            scope.launch {
                                val newId = viewModel.addTabAndReturnId(noteId)
                                if (newId != -1L) {
                                    activeTabId = newId
                                } else {
                                    showLimitReachedDialog = true
                                }
                            }
                        }
                    },
                    onLongPressTab = { id ->
                        isTabSelectionMode = true
                        selectedTabIds = setOf(id)
                    },
                    tabToDeleteId = tabToDeleteId,
                    onCancelDelete = { tabToDeleteId = null },
                    onConfirmDelete = { viewModel.moveTabToTrash(it); tabToDeleteId = null }
                )
                
                if (showTabActionsMenu) {
                    TabActionsMenu(
                        onDismiss = { showTabActionsMenu = false },
                        onAddTab = {
                            editableNoteId?.let { noteId ->
                                scope.launch {
                                    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it != -1 }
                                    val newId = viewModel.addTabAndReturnId(noteId, afterTabIndex = activeIndex)
                                    if (newId != -1L) {
                                        activeTabId = newId
                                    } else {
                                        showLimitReachedDialog = true
                                    }
                                    showTabActionsMenu = false
                                }
                            }
                        },
                        onOpenStackedTabs = {
                            isStackedWorkspaceOpen = true
                            showTabActionsMenu = false
                        },
                        onMergeTabs = {
                            if (selectedTabIds.size >= 2) {
                                showMergeConfirm = true
                            }
                            showTabActionsMenu = false
                        },
                        onDeleteTabs = {
                            if (selectedTabIds.isNotEmpty()) {
                                showDeleteTabsConfirm = true
                            } else {
                                isTabSelectionMode = true // Enter selection mode
                                isStackedWorkspaceOpen = true
                            }
                            showTabActionsMenu = false
                        },
                        selectedCount = selectedTabIds.size
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            androidx.compose.animation.AnimatedContent(
                targetState = activeTabId,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) + 
                     scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                    .togetherWith(fadeOut(animationSpec = tween(90)))
                },
                label = "TabSwitch"
            ) { targetTabId ->
                val host = targetTabId?.let { editorHosts[it] }
                if (host != null) {
                    WEditor(
                        host = host,
                        title = tabTitles[targetTabId] ?: "",
                        onTitleChange = { tabTitles[targetTabId] = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (showStapleNav) {
                StapledNoteNavigationBar(
                    noteTitle = stapleCenterTitle,
                    hasPrevious = prevStapleNote != null,
                    hasNext = nextStapleNote != null,
                    onPrevious = {
                        prevStapleNote?.let { target ->
                            if (onNavigateToNote != null) {
                                onNavigateToNote.invoke(target.id, currentPathId)
                            } else {
                                editableNoteId = target.id
                                activeTabId = null
                            }
                        }
                    },
                    onNext = {
                        nextStapleNote?.let { target ->
                            if (onNavigateToNote != null) {
                                onNavigateToNote.invoke(target.id, currentPathId)
                            } else {
                                editableNoteId = target.id
                                activeTabId = null
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }

            val activeHost = activeTabId?.let { editorHosts[it] }
            if (activeHost != null && !isStackedWorkspaceOpen) {
                // Find-in-note driven by the dial's search page.
                val activeBlocks = activeHost.snapshot.value.document.blocks
                val searchMatches = remember(activeBlocks, dialSearchQuery) {
                    findMatches(activeBlocks, dialSearchQuery)
                }
                val matchIndex = if (searchMatches.isEmpty()) -1 else dialSearchIndex.coerceIn(0, searchMatches.lastIndex)
                var dialHighlightsShown by remember { mutableStateOf(false) }
                LaunchedEffect(activeHost, isDialSearching, searchMatches, matchIndex) {
                    if (!isDialSearching) {
                        // Only clear highlights the dial put there, not ones from the marker sidebar's search.
                        if (dialHighlightsShown) {
                            activeHost.dispatchCommand(WEditorCommand.SetSearchHighlights(emptyList(), null))
                            dialHighlightsShown = false
                        }
                        return@LaunchedEffect
                    }
                    dialHighlightsShown = true
                    val active = searchMatches.getOrNull(matchIndex)
                    activeHost.dispatchCommand(WEditorCommand.SetSearchHighlights(searchMatches, active))
                    // Moving the cursor to the match scrolls it into view.
                    active?.let { activeHost.dispatchCommand(WEditorCommand.MoveCursor(it.anchor)) }
                }

                fun jumpCursor(target: (WDocument, WSelectionPoint) -> WSelectionPoint?) {
                    val snapshot = activeHost.snapshot.value
                    target(snapshot.document, snapshot.cursor.position)?.let {
                        activeHost.dispatchCommand(WEditorCommand.MoveCursor(it))
                    }
                }
                WNavigationDial(
                    recentNotes = recentNotes,
                    onChangeTitle = { showDialTitleDialog = true },
                    onPreviousParagraph = { jumpCursor(WCursorJumps::previousParagraph) },
                    onNextParagraph = { jumpCursor(WCursorJumps::nextParagraph) },
                    onPreviousWord = { jumpCursor(WCursorJumps::previousWord) },
                    onNextWord = { jumpCursor(WCursorJumps::nextWord) },
                    onOpenNote = { id ->
                        if (onNavigateToNote != null) {
                            onNavigateToNote.invoke(id, null)
                        } else {
                            editableNoteId = id
                            activeTabId = null
                        }
                    },
                    searchQuery = dialSearchQuery,
                    onSearchQueryChange = {
                        dialSearchQuery = it
                        dialSearchIndex = 0
                    },
                    searchResultLabel = when {
                        dialSearchQuery.isBlank() -> null
                        searchMatches.isEmpty() -> "No matches"
                        else -> "${matchIndex + 1} of ${searchMatches.size}"
                    },
                    onSearchPrevious = {
                        if (searchMatches.isNotEmpty()) {
                            dialSearchIndex = (matchIndex - 1 + searchMatches.size) % searchMatches.size
                        }
                    },
                    onSearchNext = {
                        if (searchMatches.isNotEmpty()) {
                            dialSearchIndex = (matchIndex + 1) % searchMatches.size
                        }
                    },
                    onSearchActiveChange = { active ->
                        isDialSearching = active
                        if (!active) {
                            dialSearchQuery = ""
                            dialSearchIndex = 0
                        }
                    }
                )
            }
        }
    }

    if (showDialTitleDialog) {
        val titleTabId = activeTabId
        RenameNoteDialog(
            initialTitle = titleTabId?.let { tabTitles[it] }?.ifBlank { null } ?: noteTitle,
            onDismiss = { showDialTitleDialog = false },
            onRename = { newTitle ->
                // The editor shows the tab title; the first tab's title also names the note.
                if (titleTabId != null) tabTitles[titleTabId] = newTitle
                if (titleTabId == null || tabs.firstOrNull()?.id == titleTabId) noteTitle = newTitle
                showDialTitleDialog = false
            }
        )
    }

    if (showFolderSheet) {
        FolderAssignmentSheet(
            folders = folders,
            onDismiss = { showFolderSheet = false },
            onAssign = { id -> selectedFolderId = id },
            onCreateFolder = { name, onCreated -> folderViewModel.addFolder(name, null, onCreated) }
        )
    }

    if (showRenameDialog) {
        RenameNoteDialog(
            initialTitle = noteTitle,
            onDismiss = { showRenameDialog = false },
            onRename = { noteTitle = it; showRenameDialog = false }
        )
    }

    if (showAddToTabDialog) {
        AddToTabDialog(
            tabs = tabs,
            tabTitles = tabTitles,
            onDismiss = { showAddToTabDialog = false },
            onTabSelected = { targetTabId ->
                scope.launch {
                    val targetTab = tabs.find { it.id == targetTabId } ?: return@launch
                    val blocksToAppend = selectedBlocksForAction
                    
                    // Update in-memory snapshot if it exists to preserve formatting during session
                    val existingHost = editorHosts[targetTabId]
                    if (existingHost != null) {
                        val currentSnapshot = existingHost.snapshot.value
                        val blocks = currentSnapshot.document.blocks.toMutableList()
                        
                        // Append blocks
                        blocks.addAll(blocksToAppend)
                        
                        existingHost.engineState.updateSnapshot(
                            currentSnapshot.copy(
                                document = currentSnapshot.document.copy(
                                    blocks = blocks.toImmutableList(),
                                    revision = currentSnapshot.document.revision + 1
                                )
                            )
                        )
                    } else {
                        // Host doesn't exist, update DB directly and update tabSnapshots map
                        val currentContent = targetTab.content
                        val appendText = blocksToAppend.filterIsInstance<WTextContainer>().joinToString("\n") { it.text }
                        val separator = if (currentContent.isBlank()) "" else if (currentContent.endsWith("\n")) "" else "\n"
                        val newContent = currentContent + separator + appendText
                        
                        viewModel.updateTab(targetTab.copy(content = newContent))
                        
                        val existingSnapshot = tabSnapshots[targetTabId]
                        if (existingSnapshot != null) {
                            val blocks = existingSnapshot.document.blocks.toMutableList()
                            blocks.addAll(blocksToAppend)
                            tabSnapshots[targetTabId] = existingSnapshot.copy(
                                document = existingSnapshot.document.copy(
                                    blocks = blocks.toImmutableList(),
                                    revision = existingSnapshot.document.revision + 1
                                )
                            )
                        }
                    }
                    
                    showAddToTabDialog = false
                }
            }
        )
    }

    if (showLimitReachedDialog) {
        AlertDialog(
            onDismissRequest = { showLimitReachedDialog = false },
            title = { Text("Tab limit reached") },
            text = { Text("This note can contain up to 10 tabs. Create a new note to continue.") },
            confirmButton = {
                TextButton(onClick = { showLimitReachedDialog = false }) { Text("OK") }
            }
        )
    }

    if (showMergeConfirm) {
        val sortedSelectedIds = tabs.filter { it.id in selectedTabIds }.sortedBy { it.orderIndex }.map { it.id }
        val destTab = tabs.find { it.id == sortedSelectedIds.first() }
        AlertDialog(
            onDismissRequest = { showMergeConfirm = false },
            title = { Text("Merge ${selectedTabIds.size} tabs?") },
            text = { Text("The contents will be combined into '${deriveTabLabel(destTab?.title, tabs.indexOf(destTab))}'. The other ${selectedTabIds.size - 1} tabs will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    editableNoteId?.let { noteId ->
                        val sourceIds = sortedSelectedIds.drop(1)
                        
                        val mergedBlocks = mutableListOf<WBlock>()
                        sortedSelectedIds.forEach { id ->
                            val snapshot = editorHosts[id]?.snapshot?.value ?: tabSnapshots[id]
                            if (snapshot != null) {
                                mergedBlocks.addAll(snapshot.document.blocks)
                            } else {
                                val tab = tabs.find { it.id == id }
                                if (tab != null) {
                                    mergedBlocks.add(WTextBlock(id = java.util.UUID.randomUUID().toString(), text = tab.content))
                                }
                            }
                        }

                        viewModel.mergeTabs(noteId, selectedTabIds.toList()) { finalDestId ->
                            val host = editorHosts[finalDestId]
                            if (host != null) {
                                val current = host.snapshot.value
                                host.engineState.updateSnapshot(current.copy(
                                    document = current.document.copy(
                                        blocks = mergedBlocks.toImmutableList(),
                                        revision = current.document.revision + 1
                                    )
                                ))
                            } else {
                                val existing = tabSnapshots[finalDestId]
                                if (existing != null) {
                                    tabSnapshots[finalDestId] = existing.copy(
                                        document = existing.document.copy(
                                            blocks = mergedBlocks.toImmutableList(),
                                            revision = existing.document.revision + 1
                                        )
                                    )
                                }
                            }
                            
                            sourceIds.forEach {
                                tabSnapshots.remove(it)
                                editorHosts.remove(it)
                                tabTitles.remove(it)
                            }
                            
                            activeTabId = finalDestId
                            isTabSelectionMode = false
                            selectedTabIds = emptySet()
                        }
                    }
                    showMergeConfirm = false
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { showMergeConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteTabsConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteTabsConfirm = false },
            title = { Text(if (selectedTabIds.size == 1) {
                val tab = tabs.find { it.id == selectedTabIds.first() }
                "Delete '${deriveTabLabel(tab?.title, tabs.indexOf(tab))}'?"
            } else "Delete ${selectedTabIds.size} tabs?") },
            text = { Text("These tabs and their contents will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    editableNoteId?.let { noteId ->
                        if (activeTabId in selectedTabIds) {
                            val sortedTabs = tabs.sortedBy { it.orderIndex }
                            val currentIndex = sortedTabs.indexOfFirst { it.id == activeTabId }
                            val remainingTabs = sortedTabs.filter { it.id !in selectedTabIds }
                            
                            activeTabId = if (remainingTabs.isNotEmpty()) {
                                val nextTab = sortedTabs.drop(currentIndex + 1).firstOrNull { it.id in remainingTabs.map { r -> r.id } }
                                nextTab?.id ?: remainingTabs.last().id
                            } else {
                                remainingTabs.firstOrNull()?.id ?: tabs.first().id
                            }
                        }
                        
                        viewModel.deleteTabs(noteId, selectedTabIds.toList())
                        
                        selectedTabIds.forEach {
                            tabSnapshots.remove(it)
                            editorHosts.remove(it)
                            tabTitles.remove(it)
                        }
                        
                        isTabSelectionMode = false
                        selectedTabIds = emptySet()
                    }
                    showDeleteTabsConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTabsConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (isStackedWorkspaceOpen && editableNoteId != null) {
        StackedTabsWorkspace(
            tabs = tabs,
            activeTabId = activeTabId,
            editorHosts = editorHosts,
            tabTitles = tabTitles,
            onTabSelected = { activeTabId = it },
            onClose = { isStackedWorkspaceOpen = false },
            onAddTab = {
                scope.launch {
                    val newId = viewModel.addTabAndReturnId(editableNoteId!!)
                    if (newId != -1L) {
                        activeTabId = newId
                        isStackedWorkspaceOpen = false
                    } else {
                        showLimitReachedDialog = true
                    }
                }
            },
            onTitleChange = { id, newTitle -> tabTitles[id] = newTitle }
        )
    }
}

@Composable
fun AddToTabDialog(
    tabs: List<NoteTab>,
    tabTitles: Map<Long, String>,
    onDismiss: () -> Unit,
    onTabSelected: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Tab") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tabs.isEmpty()) {
                    Text("No tabs available.")
                } else {
                    tabs.forEachIndexed { index, tab ->
                        ListItem(
                            headlineContent = { Text(deriveTabLabel(tabTitles[tab.id] ?: tab.title, index)) },
                            modifier = Modifier.clickable { onTabSelected(tab.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TabActionsMenu(
    onDismiss: () -> Unit,
    onAddTab: () -> Unit,
    onOpenStackedTabs: () -> Unit,
    onMergeTabs: () -> Unit,
    onDeleteTabs: () -> Unit,
    selectedCount: Int
) {
    Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() }) {
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 72.dp, end = 16.dp).width(220.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                DropdownMenuItem(
                    text = { Text("Add Tab") },
                    onClick = onAddTab,
                    leadingIcon = { Icon(Icons.Default.Add, null) }
                )
                DropdownMenuItem(
                    text = { Text("Stacked Tabs") },
                    onClick = onOpenStackedTabs,
                    leadingIcon = { Icon(Icons.Default.Layers, null) }
                )
                DropdownMenuItem(
                    text = { Text(if (selectedCount > 1) "Merge $selectedCount Tabs" else "Merge Tabs") },
                    onClick = onMergeTabs,
                    enabled = selectedCount >= 2,
                    leadingIcon = { Icon(Icons.Default.MergeType, null) }
                )
                DropdownMenuItem(
                    text = { Text(if (selectedCount > 0) "Delete $selectedCount Tabs" else "Delete Tab") },
                    onClick = onDeleteTabs,
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}

@Composable
fun BottomTabRow(
    tabs: List<NoteTab>,
    activeTabId: Long?,
    tabTitles: Map<Long, String>,
    isSelectionMode: Boolean,
    selectedTabIds: Set<Long>,
    onTabSelected: (Long) -> Unit,
    onAddTab: () -> Unit,
    onDirectAddTab: () -> Unit,
    onLongPressTab: (Long) -> Unit,
    tabToDeleteId: Long?,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (Long) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shadowElevation = 8.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIds.contains(tab.id)
                    val isActive = tab.id == activeTabId
                    
                    Box(modifier = Modifier.weight(1f)) {
                        TabItem(
                            title = deriveTabLabel(tabTitles[tab.id] ?: tab.title, index),
                            isActive = isActive,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            isDeleting = tabToDeleteId == tab.id,
                            onClick = { 
                                if (tabToDeleteId == tab.id) onCancelDelete() 
                                else onTabSelected(tab.id) 
                            },
                            onLongPress = { onLongPressTab(tab.id) },
                            onDelete = { onConfirmDelete(tab.id) }
                        )
                    }
                }

                // Compact Add Tab Button
                Surface(
                    onClick = onDirectAddTab,
                    modifier = Modifier.size(height = 36.dp, width = 36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Tab",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            IconButton(onClick = onAddTab) { Icon(Icons.Default.KeyboardArrowUp, "Actions") }
        }
    }
}

@Composable
fun TabItem(
    title: String,
    isActive: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) { 
                detectTapGestures(
                    onTap = { onClick() }, 
                    onLongPress = { onLongPress() }
                ) 
            },
        shape = RoundedCornerShape(16.dp),
        color = when {
            isDeleting -> MaterialTheme.colorScheme.errorContainer
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isDeleting) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.width(4.dp))
            } else if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isDeleting -> MaterialTheme.colorScheme.onErrorContainer
                    isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                    isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun FolderBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

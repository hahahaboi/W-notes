package com.tv.wnotes.ui.editor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.tv.wnotes.data.local.entity.LinkPath
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.entity.NoteTab
import com.tv.wnotes.ui.components.DraggableTextField
import com.tv.wnotes.ui.components.FolderAssignmentSheet
import com.tv.wnotes.ui.components.StapleAssignmentSheet
import com.tv.wnotes.ui.components.RenameNoteDialog
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.*
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.home.HomeItem
import kotlinx.collections.immutable.toImmutableList
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val useNextGenEditor = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    pathId: Long?,
    folderId: Long? = null,
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNavigateToNote: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    var selectedFolderId by remember { mutableStateOf<Long?>(folderId) }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    var editableNoteId by remember { mutableStateOf<Long?>(noteId) }
    
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showPathSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // --- MULTI-TAB STATE ---
    val tabs by if (editableNoteId != null) {
        viewModel.getTabsForNote(editableNoteId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<NoteTab>()) }
    }
    
    var activeTabId by remember { mutableStateOf<Long?>(null) }
    val tabSnapshots = remember { mutableStateMapOf<Long, WEditorSnapshot>() }
    val scope = rememberCoroutineScope()
    
    var lastTabCreatedFromSelection by remember { mutableStateOf(false) }

    // Automatically select the first tab if none is active
    LaunchedEffect(tabs, editableNoteId) {
        if (activeTabId == null) {
            if (tabs.isNotEmpty()) {
                activeTabId = tabs.first().id
            } else if (editableNoteId != null) {
                // Legacy note with no tabs? Create one.
                viewModel.addTab(editableNoteId!!, content = currentNote?.content ?: "")
            }
        }
    }

    val tabListState = rememberLazyListState()
    
    // Auto-scroll to active tab when it changes
    LaunchedEffect(activeTabId) {
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index != -1) {
            tabListState.animateScrollToItem(index)
        }
    }

    val activeTab = tabs.find { it.id == activeTabId }
    
    // Wait for tab data to load if we have an activeTabId
    // DELETED: handled above with annotations and markers

    val annotations by if (activeTabId != null) {
        viewModel.getAnnotationsForTab(activeTabId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(emptyList<ContextAnnotation>()) }
    }

    val markers by if (activeTabId != null) {
        viewModel.getMarkersForTab(activeTabId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(emptyList<com.tv.wnotes.data.local.entity.TextMarker>()) }
    }
    
    // Wait for data to load if we have an activeTabId
    if (activeTabId != null && (activeTab == null || annotations == null || markers == null)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Use the active tab ID as a key for the editor host so it swaps when tabs change
    val editorHost = rememberWEditorHost(
        initialDocument = WDocument(
            id = activeTabId?.toString() ?: "new",
            blocks = kotlinx.collections.immutable.persistentListOf(
                WTextBlock(id = "init", text = activeTab?.content ?: "")
            )
        ),
        initialAnnotations = annotations ?: emptyList(),
        initialMarkers = markers ?: emptyList(),
        initialSnapshot = activeTabId?.let { tabSnapshots[it] },
        onMakeTab = { extractedText ->
            editableNoteId?.let { noteId ->
                scope.launch {
                    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it != -1 }
                    val generatedTitle = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.generateTabTitle(extractedText)
                    val newId = viewModel.addTabAndReturnId(
                        noteId = noteId, 
                        title = generatedTitle,
                        content = extractedText, 
                        afterTabIndex = activeIndex
                    )
                    lastTabCreatedFromSelection = true
                    activeTabId = newId
                }
            }
        },
        initialCursorAtEnd = lastTabCreatedFromSelection
    )

    // Reset the flag after editor is remembered (it will reset on next recomposition where editorHost is recreated)
    SideEffect {
        if (lastTabCreatedFromSelection) {
            lastTabCreatedFromSelection = false
        }
    }

    val editorSnapshot by editorHost.snapshot
    LaunchedEffect(editorSnapshot, activeTabId) {
        activeTabId?.let { id ->
            tabSnapshots[id] = editorSnapshot
        }
    }

    // Persist Annotations
    LaunchedEffect(editorSnapshot.annotations, activeTabId) {
        activeTabId?.let { tabId ->
            viewModel.syncAnnotations(tabId, editorSnapshot.annotations)
        }
    }

    // Persist Markers
    LaunchedEffect(editorSnapshot.markers, activeTabId) {
        activeTabId?.let { tabId ->
            viewModel.syncMarkers(tabId, editorSnapshot.markers)
        }
    }
    // -----------------------

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val block = WMediaBlock(
                id = java.util.UUID.randomUUID().toString(),
                url = it.toString(),
                mediaType = MediaType.Image
            )
            editorHost.gestureCoordinator.onCommand(WEditorCommand.InsertBlock(block))
        }
    }

    // Final save on screen exit
    val currentTitle by rememberUpdatedState(title)
    val currentFolderId by rememberUpdatedState(selectedFolderId)
    val currentNoteState by rememberUpdatedState(currentNote)
    val currentEditableId by rememberUpdatedState(editableNoteId)
    val currentTabs by rememberUpdatedState(tabs)
    val currentTabSnapshots by rememberUpdatedState(tabSnapshots)

    DisposableEffect(Unit) {
        onDispose {
            val id = currentEditableId
            val note = currentNoteState
            if (id != null && note != null) {
                // Save all tabs from cache
                currentTabs.forEach { tab ->
                    val snapshot = currentTabSnapshots[tab.id]
                    if (snapshot != null) {
                        val finalTabContent = snapshot.document.blocks
                            .filterIsInstance<WTextContainer>()
                            .joinToString("\n") { it.text }
                        // Use blocking or a scope that outlives the component if possible.
                        // Since this is in onDispose, we should ideally use a global scope or 
                        // ensure the ViewModel scope handles it.
                        viewModel.updateTab(tab.copy(content = finalTabContent))
                    }
                }
                
                // Also update the main note record
                viewModel.updateNote(note.copy(
                    title = currentTitle.ifBlank { note.title },
                    folderId = currentFolderId,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }
    // ------------------------------
    
    val folders by folderViewModel.allFolders.collectAsState()
    val allPaths by linkPathViewModel.allPaths.collectAsState()

    val currentPaths by if (editableNoteId != null) {
        linkPathViewModel.getPathsForNote(editableNoteId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<LinkPath>()) }
    }

    val pathNotes by if (pathId != null) {
        linkPathViewModel.getNotesInPath(pathId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<Note>()) }
    }

    LaunchedEffect(noteId) {
        if (noteId != null && noteId != -1L) {
            val note = viewModel.getNoteById(noteId)
            if (note != null) {
                currentNote = note
                editableNoteId = note.id
                title = note.title
                selectedFolderId = note.folderId
            }
        }
    }

    // Auto-save logic
    LaunchedEffect(title, editorSnapshot.document.revision, selectedFolderId, activeTabId) {
        val currentContent = editorSnapshot.document.blocks
            .filterIsInstance<WTextContainer>()
            .joinToString("\n") { it.text }

        if (title.isBlank() && currentContent.isBlank() && editableNoteId == null) return@LaunchedEffect
        
        // Immediate save for folder/tab changes, debounced for text
        if (title == currentNote?.title && selectedFolderId == currentNote?.folderId && activeTabId == activeTab?.id) {
            delay(1000)
        }
        
        if (editableNoteId == null) {
            val newId = viewModel.createNoteAndReturnId(title, currentContent, selectedFolderId)
            editableNoteId = newId
            currentNote = viewModel.getNoteById(newId)
            
            // Auto-staple if created from a path
            if (pathId != null) {
                linkPathViewModel.stapleNote(newId, pathId)
            }
        } else {
            // Update the active tab if it exists
            activeTab?.let { tab ->
                if (tab.content != currentContent) {
                    viewModel.updateTab(tab.copy(content = currentContent))
                }
            }

            if (currentNote?.title != title || currentNote?.folderId != selectedFolderId) {
                viewModel.updateNoteTitleAndFolder(editableNoteId!!, title, selectedFolderId)
                currentNote = currentNote?.copy(title = title, folderId = selectedFolderId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                        val folder = folders.find { it.id == selectedFolderId }
                        FolderBadge(label = folder?.name ?: "Uncategorized", icon = Icons.Outlined.Folder)
                        
                        currentPaths.firstOrNull()?.let { path ->
                            Spacer(modifier = Modifier.height(4.dp))
                            FolderBadge(label = path.name, icon = Icons.Outlined.Link)
                        }
                    }
                    
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = com.tv.wnotes.ui.theme.ActionMenuShape
                    ) {
                        DropdownMenuItem(text = { Text("Add Image") }, onClick = { showOverflowMenu = false; imagePickerLauncher.launch("image/*") })
                        DropdownMenuItem(text = { Text("Add to Folder") }, onClick = { showOverflowMenu = false; showFolderSheet = true })
                        DropdownMenuItem(text = { Text("Staple") }, onClick = { showOverflowMenu = false; showPathSheet = true })
                        DropdownMenuItem(text = { Text(if (currentNote?.isStarred == true) "Unstar" else "Star") }, onClick = { currentNote?.let(viewModel::toggleStar); showOverflowMenu = false })
                        DropdownMenuItem(text = { Text(if (currentNote?.isPinned == true) "Unpin" else "Pin") }, onClick = { currentNote?.let(viewModel::togglePin); showOverflowMenu = false })
                        DropdownMenuItem(text = { Text(if (currentNote?.isDeleted == true) "Delete Permanently" else "Move to Trash") }, onClick = { 
                            currentNote?.let { if (it.isDeleted) viewModel.deletePermanently(it) else viewModel.moveToTrash(it) }
                            showOverflowMenu = false
                            onBack()
                        })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { currentNote?.let(viewModel::duplicate); showOverflowMenu = false })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showOverflowMenu = false; showRenameDialog = true })
                        DropdownMenuItem(text = { Text("Share") }, onClick = { 
                            val shareContent = editorSnapshot.document.blocks
                                .filterIsInstance<WTextContainer>()
                                .joinToString("\n") { it.text }
                            shareNote(context, currentNote, title, shareContent)
                            showOverflowMenu = false 
                        })
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Multi-Tab Bar
                if (editableNoteId != null) {
                    BottomTabRow(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        listState = tabListState,
                        onTabSelected = { activeTabId = it },
                        onAddTab = { 
                            editableNoteId?.let { noteId ->
                                scope.launch {
                                    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it != -1 }
                                    val newId = viewModel.addTabAndReturnId(noteId, afterTabIndex = activeIndex)
                                    activeTabId = newId
                                }
                            }
                        }
                    )
                }

                // Existing Path Navigation
                if (pathId != null && pathNotes.isNotEmpty() && editableNoteId != null) {
                    val currentIndex = pathNotes.indexOfFirst { it.id == editableNoteId }
                    if (currentIndex != -1) {
                        BottomAppBar(
                            modifier = Modifier.height(130.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Previous Note Group
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(
                                        onClick = { onNavigateToNote(pathNotes[currentIndex - 1].id, pathId) },
                                        enabled = currentIndex > 0,
                                        modifier = Modifier.height(40.dp)
                                    ) { 
                                        Text("« Prev", style = MaterialTheme.typography.labelLarge) 
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val newId = viewModel.createNoteAndReturnId("", "")
                                                linkPathViewModel.insertNoteInPath(pathId, newId, currentIndex)
                                                delay(250) 
                                                onNavigateToNote(newId, pathId)
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                
                                // Center Progress
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Text(
                                        "Step ${currentIndex + 1} of ${pathNotes.size}", 
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                // Next Note Group
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(
                                        onClick = { onNavigateToNote(pathNotes[currentIndex + 1].id, pathId) },
                                        enabled = currentIndex < pathNotes.size - 1,
                                        modifier = Modifier.height(40.dp)
                                    ) { 
                                        Text("Next »", style = MaterialTheme.typography.labelLarge) 
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val newId = viewModel.createNoteAndReturnId("", "")
                                                linkPathViewModel.insertNoteInPath(pathId, newId, currentIndex + 1)
                                                delay(250)
                                                onNavigateToNote(newId, pathId)
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (useNextGenEditor) {
                WEditor(
                    host = editorHost,
                    title = title,
                    onTitleChange = { title = it },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.headlineMedium
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    DraggableTextField(
                        value = contentValue,
                        onValueChange = { contentValue = it },
                        placeholder = { 
                            Text(
                                "Write here...", 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ) 
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    
                    // Real-time word count in editor
                    val words = if (contentValue.text.isBlank()) 0 else contentValue.text.trim().split(Regex("\\s+")).size
                    Text(
                        text = "$words words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showFolderSheet) {
        FolderAssignmentSheet(
            folders = folders,
            onDismiss = { showFolderSheet = false },
            onAssign = { id -> selectedFolderId = id },
            onCreateFolder = { name, onCreated -> folderViewModel.addFolder(name, null, onCreated) }
        )
    }

    if (showPathSheet) {
        StapleAssignmentSheet(
            paths = allPaths,
            onDismiss = { showPathSheet = false },
            onAssign = { pId ->
                editableNoteId?.let { noteId ->
                    if (pId == null) linkPathViewModel.unstapleNote(noteId)
                    else linkPathViewModel.stapleNote(noteId, pId)
                }
            },
            onCreatePath = { name, onCreated ->
                currentNote?.let { linkPathViewModel.createStaple(name, listOf(HomeItem.NoteItem(it)), onCreated) }
            }
        )
    }

    if (showRenameDialog) {
        RenameNoteDialog(
            initialTitle = title,
            onDismiss = { showRenameDialog = false },
            onRename = { 
                title = it
                showRenameDialog = false
            }
        )
    }
}

@Composable
fun BottomTabRow(
    tabs: List<NoteTab>,
    activeTabId: Long?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onTabSelected: (Long) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    TabItem(
                        title = getEffectiveTabTitle(tab),
                        isActive = isActive,
                        onClick = { onTabSelected(tab.id) }
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            IconButton(
                onClick = onAddTab,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Tab",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TabItem(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun getEffectiveTabTitle(tab: NoteTab): String {
    if (!tab.title.isNullOrBlank()) return tab.title
    
    val plainText = tab.content.take(25).replace("\n", " ").trim()
    return if (plainText.isEmpty()) "New Tab" else plainText
}

@Composable
private fun FolderBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun shareNote(context: android.content.Context, note: Note?, title: String, content: String) {
    val body = "${note?.title ?: title}\n\n$content"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share note"))
}

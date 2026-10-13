package com.tv.wnotes.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tv.wnotes.ui.components.NoteWorkspaceScreen
import com.tv.wnotes.ui.components.PinDialog
import com.tv.wnotes.ui.components.PinMode
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedNotesScreen(
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onNavigateToGroup: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    drawerProgress: Float
) {
    val lockedItems by viewModel.lockedItems.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val pathInfo by linkPathViewModel.allNotePathInfo.collectAsState()
    val groupPathInfo by linkPathViewModel.allGroupPathInfo.collectAsState()
    val lockedPin by viewModel.lockedPin.collectAsState()
    val scope = rememberCoroutineScope()
    
    var isUnlocked by remember { mutableStateOf(false) }
    var showPinEntry by remember { mutableStateOf(false) }
    var showPinEntryForNewNote by remember { mutableStateOf(false) }
    var itemIdToOpen by remember { mutableStateOf<Long?>(null) }
    var showPinReset by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val pathNames = remember(pathInfo) { 
        pathInfo.associate { it.noteId to it.pathName }
    }
    val groupPathNames = remember(groupPathInfo) {
        groupPathInfo.associate { it.groupId to it.pathName }
    }

    LaunchedEffect(lockedPin) {
        if (lockedPin != null && !isUnlocked) {
            showPinEntry = true
        }
    }

    if (lockedPin == null) {
        // First time, no items locked yet
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Locked Notes", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            com.tv.wnotes.ui.components.WAnimatedIcon(
                                progress = drawerProgress,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showPinEntryForNewNote = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Locked Item")
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Literal blank screen as requested
            }
        }
    } else if (!isUnlocked) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Locked Notes", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            com.tv.wnotes.ui.components.WAnimatedIcon(
                                progress = drawerProgress,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset Password") },
                                onClick = {
                                    showPinReset = true
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (showPinEntry) {
                    PinDialog(
                        mode = PinMode.ENTER,
                        correctPin = lockedPin,
                        onDismiss = { 
                            showPinEntry = false
                            onBack()
                        },
                        onConfirm = {
                            isUnlocked = true
                            showPinEntry = false
                        }
                    )
                } else {
                    Button(onClick = { showPinEntry = true }) {
                        Text("Unlock Section")
                    }
                }
            }
        }
    } else {
        NoteWorkspaceScreen(
            sectionKey = "locked",
            title = "Locked Notes",
            items = lockedItems,
            folderNames = folderNames,
            pathNames = pathNames,
            groupPathNames = groupPathNames,
            emptyMessage = "No locked items yet.",
            onBack = onBack,
            onNoteClick = { id ->
                itemIdToOpen = id
            },
            onNavigateToGroup = onNavigateToGroup,
            noteViewModel = viewModel,
            folderViewModel = folderViewModel,
            linkPathViewModel = linkPathViewModel,
            onOpenDrawer = onOpenDrawer,
            drawerProgress = drawerProgress,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showPinEntryForNewNote = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Locked Item")
                }
            }
        )
    }

    if (showPinEntryForNewNote) {
        PinDialog(
            mode = if (lockedPin == null) PinMode.SET else PinMode.ENTER,
            correctPin = lockedPin,
            onDismiss = { showPinEntryForNewNote = false },
            onConfirm = { pin ->
                showPinEntryForNewNote = false
                scope.launch {
                    if (lockedPin == null) viewModel.setLockedPin(pin)
                    val noteId = viewModel.createNoteAndReturnId("Untitled", "")
                    viewModel.getNoteById(noteId)?.let { note ->
                        viewModel.toggleLock(note)
                        // If we just set the PIN, the screen state will change to Unlocked
                        if (lockedPin == null) isUnlocked = true
                        onNoteClick(noteId)
                    }
                }
            }
        )
    }

    if (itemIdToOpen != null) {
        PinDialog(
            mode = PinMode.ENTER,
            correctPin = lockedPin,
            onDismiss = { itemIdToOpen = null },
            onConfirm = {
                onNoteClick(itemIdToOpen!!)
                itemIdToOpen = null
            }
        )
    }

    if (showPinReset) {
        PinDialog(
            mode = PinMode.RESET,
            correctPin = lockedPin,
            onDismiss = { showPinReset = false },
            onConfirm = {
                viewModel.setLockedPin(it)
                showPinReset = false
            }
        )
    }
}

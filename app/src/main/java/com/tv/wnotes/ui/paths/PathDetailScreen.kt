package com.tv.wnotes.ui.paths

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.ui.components.GroupCollageCard
import com.tv.wnotes.ui.components.HorizontalStapleConnector
import com.tv.wnotes.ui.components.PinDialog
import com.tv.wnotes.ui.components.PinMode
import com.tv.wnotes.ui.home.HomeItem
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathDetailScreen(
    pathId: Long,
    pathName: String,
    linkPathViewModel: LinkPathViewModel,
    folderViewModel: FolderViewModel,
    noteViewModel: NoteViewModel,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onNavigateToGroup: (Long, String) -> Unit,
    onOpenDrawer: () -> Unit,
    drawerProgress: Float
) {
    val databaseItems by remember(pathId) { linkPathViewModel.getItemsInPath(pathId) }.collectAsState(initial = emptyList())
    var editableItems by remember { mutableStateOf(emptyList<HomeItem>()) }
    
    val density = LocalDensity.current
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var deltaX by remember { mutableStateOf(0f) }

    LaunchedEffect(databaseItems) {
        if (draggingItemIndex == null) {
            val list = mutableListOf<HomeItem>()
            databaseItems.forEach { item ->
                when (item) {
                    is HomeItem.NoteItem -> list.add(item)
                    is HomeItem.GroupItem -> {
                        item.groupWithNotes.orderedNotes.forEach { note ->
                            list.add(HomeItem.NoteItem(note))
                        }
                    }
                }
            }
            editableItems = list
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(editableItems, searchQuery) {
        if (searchQuery.isBlank()) editableItems
        else editableItems.filter { item ->
            when (item) {
                is HomeItem.NoteItem -> {
                    item.note.title.contains(searchQuery, ignoreCase = true) || 
                    item.note.content.contains(searchQuery, ignoreCase = true)
                }
                is HomeItem.GroupItem -> {
                    item.groupWithNotes.group.name.contains(searchQuery, ignoreCase = true) ||
                    item.groupWithNotes.notes.any { it.title.contains(searchQuery, ignoreCase = true) || it.content.contains(searchQuery, ignoreCase = true) }
                }
            }
        }
    }

    var showEditItemsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val allNotes by noteViewModel.allNotes.collectAsState()
    val homeItems by noteViewModel.homeItems.collectAsState()
    val folders by folderViewModel.allFolders.collectAsState()
    val lockedPin by noteViewModel.lockedPin.collectAsState()
    
    var noteToLock by remember { mutableStateOf<Note?>(null) }
    var showPinSetupForNote by remember { mutableStateOf(false) }
    var showPinEntryForNote by remember { mutableStateOf(false) }

    val folderNames = remember(folders) { folders.associate { it.id to it.name } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pathName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "${editableItems.size} Items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Staple")
                    }
                    IconButton(onClick = { showEditItemsDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Edit Items")
                    }
                    TextButton(onClick = {
                        linkPathViewModel.updateOrder(pathId, editableItems)
                    }) {
                        Text("Save Order")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNoteClick(-1L) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search in path...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isEmpty()) "No items in this path" else "No matches found")
                }
            } else {
                if (searchQuery.isEmpty()) {
                    Text(
                        "Hold card to drag and reorder sequence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                            val isDragging = draggingItemIndex == index
                            val zIndex = if (isDragging) 1f else 0f
                            val translationX = if (isDragging) deltaX else 0f
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .zIndex(zIndex)
                                    .graphicsLayer { this.translationX = translationX }
                                    .pointerInput(filteredItems, searchQuery) {
                                        if (searchQuery.isEmpty()) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { draggingItemIndex = index },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    deltaX += dragAmount.x
                                                    
                                                    val itemWidthPx = with(density) { 200.dp.toPx() }
                                                    val targetIndex = if (deltaX > itemWidthPx / 2) index + 1 else if (deltaX < -itemWidthPx / 2) index - 1 else index

                                                    if (targetIndex in editableItems.indices && targetIndex != index) {
                                                        val newList = editableItems.toMutableList()
                                                        val movedItem = newList.removeAt(index)
                                                        newList.add(targetIndex, movedItem)
                                                        editableItems = newList
                                                        draggingItemIndex = targetIndex
                                                        deltaX = 0f
                                                        
                                                        // Persist the new order
                                                        linkPathViewModel.updateOrder(pathId, newList)
                                                    }
                                                },
                                                onDragEnd = { draggingItemIndex = null; deltaX = 0f },
                                                onDragCancel = { draggingItemIndex = null; deltaX = 0f }
                                            )
                                        }
                                    }
                            ) {
                                when (item) {
                                    is HomeItem.NoteItem -> {
                                        val note = item.note
                                        var showNoteMenu by remember { mutableStateOf(false) }
                                        Box {
                                            PathNoteCard(
                                                note = note,
                                                folderName = folderNames[note.folderId],
                                                onClick = { onNoteClick(note.id) },
                                                onLongPress = { showNoteMenu = true },
                                                isDragging = isDragging
                                            )
                                            DropdownMenu(
                                                expanded = showNoteMenu,
                                                onDismissRequest = { showNoteMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(if (note.isLocked) "Unlock" else "Lock") },
                                                    onClick = {
                                                        showNoteMenu = false
                                                        if (note.isLocked) {
                                                            noteViewModel.toggleLock(note)
                                                        } else {
                                                            noteToLock = note
                                                            if (lockedPin == null) showPinSetupForNote = true
                                                            else showPinEntryForNote = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    is HomeItem.GroupItem -> {
                                        // Expanded above
                                    }
                                }
                                
                                if (index < filteredItems.size - 1) {
                                    HorizontalStapleConnector()
                                }
                            }
                        }
                    }

                    com.tv.wnotes.ui.components.WScrollbar(
                        listState = rowState,
                        orientation = com.tv.wnotes.ui.components.WScrollbarOrientation.HORIZONTAL,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }
        }

        if (showEditItemsDialog) {
            val selectedItemIds = remember { mutableStateListOf<String>().apply { 
                addAll(editableItems.map { it.id })
            } }

            AlertDialog(
                onDismissRequest = { showEditItemsDialog = false },
                title = { Text("Edit Stapled Items") },
                text = {
                    Column {
                        Text("Select items to staple together:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val dialogListState = androidx.compose.foundation.lazy.rememberLazyListState()
                        Box(modifier = Modifier.height(300.dp)) {
                            LazyColumn(state = dialogListState) {
                                items(homeItems) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            if (selectedItemIds.contains(item.id)) selectedItemIds.remove(item.id)
                                            else selectedItemIds.add(item.id)
                                        }.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = selectedItemIds.contains(item.id), onCheckedChange = null)
                                        val label = when (item) {
                                            is HomeItem.NoteItem -> item.note.title
                                            is HomeItem.GroupItem -> "[Group] ${item.groupWithNotes.group.name}"
                                        }
                                        Text(label.ifEmpty { "Untitled" }, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                            com.tv.wnotes.ui.components.WScrollbar(
                                listState = dialogListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newItemList = homeItems.filter { selectedItemIds.contains(it.id) }
                        editableItems = newItemList
                        linkPathViewModel.updateOrder(pathId, newItemList)
                        showEditItemsDialog = false
                    }) { Text("Update") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditItemsDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showRenameDialog) {
            var value by remember { mutableStateOf(pathName) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Staple") },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = { Text("Staple name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (value.isNotBlank()) {
                            val path = linkPathViewModel.allPaths.value.find { it.id == pathId }
                            if (path != null) {
                                linkPathViewModel.renameStaple(path, value.trim())
                            }
                            showRenameDialog = false
                        }
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
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
}

@Composable
fun PathNoteCard(
    note: Note,
    folderName: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    isDragging: Boolean
) {
    val elevation by animateDpAsState(if (isDragging) 12.dp else 4.dp, label = "elev")
    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")

    Card(
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(1f)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Text(
                text = note.title.ifEmpty { "Untitled" },
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                fontSize = 11.sp,
                maxLines = 4,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            folderName?.let {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "📁 $it",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

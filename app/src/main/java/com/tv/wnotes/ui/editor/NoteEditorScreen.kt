package com.tv.wnotes.ui.editor

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tv.wnotes.ui.components.DraggableTextField
import com.tv.wnotes.ui.components.FolderAssignmentSheet
import com.tv.wnotes.ui.components.StapleAssignmentSheet
import com.tv.wnotes.ui.components.RenameNoteDialog
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.model.WTextBlock
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.viewmodel.FolderViewModel
import com.tv.wnotes.viewmodel.LinkPathViewModel
import com.tv.wnotes.viewmodel.NoteViewModel
import kotlinx.coroutines.delay

private const val useNextGenEditor = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    pathId: Long?,
    viewModel: NoteViewModel,
    folderViewModel: FolderViewModel,
    linkPathViewModel: LinkPathViewModel,
    onBack: () -> Unit,
    onNavigateToNote: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    var editableNoteId by remember { mutableStateOf<Long?>(noteId) }
    
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }
    var showPathSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Use a stable key for the editor host so it doesn't restart when a new note gets an ID.
    val initialNoteId = remember { noteId }
    val initialContent = remember { contentValue.text }

    // --- NEXT-GEN EDITOR TOGGLE ---
    val editorHost = rememberWEditorHost(
        initialDocument = WDocument(
            id = initialNoteId?.toString() ?: "new",
            blocks = kotlinx.collections.immutable.persistentListOf(
                WTextBlock(id = "init", text = initialContent)
            )
        )
    )

    // Keep contentValue in sync with WEditor internal state to trigger existing auto-save logic.
    val editorSnapshot by editorHost.snapshot
    LaunchedEffect(editorSnapshot.document.revision) {
        val mergedText = editorSnapshot.document.blocks
            .filterIsInstance<WTextContainer>()
            .joinToString("\n") { it.text }
        
        if (mergedText != contentValue.text) {
            contentValue = contentValue.copy(text = mergedText)
        }
    }

    // Final save on screen exit
    val currentTitle by rememberUpdatedState(title)
    val currentFolderId by rememberUpdatedState(selectedFolderId)
    val currentNoteState by rememberUpdatedState(currentNote)
    val currentEditableId by rememberUpdatedState(editableNoteId)

    DisposableEffect(Unit) {
        onDispose {
            val id = currentEditableId
            val note = currentNoteState
            if (id != null && note != null) {
                val finalContent = editorHost.snapshot.value.document.blocks
                    .filterIsInstance<WTextContainer>()
                    .joinToString("\n") { it.text }
                
                viewModel.updateNote(note.copy(
                    title = currentTitle.ifBlank { note.title },
                    content = finalContent,
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
                contentValue = TextFieldValue(note.content)
                selectedFolderId = note.folderId
                
                // Also update the next-gen editor engine if it's currently empty
                if (editorHost.snapshot.value.document.blocks.size <= 1 && 
                    editorHost.snapshot.value.document.blocks.firstOrNull()?.let { it is WTextBlock && it.text.isEmpty() } == true) {
                    editorHost.engineState.updateSnapshot(
                        editorHost.snapshot.value.copy(
                            document = editorHost.snapshot.value.document.copy(
                                id = note.id.toString(),
                                blocks = kotlinx.collections.immutable.persistentListOf(
                                    WTextBlock(id = "init", text = note.content)
                                )
                            )
                        )
                    )
                }
            }
        }
    }

    // Auto-save logic
    LaunchedEffect(title, contentValue.text, selectedFolderId) {
        if (title.isBlank() && contentValue.text.isBlank() && editableNoteId == null) return@LaunchedEffect
        delay(500) // Debounce save
        if (editableNoteId == null) {
            val newId = viewModel.createNoteAndReturnId(title, contentValue.text, selectedFolderId)
            editableNoteId = newId
            currentNote = viewModel.getNoteById(newId)
        } else {
            currentNote?.let {
                val updated = it.copy(
                    title = title.ifBlank { it.title },
                    content = contentValue.text,
                    folderId = selectedFolderId,
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.updateNote(updated)
                currentNote = updated
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
                        FolderBadge(label = folder?.name ?: "Uncategorized", icon = "📁")
                        
                        currentPaths.firstOrNull()?.let { path ->
                            Spacer(modifier = Modifier.height(4.dp))
                            FolderBadge(label = path.name, icon = "📎")
                        }
                    }
                    
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
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
                        DropdownMenuItem(text = { Text("Share") }, onClick = { shareNote(context, currentNote, title, contentValue.text); showOverflowMenu = false })
                    }
                }
            )
        },
        bottomBar = {
            if (pathId != null && pathNotes.isNotEmpty() && editableNoteId != null) {
                val currentIndex = pathNotes.indexOfFirst { it.id == editableNoteId }
                if (currentIndex != -1) {
                    BottomAppBar {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onNavigateToNote(pathNotes[currentIndex - 1].id, pathId) },
                                enabled = currentIndex > 0
                            ) { Text("« Prev") }
                            
                            Text("Step ${currentIndex + 1} of ${pathNotes.size}", style = MaterialTheme.typography.labelSmall)

                            TextButton(
                                onClick = { onNavigateToNote(pathNotes[currentIndex + 1].id, pathId) },
                                enabled = currentIndex < pathNotes.size - 1
                            ) { Text("Next »") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
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

            if (useNextGenEditor) {
                WEditor(
                    host = editorHost,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
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
            }
            
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
                currentNote?.let { linkPathViewModel.createStaple(name, listOf(it), onCreated) }
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
private fun FolderBadge(label: String, icon: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$icon $label", style = MaterialTheme.typography.labelSmall)
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

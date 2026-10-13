package com.tv.wnotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.data.local.entity.LinkPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderAssignmentSheet(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onAssign: (Long?) -> Unit,
    onCreateFolder: (String, (Long) -> Unit) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Add to Folder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onAssign(null)
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Uncategorized", style = MaterialTheme.typography.titleMedium)
            }
            val sheetScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                LazyColumn(
                    state = sheetScrollState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        TextButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("New Folder", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    items(folders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(folder.id)
                                    onDismiss()
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(folder.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                WScrollbar(
                    listState = sheetScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateNameDialog(
            title = "New Folder",
            placeholder = "Folder name",
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreateFolder(name) { id ->
                    onAssign(id)
                    onDismiss()
                }
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StapleAssignmentSheet(
    paths: List<LinkPath>,
    onDismiss: () -> Unit,
    onAssign: (Long?) -> Unit,
    onCreatePath: (String, (Long) -> Unit) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Staple Note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onAssign(null)
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Not Stapled", style = MaterialTheme.typography.titleMedium)
            }
            val sheetScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                LazyColumn(
                    state = sheetScrollState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        TextButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("New Staple", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    items(paths) { path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(path.id)
                                    onDismiss()
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(path.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                WScrollbar(
                    listState = sheetScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateNameDialog(
            title = "New Staple",
            placeholder = "Staple name",
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePath(name) { id ->
                    onAssign(id)
                    onDismiss()
                }
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreateNameDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isNotBlank()) onCreate(value.trim())
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

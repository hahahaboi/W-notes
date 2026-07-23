package com.tv.wnotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAssign(folder.id)
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${folder.icon} ${folder.name}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            TextButton(onClick = { showCreateDialog = true }) {
                Text("+ New Folder")
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(paths) { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAssign(path.id)
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📎 ${path.name}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            TextButton(onClick = { showCreateDialog = true }) {
                Text("+ New Staple")
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
private fun CreateNameDialog(
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

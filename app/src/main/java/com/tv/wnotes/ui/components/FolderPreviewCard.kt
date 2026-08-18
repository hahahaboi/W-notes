package com.tv.wnotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tv.wnotes.data.local.entity.Folder
import com.tv.wnotes.ui.home.HomeItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPreviewCard(
    folder: Folder,
    items: List<HomeItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    subfolderCount: Int = 0,
    isOpen: Boolean = false
) {
    PhysicalFolder(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleTap
            ),
        isOpen = isOpen
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(top = 12.dp) // Space for tab
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top // Align counts with the top of the title
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold, // Increased weight for "strongest text"
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp), // Added padding for truncation clearance
                    color = Color.DarkGray
                )
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$subfolderCount ${if (subfolderCount == 1) "folder" else "folders"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${items.size} ${if (items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (items.isEmpty()) {
                Text(
                    text = "No items in this folder yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                items.take(2).forEach { item ->
                    val (title, content) = when (item) {
                        is HomeItem.NoteItem -> item.note.title to item.note.content
                        is HomeItem.GroupItem -> ("[Group] " + item.groupWithNotes.group.name) to (item.groupWithNotes.notes.firstOrNull()?.content ?: "")
                    }
                    Text(
                        text = title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.DarkGray
                    )
                    Text(
                        text = content.ifBlank { "No preview available" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

package com.tv.wnotes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.ui.theme.noteTintFor
import com.tv.wnotes.util.NoteDisplayUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SquareNoteCard(
    note: Note,
    folderName: String?,
    linkPathName: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    displayTitleOverride: String? = null
) {
    val displayData = remember(note, displayTitleOverride) {
        NoteDisplayUtils.resolveNoteDisplayData(note, displayTitleOverride)
    }

    val tint = noteTintFor(note.id)
    val isDark = isSystemInDarkTheme()
    val accent = tint.accent

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (selected) BorderStroke(2.5.dp, accent) else BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) tint.dark else tint.light
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatUpdatedAt(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.hasGoal && note.content.length < note.goalTargetLength) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "!!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (note.isStarred) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = "Starred",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (note.isPinned) {
                Spacer(modifier = Modifier.height(4.dp))
                InfoPill(label = "Pinned", icon = Icons.Outlined.PushPin, accent = accent)
            }
            
            if (note.isRough) {
                Spacer(modifier = Modifier.height(4.dp))
                InfoPill(label = "Rough", icon = Icons.Outlined.EditNote, accent = accent)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = displayData.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayData.displayContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                folderName?.let {
                    InfoPill(label = it, icon = Icons.Outlined.Folder, accent = accent)
                }
                linkPathName?.let {
                    InfoPill(label = it, icon = Icons.Outlined.Link, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, icon: ImageVector? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
        contentColor = accent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = accent
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatUpdatedAt(timestamp: Long): String {
    return SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
}

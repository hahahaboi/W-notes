package com.tv.wnotes.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WAnnotationPopup(
    annotation: ContextAnnotation,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 280.dp)
            .padding(8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(
                        onClick = { 
                            // Open creation popup with existing text to simulate edit
                            onCommand(WEditorCommand.DismissAnnotation)
                            onCommand(
                                WEditorCommand.StartCreatingAnnotation(
                                    range = com.tv.wnotes.ui.editor.engine.state.WSelectionRange(
                                        anchor = com.tv.wnotes.ui.editor.engine.state.WSelectionPoint(annotation.blockId, annotation.startOffset),
                                        focus = com.tv.wnotes.ui.editor.engine.state.WSelectionPoint(annotation.blockId, annotation.endOffset)
                                    ),
                                    anchorText = annotation.anchorText
                                )
                            )
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCommand(WEditorCommand.DeleteAnnotation(annotation.id)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCommand(WEditorCommand.DismissAnnotation) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = annotation.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val date = remember(annotation.createdAt) {
                val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(annotation.createdAt))
            }
            
            Text(
                text = "Added on $date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

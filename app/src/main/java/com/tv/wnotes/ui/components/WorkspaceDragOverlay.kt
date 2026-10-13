package com.tv.wnotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tv.wnotes.ui.home.HomeItem
import kotlin.math.roundToInt

@Composable
fun WorkspaceDragOverlay(
    coordinator: WorkspaceDragCoordinator,
    folderNames: Map<Long, String>,
    pathNames: Map<Long, String>
) {
    val draggedItem = coordinator.draggedItem ?: return
    if (!coordinator.isActuallyDragging) return
    
    val bounds = coordinator.getBounds(draggedItem.id) ?: return
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Calculate global finger position
    val fingerX = bounds.left + coordinator.initialTouchPosition.x + coordinator.dragOffset.x
    val fingerY = bounds.top + coordinator.initialTouchPosition.y + coordinator.dragOffset.y

    Box(modifier = Modifier.fillMaxSize().zIndex(999f)) {
        Box(
            modifier = Modifier
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .offset {
                    // Position the card so its center is exactly at the finger position
                    IntOffset(
                        (fingerX - (bounds.width / 2f)).roundToInt(),
                        (fingerY - (bounds.height / 2f)).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = 0.6f
                    scaleY = 0.6f
                    shadowElevation = 12.dp.toPx()
                }
        ) {
            when (draggedItem) {
                is HomeItem.NoteItem -> {
                    SquareNoteCard(
                        note = draggedItem.note,
                        folderName = draggedItem.note.folderId?.let { folderNames[it] },
                        linkPathName = pathNames[draggedItem.note.id]
                    )
                }
                is HomeItem.GroupItem -> {
                    // Groups logic if needed
                }
            }
        }
    }
}

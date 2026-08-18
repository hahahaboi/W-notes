package com.tv.wnotes.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.Note
import kotlin.math.absoluteValue

@Composable
fun SwipeSelectableNoteCard(
    note: Note,
    folderName: String?,
    pathName: String?,
    selected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember(note.id) { mutableStateOf(0f) }
    var swipeMode by remember(note.id) { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(targetValue = offsetX, label = "offset")
    val elevation by animateDpAsState(targetValue = if (swipeMode || selected) 8.dp else 2.dp, label = "elevation")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(note.id, isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(note.id, isSelectionMode) {
                if (!isSelectionMode) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { 
                            swipeMode = true 
                        },
                        onDragEnd = {
                            if (offsetX > 150f) onSwipeRight()
                            else if (offsetX < -150f) onSwipeLeft()
                            swipeMode = false
                            offsetX = 0f
                        },
                        onDragCancel = {
                            swipeMode = false
                            offsetX = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-200f, 200f)
                    }
                }
            }
    ) {
        // Background icons
        Row(
            modifier = Modifier.fillMaxWidth().matchParentSize().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha((offsetX / 150f).coerceIn(0f, 1f))
            )
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(28.dp)
                    .alpha((-offsetX / 150f).coerceIn(0f, 1f))
            )
        }

        SquareNoteCard(
            note = note,
            folderName = folderName,
            linkPathName = pathName,
            selected = selected,
            modifier = Modifier
                .graphicsLayer { translationX = animatedOffset }
                .shadow(elevation, RoundedCornerShape(22.dp))
        )
    }
}

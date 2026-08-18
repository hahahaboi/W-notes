package com.tv.wnotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tv.wnotes.data.local.entity.Note
import com.tv.wnotes.data.local.model.GroupWithNotes
import com.tv.wnotes.util.TitleUtils
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

data class CardGeometry(
    val xOffset: Dp,
    val yOffset: Dp,
    val rotation: Float,
    val zIndex: Float,
    val width: Dp = 70.dp,
    val height: Dp = 100.dp
)

@Composable
fun GroupCollageCard(
    groupWithNotes: GroupWithNotes,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onNoteClick: (Note, CardGeometry, Offset) -> Unit,
    onPreviewStart: (Note, CardGeometry, Offset, List<Note>, Map<Long, CardGeometry>) -> Unit,
    onPreviewChange: (Note, CardGeometry) -> Unit,
    onPreviewEnd: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    folderName: String? = null,
    pathName: String? = null
) {
    var containerPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // Store geometries for hit-testing
    val cardGeometries = remember { mutableStateMapOf<Long, CardGeometry>() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (groupWithNotes.group.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = groupWithNotes.group.name.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )

                if (groupWithNotes.group.isStarred) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Starred",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val containerWidth = maxWidth
                val containerHeight = maxHeight
                val notes = groupWithNotes.orderedNotes
                val count = notes.size

                val containerWidthPx = with(density) { containerWidth.toPx() }
                val containerHeightPx = with(density) { containerHeight.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            containerPosition = coordinates.positionInWindow()
                        }
                        .pointerInput(notes, containerPosition) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var longPressTriggered = false
                                    val longPressJob = launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        longPressTriggered = true
                                        
                                        // Hit test for initial note
                                        val hitNote = findNoteAt(
                                            down.position,
                                            notes,
                                            cardGeometries,
                                            Size(containerWidthPx, containerHeightPx),
                                            density.density
                                        )
                                        
                                        hitNote?.let { (note, geo) ->
                                            onPreviewStart(note, geo, containerPosition, notes, cardGeometries.toMap())
                                        } ?: run {
                                            // No note hit, trigger group long press if needed
                                            onLongPress()
                                        }
                                    }

                                    var currentNoteId: Long? = null

                                    do {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    
                                    // Always consume if long press is active
                                    if (longPressTriggered) {
                                        changes.forEach { it.consume() }
                                    }

                                    if (changes.any { it.pressed }) {
                                        if (longPressTriggered) {
                                            val pos = changes.first().position
                                            val hitNote = findNoteAt(
                                                pos,
                                                notes,
                                                cardGeometries,
                                                Size(containerWidthPx, containerHeightPx),
                                                density.density
                                            )
                                            if (hitNote != null) {
                                                if (hitNote.first.id != currentNoteId) {
                                                    currentNoteId = hitNote.first.id
                                                    onPreviewChange(hitNote.first, hitNote.second)
                                                }
                                            }
                                        }
                                    } else {
                                        // Released
                                        longPressJob.cancel()
                                        if (longPressTriggered) {
                                            onPreviewEnd()
                                        } else {
                                            // Handle tap: Check if a card was hit
                                            // Ensure user hasn't moved too much (slop check)
                                            val up = changes.first()
                                            val distance = (up.position - down.position).getDistance()
                                            
                                            if (distance < viewConfiguration.touchSlop) {
                                                val hitNote = findNoteAt(
                                                    down.position,
                                                    notes,
                                                    cardGeometries,
                                                    Size(containerWidthPx, containerHeightPx),
                                                    density.density
                                                )
                                                if (hitNote != null) {
                                                    up.consume()
                                                    onNoteClick(hitNote.first, hitNote.second, containerPosition)
                                                } else {
                                                    onClick()
                                                }
                                            }
                                        }
                                    }
                                } while (changes.any { it.pressed })
                                }
                            }
                        }
                ) {
                    if (count > 0) {
                        val cardWidth = 70.dp
                        val cardHeight = 100.dp
                        val maxRotation = 15f
                        val horizontalSpacing = if (count > 1) {
                            val availableSpace = containerWidth - cardWidth
                            val maxSpacing = 30.dp
                            if (availableSpace < maxSpacing * (count - 1)) {
                                availableSpace / (count - 1)
                            } else {
                                maxSpacing
                            }
                        } else 0.dp

                        val totalFanWidth = if (count > 1) horizontalSpacing * (count - 1) + cardWidth else cardWidth
                        val startX = (containerWidth - totalFanWidth) / 2

                        notes.forEachIndexed { index, note ->
                            val geometry = remember(note.id, count, containerWidth) {
                                val rotation = if (count > 1) {
                                    val fraction = index.toFloat() / (count - 1)
                                    (fraction * 2 - 1) * maxRotation
                                } else 0f
                                
                                // Add a tiny random-looking tilt based on note ID for organic feel
                                val randomTilt = ((note.id % 7) - 3) * 0.5f

                                val xPos = startX + horizontalSpacing * index
                                val arcOffset = if (count > 1) {
                                    val fraction = index.toFloat() / (count - 1)
                                    val arcValue = (fraction * 2 - 1).absoluteValue.coerceIn(0f, 1f)
                                    (arcValue * arcValue * 8).dp
                                } else 4.dp

                                CardGeometry(
                                    xOffset = xPos,
                                    yOffset = arcOffset,
                                    rotation = rotation + randomTilt,
                                    zIndex = index.toFloat(),
                                    width = cardWidth,
                                    height = cardHeight
                                ).also { cardGeometries[note.id] = it }
                            }

                            GroupedMiniCard(
                                note = note,
                                geometry = geometry
                            )
                        }
                    }
                }
            }

            // Bottom Indicators
            if (folderName != null || pathName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (folderName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "📁 $folderName",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pathName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "📎 $pathName",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun findNoteAt(
    position: Offset,
    notes: List<Note>,
    geometries: Map<Long, CardGeometry>,
    containerSize: Size,
    density: Float
): Pair<Note, CardGeometry>? {
    // Search from topmost z-index to bottom
    for (i in notes.indices.reversed()) {
        val note = notes[i]
        val geo = geometries[note.id] ?: continue
        
        // Cards are positioned using Alignment.TopStart + offset in the inner Box
        // So geo.xOffset is the left edge relative to the Box top-left
        val w = geo.width.value * density
        val h = geo.height.value * density
        
        val cx = geo.xOffset.value * density + w / 2
        val cy = geo.yOffset.value * density + h / 2
        
        // Check if point is inside rotated rectangle
        val dx = position.x - cx
        val dy = position.y - cy
        
        val angleRad = -geo.rotation * (PI / 180.0).toFloat()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        
        val rx = dx * cosA - dy * sinA
        val ry = dx * sinA + dy * cosA
        
        if (abs(rx) <= w / 2 && abs(ry) <= h / 2) {
            return note to geo
        }
    }
    return null
}

@Composable
fun GroupedMiniCard(
    note: Note,
    geometry: CardGeometry
) {
    Surface(
        modifier = Modifier
            .offset(x = geometry.xOffset, y = geometry.yOffset)
            .zIndex(geometry.zIndex)
            .rotate(geometry.rotation)
            .size(width = geometry.width, height = geometry.height)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(8.dp),
                clip = false
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = TitleUtils.getShortTitle(note.title, wordCount = 2),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 12.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.isStarred) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

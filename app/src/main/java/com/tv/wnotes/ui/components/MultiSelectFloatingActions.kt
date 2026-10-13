package com.tv.wnotes.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun MultiSelectFloatingActions(
    visible: Boolean,
    onStar: () -> Unit,
    onTrash: () -> Unit,
    onShare: () -> Unit,
    onFolder: () -> Unit,
    onPath: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    isHomeScreen: Boolean = false,
    selectedCount: Int = 0,
    onInfo: (() -> Unit)? = null,
    onGroup: (() -> Unit)? = null,
    showInfoPanel: Boolean = false,
    onCloseInfo: () -> Unit = {},
    infoContent: @Composable (() -> Unit)? = null,
) {
    if (!visible) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    var expanded by remember { mutableStateOf(true) }
    var offset by remember { mutableStateOf(Offset(screenWidth - 350f, screenHeight - 650f)) }

    var isDragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 0.98f else 1f,
        animationSpec = spring(),
        label = "press_scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (showInfoPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { onCloseInfo() }
                    }
            )
        }

        // Info Panel
        if (showInfoPanel && infoContent != null) {
            Surface(
                modifier = Modifier
                    .offset { 
                        IntOffset(
                            offset.x.roundToInt().coerceIn(
                                with(density) { 16.dp.toPx() }.toInt(), 
                                (screenWidth - with(density) { 216.dp.toPx() }).toInt()
                            ), 
                            (offset.y - with(density) { 140.dp.toPx() }).roundToInt().coerceAtLeast(with(density) { 16.dp.toPx() }.toInt())
                        ) 
                    }
                    .widthIn(min = 200.dp, max = 280.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Info", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    infoContent()
                }
            }
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(expanded, screenWidth, screenHeight) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset = Offset(
                                x = (offset.x + dragAmount.x).coerceIn(0f, screenWidth - (if (expanded) with(density) { 360.dp.toPx() } else with(density) { 64.dp.toPx() })),
                                y = (offset.y + dragAmount.y).coerceIn(0f, screenHeight - with(density) { 150.dp.toPx() })
                            )
                        }
                    )
                }
                .animateContentSize(animationSpec = spring()),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            if (expanded) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedCount == 1 && onInfo != null) {
                        IconButton(onClick = onInfo) { 
                            Icon(
                                Icons.Outlined.Info, 
                                "Info",
                                tint = if (showInfoPanel) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            ) 
                        }
                    }

                    if (onGroup != null) {
                        IconButton(onClick = onGroup) {
                            Icon(Icons.Default.Layers, "Group Notes")
                        }
                    }

                    IconButton(onClick = onStar) { Icon(Icons.Default.Star, "Star") }
                    onPin?.let { IconButton(onClick = it) { Icon(Icons.Outlined.PushPin, "Pin") } }
                    IconButton(onClick = onFolder) { Icon(Icons.Default.Add, "Folder") }
                    onPath?.let { IconButton(onClick = it) { Icon(Icons.Default.AddCircle, "Staple") } }
                    onDuplicate?.let { IconButton(onClick = it) { Icon(Icons.Outlined.ContentCopy, "Duplicate") } }
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = onTrash) { Icon(Icons.Default.Delete, "Trash", tint = MaterialTheme.colorScheme.error) }
                    
                    VerticalDivider(modifier = Modifier.height(24.dp))
                    
                    IconButton(onClick = { expanded = false }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Minimize") 
                    }
                }
            } else {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Expand")
                }
            }
        }
    }
}

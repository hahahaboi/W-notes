package com.tv.wnotes.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
) {
    if (!visible) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    var expanded by remember { mutableStateOf(true) }
    var offset by remember { mutableStateOf(Offset(screenWidth - 350f, screenHeight - 650f)) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            x = (offset.x + dragAmount.x).coerceIn(0f, screenWidth - (if (expanded) 300f else 64f)),
                            y = (offset.y + dragAmount.y).coerceIn(0f, screenHeight - 150f)
                        )
                    }
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
                    IconButton(onClick = onStar) { Icon(Icons.Default.Star, "Star") }
                    onPin?.let { IconButton(onClick = it) { Text("📌") } }
                    IconButton(onClick = onFolder) { Icon(Icons.Default.Add, "Folder") }
                    onPath?.let { IconButton(onClick = it) { Icon(Icons.Default.AddCircle, "Staple") } }
                    onDuplicate?.let { IconButton(onClick = it) { Text("📋") } }
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

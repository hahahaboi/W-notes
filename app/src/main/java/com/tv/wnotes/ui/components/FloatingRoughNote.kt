package com.tv.wnotes.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.viewmodel.RoughNoteViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingRoughNote(
    viewModel: RoughNoteViewModel,
    onShowSnackbar: (String, () -> Unit) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val savedX by viewModel.floatingX.collectAsState(initial = null)
    val savedY by viewModel.floatingY.collectAsState(initial = null)
    val isCollapsed by viewModel.isCollapsed.collectAsState(initial = false)
    val dockedEdge by viewModel.dockedEdge.collectAsState(initial = null)
    
    val currentSize = if (isCollapsed) 28.dp else 56.dp
    val margin = 12.dp
    
    var offset by remember { 
        mutableStateOf(Offset(
            screenWidth - with(density) { (currentSize + margin).toPx() }, 
            screenHeight - with(density) { 280.dp.toPx() }
        )) 
    }
    
    LaunchedEffect(savedX, savedY) {
        if (savedX != null && savedY != null) {
            offset = Offset(savedX!!, savedY!!)
        }
    }

    var isPillOpen by remember { mutableStateOf(false) }
    var roughContent by remember { mutableStateOf("") }

    val animatedX by animateFloatAsState(
        targetValue = if (isCollapsed) {
            when (dockedEdge) {
                "left" -> with(density) { margin.toPx() }
                "right" -> screenWidth - with(density) { (currentSize + margin).toPx() }
                else -> offset.x
            }
        } else offset.x,
        animationSpec = spring(),
        label = "x"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPillOpen) {
            // Pill background to close
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (roughContent.isNotBlank()) {
                                viewModel.createRoughNote(roughContent)
                                val lastText = roughContent
                                onShowSnackbar("Saved to Trash") {
                                    roughContent = lastText
                                    isPillOpen = true
                                }
                            }
                            roughContent = ""
                            isPillOpen = false
                        }
                    }
            )

            Surface(
                modifier = Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .width(300.dp)
                    .animateContentSize(animationSpec = spring()),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Drag handle / Header
                    val pillWidthPx = with(density) { 300.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .pointerInput(screenWidth, pillWidthPx) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        offset = Offset(
                                            x = (offset.x + dragAmount.x).coerceIn(0f, screenWidth - pillWidthPx),
                                            y = (offset.y + dragAmount.y).coerceIn(0f, screenHeight - with(density) { 200.dp.toPx() })
                                        )
                                    },
                                    onDragEnd = {
                                        viewModel.savePosition(offset.x, offset.y)
                                    },
                                    onDragCancel = {
                                        viewModel.savePosition(offset.x, offset.y)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                        )
                    }

                    TextField(
                        value = roughContent,
                        onValueChange = { roughContent = it },
                        placeholder = { Text("Quick capture...", style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        } else {
            // Visual scale for press feedback
            var isPressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.9f else 1f,
                animationSpec = spring(),
                label = "press_scale"
            )

            Surface(
                modifier = Modifier
                    .offset { IntOffset(animatedX.roundToInt(), offset.y.roundToInt()) }
                    .size(currentSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .pointerInput(isCollapsed, screenWidth, screenHeight) {
                        detectDragGestures(
                            onDragStart = { isPressed = true },
                            onDragEnd = {
                                isPressed = false
                                // Snap to nearest edge
                                val currentSizePx = with(density) { currentSize.toPx() }
                                val snapX = if (offset.x < (screenWidth - currentSizePx) / 2) {
                                    with(density) { margin.toPx() }
                                } else {
                                    screenWidth - with(density) { (currentSize + margin).toPx() }
                                }
                                
                                offset = offset.copy(x = snapX)
                                if (isCollapsed) {
                                    val edge = if (offset.x < screenWidth / 2) "left" else "right"
                                    viewModel.saveCollapsedState(true, edge)
                                }
                                viewModel.savePosition(offset.x, offset.y)
                            },
                            onDragCancel = { isPressed = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentSizePx = with(density) { currentSize.toPx() }
                                offset = Offset(
                                    x = (offset.x + dragAmount.x).coerceIn(0f, screenWidth - currentSizePx),
                                    y = (offset.y + dragAmount.y).coerceIn(0f, screenHeight - currentSizePx)
                                )
                            }
                        )
                    }
                    .pointerInput(isCollapsed) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onTap = {
                                if (isCollapsed) viewModel.saveCollapsedState(false)
                                else isPillOpen = true
                            },
                            onDoubleTap = {
                                if (!isCollapsed) {
                                    val edge = if (offset.x < screenWidth / 2) "left" else "right"
                                    viewModel.saveCollapsedState(true, edge)
                                }
                            }
                        )
                    },
                shape = CircleShape,
                color = if (isCollapsed) Color.Transparent else MaterialTheme.colorScheme.secondary,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCollapsed) {
                        // Visual nub for collapsed state, but the Surface is hit area
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.onSecondary, CircleShape)
                            )
                        }
                    } else {
                        Text(
                            "R",
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

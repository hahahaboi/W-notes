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
    
    var offset by remember { mutableStateOf(Offset(screenWidth - 200f, screenHeight - 400f)) }
    
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
                "left" -> 0f
                "right" -> screenWidth - with(density) { 16.dp.toPx() }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offset = Offset(
                                        x = (offset.x + dragAmount.x).coerceIn(0f, screenWidth - 300f),
                                        y = (offset.y + dragAmount.y).coerceIn(0f, screenHeight - 150f)
                                    )
                                    viewModel.savePosition(offset.x, offset.y)
                                }
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
            Surface(
                modifier = Modifier
                    .offset { IntOffset(animatedX.roundToInt(), offset.y.roundToInt()) }
                    .size(if (isCollapsed) 16.dp else 64.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
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
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                // Snap to nearest edge
                                val snapX = if (offset.x < screenWidth / 2) 20f else screenWidth - 84f
                                offset = offset.copy(x = snapX)
                                viewModel.savePosition(offset.x, offset.y)
                            }
                        ) { change, dragAmount ->
                            if (!isCollapsed) {
                                change.consume()
                                offset = Offset(
                                    x = (offset.x + dragAmount.x).coerceIn(0f, screenWidth - 64f),
                                    y = (offset.y + dragAmount.y).coerceIn(0f, screenHeight - 64f)
                                )
                                viewModel.savePosition(offset.x, offset.y)
                            }
                        }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!isCollapsed) {
                        Text(
                            "R",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    }
                }
            }
        }
    }
}

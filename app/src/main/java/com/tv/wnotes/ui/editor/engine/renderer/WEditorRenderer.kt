package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.ui.editor.engine.logic.WDragInternalState
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WDragPhase
import com.tv.wnotes.ui.editor.editorGestures
import kotlinx.coroutines.flow.collectLatest

/**
 * High-level component for rendering a WEditor document.
 * Orchestrates block rendering and document-level overlays (selection/cursor).
 */
@Composable
fun WEditorRenderer(
    snapshot: WEditorSnapshot,
    layoutState: WLayoutState,
    editorCoordinates: LayoutCoordinates?,
    animController: WDragAnimationController,
    cursorBlinkVisible: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    title: String,
    onTitleChange: (String) -> Unit,
    gestureCoordinator: com.tv.wnotes.ui.editor.engine.logic.WGestureCoordinator,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onGestureDetected: (String) -> Unit,
    onCaretRendered: () -> Unit = {},
    onContentPositioned: (Offset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    
    // Auto-scrolling logic
    val density = LocalDensity.current
    var editorSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    
    LaunchedEffect(snapshot.drag.phase, snapshot.drag.fingerPosition, editorCoordinates) {
        if (snapshot.drag.phase == WDragPhase.DRAGGING && editorCoordinates != null) {
            val rootPos = editorCoordinates.positionInParent()
            val fingerY = snapshot.drag.fingerPosition.y - rootPos.y // Local Y relative to editor top
            
            val threshold = with(density) { 60.dp.toPx() }
            val viewportHeight = editorSize.height.toFloat()
            
            if (fingerY < threshold) {
                // Near top - Scroll Up
                val speed = ((threshold - fingerY) / threshold).coerceIn(0f, 1f) * 20f
                while (snapshot.drag.phase == WDragPhase.DRAGGING && scrollState.value > 0) {
                    scrollState.dispatchRawDelta(-speed)
                    kotlinx.coroutines.delay(16)
                }
            } else if (fingerY > viewportHeight - threshold) {
                // Near bottom - Scroll Down
                val speed = ((fingerY - (viewportHeight - threshold)) / threshold).coerceIn(0f, 1f) * 20f
                while (snapshot.drag.phase == WDragPhase.DRAGGING && scrollState.value < scrollState.maxValue) {
                    scrollState.dispatchRawDelta(speed)
                    kotlinx.coroutines.delay(16)
                }
            }
        }
    }

    // Cursor Auto-Scrolling logic
    LaunchedEffect(snapshot.cursor.position, snapshot.document.revision, editorSize) {
        if (editorSize.height <= 0 || snapshot.drag.phase != WDragPhase.IDLE) return@LaunchedEffect
        
        // Wait a frame for layout results to catch up if document changed
        kotlinx.coroutines.yield()
        
        val cursorRect = layoutState.getInsertionBeamForPoint(snapshot.cursor.position) ?: return@LaunchedEffect
        val viewportHeight = editorSize.height.toFloat()
        val margin = with(density) { 56.dp.toPx() } // Enough space to see the next/prev line
        val currentScroll = scrollState.value.toFloat()
        
        val viewportRelativeTop = cursorRect.top - currentScroll
        val viewportRelativeBottom = cursorRect.bottom - currentScroll
        
        if (viewportRelativeTop < margin) {
            // Cursor is near or above the visible top - Scroll Up
            val scrollDelta = viewportRelativeTop - margin
            scrollState.animateScrollTo(
                value = (currentScroll + scrollDelta).toInt().coerceAtLeast(0),
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
            )
        } else if (viewportRelativeBottom > viewportHeight - margin) {
            // Cursor is near or below the visible bottom - Scroll Down
            val scrollDelta = viewportRelativeBottom - (viewportHeight - margin)
            scrollState.animateScrollTo(
                value = (currentScroll + scrollDelta).toInt().coerceAtMost(scrollState.maxValue),
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
            )
        }
    }

    val currentPlaceholderAlpha by animController.placeholderAlpha.asState()
    var contentRelativeOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.onGloballyPositioned { editorSize = it.size }) {
        var documentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            TextField(
                value = title,
                onValueChange = onTitleChange,
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium) },
                modifier = Modifier
                    .fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.headlineMedium
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { 
                        documentCoordinates = it
                        editorCoordinates?.let { root ->
                            val localPos = root.localPositionOf(it, Offset.Zero)
                            contentRelativeOffset = localPos
                            onContentPositioned(localPos)
                        }
                    }
                    .editorGestures(
                        coordinator = gestureCoordinator,
                        selectionState = snapshot.selection,
                        dragPhase = snapshot.drag.phase,
                        focusRequester = focusRequester,
                        density = density.density,
                        onGestureDetected = onGestureDetected
                    )
            ) {
                Column {
                    snapshot.document.blocks.forEach { block ->
                        key(block.id) {
                            WBlockRenderer(
                                block = block,
                                snapshot = snapshot,
                                layoutState = layoutState,
                                documentCoordinates = documentCoordinates,
                                onTextLayout = { layoutState.layoutResults[block.id] = it },
                                onCommand = { gestureCoordinator.onCommand(it) },
                                placeholderAlpha = currentPlaceholderAlpha
                            )
                        }
                    }
                }
            }

            // Infinite Scrolling Spacer
            Spacer(modifier = Modifier.height(LocalConfiguration.current.screenHeightDp.dp))

            // Real-time word count in editor - MEMOIZED & OPTIMIZED
            val words by remember(snapshot.document.revision) {
                derivedStateOf {
                    var total = 0
                    snapshot.document.blocks.forEach { block ->
                        if (block is com.tv.wnotes.ui.editor.engine.model.WTextContainer) {
                            val text = block.text
                            if (text.isNotBlank()) {
                                total += text.trim().split(Regex("\\s+")).size
                            }
                        }
                    }
                    total
                }
            }
            Text(
                text = "$words words",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            )
        }

        // Overlay for Cursors and Selection Highlights
        EditorOverlayLayer(
            snapshot = snapshot,
            layoutState = layoutState,
            cursorBlinkVisible = cursorBlinkVisible,
            contentRelativeOffset = { contentRelativeOffset }
        )

        // Selection Handles - Also moved here for perfect sync and low latency
        if (snapshot.drag.phase == WDragPhase.IDLE) {
            WSelectionHandleRenderer(
                selectionState = snapshot.selection,
                contentRelativeOffset = { contentRelativeOffset },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Insertion Caret
        if (snapshot.drag.phase != WDragPhase.IDLE) {
            WInsertionCaretRenderer(
                caretState = snapshot.drag.insertionCaret,
                onRendered = onCaretRendered,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = contentRelativeOffset.x
                    translationY = contentRelativeOffset.y
                }
            )
        }
    }
}

@Composable
private fun EditorOverlayLayer(
    snapshot: WEditorSnapshot,
    layoutState: WLayoutState,
    cursorBlinkVisible: Boolean,
    contentRelativeOffset: () -> Offset
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
    val colorScheme = MaterialTheme.colorScheme
    val cursorColor = MaterialTheme.colorScheme.primary
    
    // MEMOIZE normalized selections to avoid O(N*M) calculation during Draw phase
    val normalizedRanges = remember(snapshot.selection.ranges, snapshot.document.blocks) {
        snapshot.selection.ranges.map { com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(it, snapshot.document.blocks) }
    }
    
    val normalizedHighlight = remember(snapshot.navigationHighlightRange, snapshot.document.blocks) {
        snapshot.navigationHighlightRange?.let { com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(it, snapshot.document.blocks) }
    }
    
    val highlightAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (snapshot.navigationHighlightRange != null) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = contentRelativeOffset()
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        val layoutResults = layoutState.layoutResults
        val blockOffsets = layoutState.blockOffsets
        val blocks = snapshot.document.blocks

        // 1. Draw Selection Highlights
        if (snapshot.selection.isActive) {
            normalizedRanges.forEach { normalized ->
                blocks.forEach { block ->
                    val layout = layoutResults[block.id] ?: return@forEach
                    val offset = blockOffsets[block.id] ?: Offset.Zero
                    
                    val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
                    val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
                    val currentIdx = blocks.indexOfFirst { it.id == block.id }

                    if (currentIdx in startIdx..endIdx && startIdx != -1 && endIdx != -1) {
                        val selectionStart = if (currentIdx == startIdx) normalized.anchor.offset else 0
                        val selectionEnd = if (currentIdx == endIdx) normalized.focus.offset else {
                            if (block is com.tv.wnotes.ui.editor.engine.model.WTextBlock) block.text.length 
                            else if (block is com.tv.wnotes.ui.editor.engine.model.WChecklistBlock) block.text.length
                            else 0
                        }

                        if (selectionStart < selectionEnd) {
                            val path = layout.getPathForRange(selectionStart, selectionEnd)
                            withTransform({
                                translate(offset.x, offset.y)
                            }) {
                                drawPath(path, selectionColor)
                            }
                        }
                    }
                }
            }
        }

        // 1a. Draw Persistent Marker Indicators
        snapshot.markers.forEach { marker ->
            val color = com.tv.wnotes.ui.editor.getMarkerTypeColor(marker.type, colorScheme).copy(alpha = 0.8f)

            blocks.forEach { block ->
                val layout = layoutResults[block.id] ?: return@forEach
                val offset = blockOffsets[block.id] ?: Offset.Zero

                val startIdx = blocks.indexOfFirst { it.id == marker.startBlockId }
                val endIdx = blocks.indexOfFirst { it.id == marker.endBlockId }
                val currentIdx = blocks.indexOfFirst { it.id == block.id }

                if (currentIdx in startIdx..endIdx && startIdx != -1 && endIdx != -1) {
                    val mStart = if (currentIdx == startIdx) marker.startOffset else 0
                    val mEnd = if (currentIdx == endIdx) marker.endOffset else {
                        if (block is com.tv.wnotes.ui.editor.engine.model.WTextBlock) block.text.length
                        else if (block is com.tv.wnotes.ui.editor.engine.model.WChecklistBlock) block.text.length
                        else 0
                    }

                    if (mStart < mEnd) {
                        val path = layout.getPathForRange(mStart, mEnd)
                        withTransform({
                            translate(offset.x, offset.y)
                        }) {
                            drawPath(
                                path = path,
                                color = color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 1b. Draw Navigation Highlight (Temporary)
        if (highlightAlpha > 0f) {
            normalizedHighlight?.let { normalized ->
                val color = highlightColor.copy(alpha = highlightColor.alpha * highlightAlpha)
                blocks.forEach { block ->
                    val layout = layoutResults[block.id] ?: return@forEach
                    val offset = blockOffsets[block.id] ?: Offset.Zero

                    val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
                    val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }
                    val currentIdx = blocks.indexOfFirst { it.id == block.id }

                    if (currentIdx in startIdx..endIdx && startIdx != -1 && endIdx != -1) {
                        val selectionStart = if (currentIdx == startIdx) normalized.anchor.offset else 0
                        val selectionEnd = if (currentIdx == endIdx) normalized.focus.offset else {
                            if (block is com.tv.wnotes.ui.editor.engine.model.WTextBlock) block.text.length
                            else if (block is com.tv.wnotes.ui.editor.engine.model.WChecklistBlock) block.text.length
                            else 0
                        }

                        if (selectionStart < selectionEnd) {
                            val path = layout.getPathForRange(selectionStart, selectionEnd)
                            withTransform({
                                translate(offset.x, offset.y)
                            }) {
                                drawPath(path, color)
                            }
                        }
                    }
                }
            }
        }

        // 2. Draw Cursor
        val cursorState = snapshot.cursor
        val isDragging = snapshot.drag.phase != WDragPhase.IDLE
        val cursorPosition = cursorState.position
        val cursorLayout = layoutResults[cursorPosition.blockId]
        val blockOffset = blockOffsets[cursorPosition.blockId] ?: Offset.Zero

        if (cursorLayout != null && cursorState.isVisible && cursorBlinkVisible && !isDragging) {
            val rect = cursorLayout.getCursorRect(cursorPosition.offset.coerceIn(0, cursorLayout.layoutInput.text.length))
            // Ensure minimum height for visibility
            val cursorHeight = if (rect.height < 5f) 20.dp.toPx() else rect.height
            drawRect(
                color = cursorColor,
                topLeft = rect.topLeft + blockOffset,
                size = rect.size.copy(width = 2.dp.toPx(), height = cursorHeight)
            )
        }
    }
}

package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.ui.editor.engine.logic.WDragInternalState
import com.tv.wnotes.ui.editor.engine.logic.WWordCountEngine
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WDragPhase
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.editorGestures
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds

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
    modifier: Modifier = Modifier,
    onCaretRendered: () -> Unit = {},
    onContentPositioned: (Offset) -> Unit = {},
) {
    // Stable wrapper for coordinates to prevent unnecessary recompositions
    val rootCoordinates = remember { WEditorRootCoordinates() }
    
    // Auto-scrolling logic
    val density = LocalDensity.current
    var editorSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    
    LaunchedEffect(snapshot.drag.phase, snapshot.drag.fingerPosition, editorCoordinates) {
        if ((snapshot.drag.phase == WDragPhase.DRAGGING) && (editorCoordinates != null)) {
            val rootPos = editorCoordinates.positionInParent()
            val fingerY = snapshot.drag.fingerPosition.y - rootPos.y // Local Y relative to editor top
            
            val threshold = with(density) { 60.dp.toPx() }
            val viewportHeight = editorSize.height.toFloat()
            
            if (fingerY < threshold) {
                // Near top - Scroll Up
                val speed = ((threshold - fingerY) / threshold).coerceIn(0f, 1f) * 20f
                while ((snapshot.drag.phase == WDragPhase.DRAGGING) && scrollState.value > 0) {
                    kotlinx.coroutines.delay(16.milliseconds)
                    scrollState.dispatchRawDelta(-speed)
                }
            } else if (fingerY > viewportHeight - threshold) {
                // Near bottom - Scroll Down
                val speed = ((fingerY - (viewportHeight - threshold)) / threshold).coerceIn(0f, 1f) * 20f
                while (snapshot.drag.phase == WDragPhase.DRAGGING && scrollState.value < scrollState.maxValue) {
                    scrollState.dispatchRawDelta(speed)
                    kotlinx.coroutines.delay(16.milliseconds)
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
    var boxRelativeOffset by remember { mutableStateOf(Offset.Zero) }
    // Initialize with a large value so it doesn't start "stuck" at (0,0) before measurement
    var toolbarRawY by remember { mutableFloatStateOf(2000f) }
    
    val horizontalScrollState = rememberScrollState()
    val zoom = snapshot.settings.zoom

    // Update layoutState with current zoom for hit-testing
    SideEffect {
        layoutState.zoom = zoom
    }

    // Pre-calculate dragged block IDs to optimize block rendering
    val draggedBlockIds by remember(snapshot.drag.phase, snapshot.drag.selectedRanges, snapshot.document.blocks) {
        derivedStateOf {
            if (snapshot.drag.phase == WDragPhase.IDLE || snapshot.drag.isCopy) emptySet()
            else {
                val blocks = snapshot.document.blocks
                val result = mutableSetOf<String>()
                snapshot.drag.selectedRanges.forEach { range ->
                    val norm = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, blocks)
                    val s = blocks.indexOfFirst { it.id == norm.anchor.blockId }
                    val e = blocks.indexOfFirst { it.id == norm.focus.blockId }
                    if (s != -1 && e != -1) {
                        for (i in s..e) result.add(blocks[i].id)
                    }
                }
                result
            }
        }
    }

    Box(modifier = modifier.onGloballyPositioned { editorSize = it.size }) {

        // 1. Zoomable Content Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { coords ->
                        rootCoordinates.value = coords
                        editorCoordinates?.let { root ->
                            val localPos = root.localPositionOf(coords, Offset.Zero)
                            contentRelativeOffset = localPos
                            onContentPositioned(localPos)
                        }
                    }
                    .let { base ->
                        if (editorSize.width > 0) {
                            base.width(with(density) { (editorSize.width.toFloat() / zoom).toDp() })
                        } else {
                            base.fillMaxWidth()
                        }
                    }
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Title section: its own panel, kept visually apart from the note body.
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WTitleEditor(
                        snapshot = snapshot,
                        title = title,
                        onTitleChange = onTitleChange,
                        layoutState = layoutState,
                        onCommand = { gestureCoordinator.onCommand(it) },
                        rootCoordinates = rootCoordinates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }

                // Visible gap between the title section and the toolbar.
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Placeholder for sticky toolbar
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .onGloballyPositioned { coords ->
                            // Calculate position relative to the root viewport Box
                            editorCoordinates?.let { root ->
                                toolbarRawY = root.localPositionOf(coords, Offset.Zero).y
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Separator band between the toolbar section and the notes section.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            rootCoordinates.value?.let { docRoot ->
                                boxRelativeOffset = docRoot.localPositionOf(coords, Offset.Zero)
                            }
                        }
                        .editorGestures(
                            coordinator = gestureCoordinator,
                            selectionState = snapshot.selection,
                            dragPhase = snapshot.drag.phase,
                            focusRequester = focusRequester,
                            density = density.density,
                            currentZoom = zoom,
                            verticalScrollState = scrollState,
                            horizontalScrollState = horizontalScrollState,
                            onGestureDetected = onGestureDetected,
                            coordinateTranslation = { boxRelativeOffset }
                        )
                ) {
                    val renderContext = WBlockRenderContext(
                        baseFontSize = snapshot.settings.fontSize,
                        baseFontFamily = snapshot.settings.fontFamily,
                        isDrawingMode = snapshot.isDrawingMode
                    )
                    CompositionLocalProvider(LocalWBlockRenderContext provides renderContext) {
                    Column {
                        snapshot.document.blocks.forEach { block ->
                            val blockAnnotations = snapshot.annotationIndex[block.id] ?: emptyList()
                            key(block.id) {
                                WBlockRenderer(
                                    block = block,
                                    annotations = blockAnnotations,
                                    layoutState = layoutState,
                                    rootCoordinates = rootCoordinates,
                                    onTextLayout = { layoutState.layoutResults[block.id] = it },
                                    onCommand = { gestureCoordinator.onCommand(it) },
                                    allBlocks = snapshot.document.blocks,
                                    isBeingDragged = draggedBlockIds.contains(block.id),
                                    placeholderAlpha = currentPlaceholderAlpha
                                )
                            }
                        }
                    }
                    }

                    // Render Goal Cursor and Light Gray Area if goal exists
                    val goal = snapshot.goal
                    if (goal != null) {
                        val goalBeam = layoutState.getInsertionBeamForPoint(WSelectionPoint(goal.blockId, goal.offset))
                        if (goalBeam != null) {
                            val goalY = goalBeam.top
                            val lastContentBlock = snapshot.document.blocks.lastOrNull { (it as? WTextContainer)?.text?.isNotBlank() == true }
                            val lastContentY = if (lastContentBlock != null) {
                                val blockOffset = layoutState.blockOffsets[lastContentBlock.id]?.y ?: 0f
                                val blockHeight = layoutState.blockHeights[lastContentBlock.id] ?: 50f
                                blockOffset + blockHeight
                            } else {
                                0f
                            }

                            if (goalY > lastContentY) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(with(density) { (goalY - lastContentY).toDp() })
                                        .offset(y = with(density) { lastContentY.toDp() })
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .offset(y = with(density) { (goalY - 16.dp.toPx()).toDp() })
                                    .pointerInput(goal) {
                                        detectDragGestures(
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val currentPos = goalBeam.center + Offset(0f, dragAmount.y)
                                                val point = layoutState.getSelectionPointForOffset(currentPos)
                                                if (point != null) {
                                                    gestureCoordinator.onCommand(WEditorCommand.MoveGoal(point))
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(goal) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                gestureCoordinator.onCommand(WEditorCommand.RemoveGoal)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    thickness = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                                ) {
                                    Text(
                                        text = "Goal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Infinite Scrolling Spacer
                Spacer(modifier = Modifier.height(LocalConfiguration.current.screenHeightDp.dp))

                // Real-time word count in editor - OPTIMIZED ALGORITHM
                val words by remember(snapshot.document.revision) {
                    derivedStateOf {
                        WWordCountEngine.countWords(snapshot.document.blocks)
                    }
                }
                Text(
                    text = "$words words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                )
            }

            // Overlay for Cursors and Selection Highlights (Scales with content)
            EditorOverlayLayer(
                snapshot = snapshot,
                layoutState = layoutState,
                cursorBlinkVisible = cursorBlinkVisible,
                contentRelativeOffset = { contentRelativeOffset }
            )

            // Selection Handles (Scales with content)
            if (snapshot.drag.phase == WDragPhase.IDLE) {
                WSelectionHandleRenderer(
                    selectionState = snapshot.selection,
                    contentRelativeOffset = { contentRelativeOffset },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Insertion Caret (Scales with content)
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

        // 2. Sticky Toolbar Overlay (OUTSIDE zoomable box, STAYS UNSCALED)
        val isStuck = toolbarRawY <= 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { 
                    // When toolbarRawY > 0, it follows scroll. When <= 0, it stays at 0.
                    IntOffset(0, toolbarRawY.coerceAtLeast(0f).toInt()) 
                }
                .padding(horizontal = 16.dp)
                .then(if (isStuck) Modifier.shadow(4.dp) else Modifier)
        ) {
            com.tv.wnotes.ui.editor.WFormattingToolbar(
                snapshot = snapshot,
                onCommand = { gestureCoordinator.onCommand(it) }
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
    val copyArmedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
    val searchHighlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
    val activeSearchHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val colorScheme = MaterialTheme.colorScheme
    val cursorColor = MaterialTheme.colorScheme.primary
    
    // MEMOIZE normalized selections to avoid O(N*M) calculation during Draw phase
    val normalizedRanges = remember(snapshot.selection.ranges, snapshot.document.blocks) {
        snapshot.selection.ranges.map { com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(it, snapshot.document.blocks) }
    }

    val isCopyMode = snapshot.drag.phase == WDragPhase.COPY_ARMED || (snapshot.drag.phase != WDragPhase.IDLE && snapshot.drag.isCopy)
    
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

        val drawRangeHighlight: (WSelectionRange, Color) -> Unit = { range, color ->
            val normalized = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, blocks)
            if (normalized.anchor.blockId == "W_NOTE_TITLE") {
                layoutState.titleLayoutResult?.let { layout ->
                    val textLength = layout.layoutInput.text.length
                    val start = normalized.anchor.offset.coerceIn(0, textLength)
                    val end = if (normalized.focus.blockId == "W_NOTE_TITLE") {
                        normalized.focus.offset.coerceIn(0, textLength)
                    } else {
                        textLength
                    }.coerceIn(0, textLength)
                    if (start < end) {
                        layout.selectionPathOrNull(start, end)?.let { path ->
                            val titleOffset = layoutState.titleOffset
                            withTransform({
                                translate(titleOffset.x, titleOffset.y)
                            }) {
                                drawPath(path, color)
                            }
                        }
                    }
                }
            } else {
                val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
                val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }

                if (startIdx != -1 && endIdx != -1) {
                    for (i in startIdx..endIdx) {
                        val block = blocks[i]
                        val layout = layoutResults[block.id] ?: continue
                        val offset = blockOffsets[block.id] ?: Offset.Zero

                        val textLength = layout.layoutInput.text.length
                        val hStart = if (i == startIdx) normalized.anchor.offset.coerceIn(0, textLength) else 0
                        val hEnd = if (i == endIdx) normalized.focus.offset.coerceIn(0, textLength) else textLength

                        if (hStart < hEnd) {
                            layout.selectionPathOrNull(hStart, hEnd)?.let { path ->
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
        }

        // Draw Search Highlights
        val activeSearchRange = snapshot.activeSearchHighlightRange
        snapshot.searchHighlightRanges.forEach { range ->
            if (range != activeSearchRange) {
                drawRangeHighlight(range, searchHighlightColor)
            }
        }
        if (activeSearchRange != null) {
            drawRangeHighlight(activeSearchRange, activeSearchHighlightColor)
        }

        // 1. Draw Selection Highlights
        if (snapshot.selection.isActive) {
            val currentColor = if (isCopyMode) copyArmedColor else selectionColor
            normalizedRanges.forEach { normalized ->
                
                // Handle Title Selection
                if (normalized.anchor.blockId == "W_NOTE_TITLE") {
                    layoutState.titleLayoutResult?.let { layout ->
                        val textLength = layout.layoutInput.text.length
                        val start = normalized.anchor.offset.coerceIn(0, textLength)
                        val end = if (normalized.focus.blockId == "W_NOTE_TITLE") {
                            normalized.focus.offset.coerceIn(0, textLength)
                        } else {
                            textLength
                        }.coerceIn(0, textLength)
                        if (start < end) {
                            layout.selectionPathOrNull(start, end)?.let { path ->
                                val titleOffset = layoutState.titleOffset
                                withTransform({
                                    translate(titleOffset.x, titleOffset.y)
                                }) {
                                    drawPath(path, currentColor)
                                }
                            }
                        }
                    }
                    if (normalized.focus.blockId == "W_NOTE_TITLE") return@forEach
                }

                val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
                val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }

                // Stale ranges are ignored during draw phase to prevent crashes or visual artifacts
                if (startIdx != -1 && endIdx != -1) {
                    for (i in startIdx..endIdx) {
                        val block = blocks[i]
                        val layout = layoutResults[block.id] ?: continue
                        val offset = blockOffsets[block.id] ?: Offset.Zero

                        val textLength = layout.layoutInput.text.length
                        val selectionStart = if (i == startIdx) normalized.anchor.offset.coerceIn(0, textLength) else 0
                        val selectionEnd = if (i == endIdx) normalized.focus.offset.coerceIn(0, textLength) else textLength

                        if (selectionStart < selectionEnd) {
                            layout.selectionPathOrNull(selectionStart, selectionEnd)?.let { path ->
                                withTransform({
                                    translate(offset.x, offset.y)
                                }) {
                                    drawPath(path, currentColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1a. Draw Persistent Marker Indicators
        snapshot.markers.forEach { marker ->
            val color = com.tv.wnotes.ui.editor.getMarkerTypeColor(marker.type, colorScheme).copy(alpha = 0.8f)

            val startIdx = blocks.indexOfFirst { it.id == marker.startBlockId }
            val endIdx = blocks.indexOfFirst { it.id == marker.endBlockId }

            if (startIdx != -1 && endIdx != -1) {
                for (i in startIdx..endIdx) {
                    val block = blocks[i]
                    val layout = layoutResults[block.id] ?: continue
                    val offset = blockOffsets[block.id] ?: Offset.Zero

                    val textLength = layout.layoutInput.text.length
                    val mStart = if (i == startIdx) marker.startOffset.coerceIn(0, textLength) else 0
                    val mEnd = if (i == endIdx) marker.endOffset.coerceIn(0, textLength) else textLength

                    if (mStart < mEnd) {
                        layout.selectionPathOrNull(mStart, mEnd)?.let { path ->
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
        }

        // 1b. Draw Navigation Highlight (Temporary)
        if (highlightAlpha > 0f) {
            normalizedHighlight?.let { normalized ->
                val color = highlightColor.copy(alpha = highlightColor.alpha * highlightAlpha)
                
                val startIdx = blocks.indexOfFirst { it.id == normalized.anchor.blockId }
                val endIdx = blocks.indexOfFirst { it.id == normalized.focus.blockId }

                if (startIdx != -1 && endIdx != -1) {
                    for (i in startIdx..endIdx) {
                        val block = blocks[i]
                        val layout = layoutResults[block.id] ?: continue
                        val offset = blockOffsets[block.id] ?: Offset.Zero

                        val textLength = layout.layoutInput.text.length
                        val selectionStart = if (i == startIdx) normalized.anchor.offset.coerceIn(0, textLength) else 0
                        val selectionEnd = if (i == endIdx) normalized.focus.offset.coerceIn(0, textLength) else textLength

                        if (selectionStart < selectionEnd) {
                            layout.selectionPathOrNull(selectionStart, selectionEnd)?.let { path ->
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
        }

        // 2. Draw Cursor
        val cursorState = snapshot.cursor
        val isDragging = snapshot.drag.phase != WDragPhase.IDLE
        val cursorPosition = cursorState.position
        val cursorLayout = layoutResults[cursorPosition.blockId]
        val blockOffset = blockOffsets[cursorPosition.blockId] ?: Offset.Zero

        if (cursorLayout != null && cursorState.isVisible && cursorBlinkVisible && !isDragging) {
            val validOffset = cursorLayout.validOffsetOrNull(cursorPosition.offset) ?: return@Canvas
            val rect = cursorLayout.getCursorRect(validOffset)
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

package com.tv.wnotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.center
import kotlin.math.roundToInt
import kotlin.math.sign
import com.tv.wnotes.ui.editor.engine.renderer.WDragPreviewRenderer
import com.tv.wnotes.ui.editor.engine.renderer.WDragAnimationController
import com.tv.wnotes.ui.editor.engine.state.*
import com.tv.wnotes.ui.editor.engine.model.SpanType
import com.tv.wnotes.ui.editor.engine.model.WTextContainer
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.logic.WLayoutProvider
import com.tv.wnotes.ui.editor.engine.logic.WSelectionManager

val SelectionToolbarBg = Color(0xFF1A1A1A)
val SelectionToolbarContent = Color(0xFFE8E8E8)
val SelectionToolbarMuted = Color(0xFF9E9E9E)
val SelectionToolbarDivider = Color.White.copy(alpha = 0.09f)
val SelectionToolbarBorder = Color.White.copy(alpha = 0.08f)
val SelectionToolbarActive = Color.White.copy(alpha = 0.14f)
val SelectionToolbarWell = Color.White.copy(alpha = 0.05f)

/**
 * Separate layer responsible for Cursor, Selection, Handles, Drag Previews, etc.
 * Initially renders nothing.
 */
@Composable
fun WEditorOverlay(
    snapshot: WEditorSnapshot,
    layoutState: WLayoutProvider,
    animController: WDragAnimationController,
    onCommand: (WEditorCommand) -> Unit,
    contentRelativeOffset: () -> Offset,
    modifier: Modifier = Modifier
) {
    val isDragging = snapshot.drag.phase != WDragPhase.IDLE

    Box(modifier = modifier.fillMaxSize()) {

        // Drag Preview
        WDragPreviewRenderer(
            previewState = snapshot.drag.preview,
            animController = animController,
            modifier = Modifier.fillMaxSize()
        )

        // Multi-Selection Toolbar (Redesigned)
        if (snapshot.selection.isActive && !isDragging) {
            Box(modifier = Modifier.fillMaxSize()) {
                MultiSelectionToolbar(
                    snapshot = snapshot,
                    layoutState = layoutState,
                    onCommand = onCommand,
                    modifier = Modifier.imePadding()
                )
            }
        }

        // Annotation Input Popup
        if (snapshot.isCreatingAnnotation) {
            AnnotationInputPopup(
                snapshot = snapshot,
                onCommand = onCommand,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // View Annotation Popup
        snapshot.activeAnnotationId?.let { id ->
            val annotation = snapshot.annotations.find { it.id == id }
            if (annotation != null) {
                val geometry = layoutState.getAnnotationGeometry(id)
                if (geometry != null) {
                    val editorOffset = contentRelativeOffset()

                    // Physical screen position of the annotated text (relative to overlay root)
                    val targetX = geometry.boundingRect.center.x + editorOffset.x
                    val targetY = geometry.boundingRect.top + editorOffset.y

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val overlayWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
                        var popupWidthPx by remember(id, density) {
                            mutableFloatStateOf(with(density) { 280.dp.toPx() })
                        }

                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { popupWidthPx = it.size.width.toFloat() }
                                .graphicsLayer {
                                    val horizontalPadding = 16.dp.toPx()
                                    val maxX = (overlayWidthPx - popupWidthPx - horizontalPadding)
                                        .coerceAtLeast(horizontalPadding)

                                    translationX = (targetX - popupWidthPx / 2f)
                                        .coerceIn(horizontalPadding, maxX.coerceAtLeast(horizontalPadding))

                                    val popupHeight = 140.dp.toPx()
                                    val preferredY = targetY - popupHeight - 8.dp.toPx()
                                    translationY = if (preferredY < 16.dp.toPx()) {
                                        geometry.boundingRect.bottom + editorOffset.y + 8.dp.toPx()
                                    } else {
                                        preferredY
                                    }
                                }
                        ) {
                            WAnnotationPopup(
                                annotation = annotation,
                                onCommand = onCommand
                            )
                        }
                    }
                }
            }
        }

        // Add Mode Hint (Floating Cancel Button)
        if (snapshot.selection.interactionMode == WSelectionInteraction.ADD_RANGE) {
            Surface(
                onClick = { onCommand(WEditorCommand.ClearSelection) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .size(36.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = SelectionToolbarBg,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, SelectionToolbarBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Selection",
                        tint = SelectionToolbarContent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Marker Jump
        if (snapshot.isQuickJumpOpen) {
            WMarkerQuickJump(
                snapshot = snapshot,
                onCommand = onCommand
            )
        }
    }
}

@Composable
private fun MultiSelectionToolbar(
    snapshot: WEditorSnapshot,
    layoutState: WLayoutProvider,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = snapshot.selection
    var isExpanded by remember { mutableStateOf(value = false) }
    var rawOffset by remember { mutableStateOf(Offset.Zero) }
    var hasInitializedPosition by remember { mutableStateOf(false) }
    var parentSize by remember { mutableStateOf(Size.Zero) }
    var toolbarSize by remember { mutableStateOf(Size.Zero) }

    val density = LocalDensity.current

    // Entrance animation: a soft pop-in when the selection toolbar appears.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")

    // Inline styles present at the start of the selection, to highlight their buttons.
    val activeSpanTypes = remember(state, snapshot.document.blocks) {
        val point = state.ranges.firstOrNull()
            ?.let { WSelectionManager.normalize(it, snapshot.document.blocks).anchor }
            ?: snapshot.cursor.position
        val block = snapshot.document.blocks.find { it.id == point.blockId } as? WTextContainer
        block?.spans
            ?.filter { it.start <= point.offset && point.offset < it.end }
            ?.map { it.type }
            ?.toSet()
            ?: emptySet()
    }

    // Initial position logic
    LaunchedEffect(state.isActive, snapshot.document.revision) {
        if (state.isActive && !hasInitializedPosition) {
            val currentBlocks = snapshot.document.blocks
            val title = snapshot.document.metadata.title
            
            // Hardened validation check before any measurement
            val allValid = state.ranges.all { range ->
                WSelectionManager.isValidPoint(range.anchor, currentBlocks, title) &&
                WSelectionManager.isValidPoint(range.focus, currentBlocks, title)
            }

            if (allValid) {
                val bounds = try { 
                    layoutState.getSelectionBounds() 
                } catch (e: Exception) { 
                    emptyList() 
                }
                
                if (bounds.isNotEmpty()) {
                    val topBound = bounds.minByOrNull { it.top }?.top ?: 0f
                    val centerX = bounds.map { it.center.x }.average().toFloat()
                    
                    if (!centerX.isNaN()) {
                        with(density) {
                            rawOffset = Offset(
                                (centerX - 100.dp.toPx()).coerceAtLeast(16.dp.toPx()), 
                                (topBound - 64.dp.toPx()).coerceAtLeast(16.dp.toPx())
                            )
                        }
                        hasInitializedPosition = true
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { parentSize = it.size.toSize() }
    ) {
        Column(
            modifier = Modifier
                .offset {
                    if (!hasInitializedPosition && parentSize.width > 0) {
                        rawOffset = Offset(
                            (parentSize.width - toolbarSize.width) / 2,
                            parentSize.height - toolbarSize.height - 100.dp.toPx()
                        )
                        hasInitializedPosition = true
                    }
                    IntOffset(
                        rawOffset.x.roundToInt().coerceIn(0, (parentSize.width - toolbarSize.width).toInt().coerceAtLeast(0)),
                        rawOffset.y.roundToInt().coerceIn(0, (parentSize.height - toolbarSize.height).toInt().coerceAtLeast(0))
                    )
                }
                .onGloballyPositioned { toolbarSize = it.size.toSize() }
                .pointerInput(Unit) {
                    // Drag moves the toolbar; a mostly-vertical swipe toggles the extra options instead.
                    var startOffset = Offset.Zero
                    var total = Offset.Zero
                    val swipeThreshold = 28.dp.toPx()
                    detectDragGestures(
                        onDragStart = {
                            startOffset = rawOffset
                            total = Offset.Zero
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(total.y) > swipeThreshold &&
                                kotlin.math.abs(total.y) > 1.5f * kotlin.math.abs(total.x)
                            ) {
                                rawOffset = startOffset
                                isExpanded = !isExpanded
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                        rawOffset += dragAmount
                    }
                }
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * p
                    scaleY = 0.9f + 0.1f * p
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            horizontalAlignment = Alignment.End
        ) {
            // Main Toolbar
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SelectionToolbarBg,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, SelectionToolbarBorder)
            ) {
                Row(
                    modifier = Modifier
                        .height(46.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontSizeScrollWheel(
                        onAdjust = { delta -> onCommand(WEditorCommand.AdjustFontSize(delta)) }
                    )
                    ToolbarSeparator()
                    ToolbarAction(Icons.Default.ContentCut, "Cut") { onCommand(WEditorCommand.Cut) }
                    ToolbarAction(Icons.Default.ContentCopy, "Copy") { onCommand(WEditorCommand.Copy) }
                    ToolbarAction(Icons.Default.ContentPaste, "Paste") { onCommand(WEditorCommand.Paste) }
                    ToolbarAction(Icons.Default.SelectAll, "Select All") { onCommand(WEditorCommand.SelectAll) }
                    ToolbarSeparator()
                    ToolbarAction(
                        icon = Icons.Default.FormatBold,
                        contentDescription = "Bold",
                        active = SpanType.Bold in activeSpanTypes
                    ) { onCommand(WEditorCommand.ApplySpan(SpanType.Bold)) }
                    ToolbarAction(
                        icon = Icons.Default.FormatItalic,
                        contentDescription = "Italic",
                        active = SpanType.Italic in activeSpanTypes
                    ) { onCommand(WEditorCommand.ApplySpan(SpanType.Italic)) }
                    ToolbarSeparator()
                    ToolbarAction(
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Less" else "More",
                        active = isExpanded,
                        iconRotation = chevronRotation
                    ) { isExpanded = !isExpanded }
                }
            }

            // Dropdown Menu (L-Shape)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
                exit = fadeOut() + scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f))
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(232.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = SelectionToolbarBg,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, SelectionToolbarBorder)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        val isAdding = state.interactionMode == WSelectionInteraction.ADD_RANGE
                        val canAnnotate = WSelectionManager.isSingleBlock(state)
                        val hasSelection = !WSelectionManager.isCollapsed(state)

                        DropdownItem(
                            icon = if (isAdding) Icons.Default.Check else Icons.Default.Add,
                            label = if (isAdding) "Done" else "Add Selection"
                        ) {
                            val nextMode = if (isAdding)
                                WSelectionInteraction.NORMAL
                            else
                                WSelectionInteraction.ADD_RANGE
                            onCommand(WEditorCommand.SetSelectionInteraction(nextMode))
                            isExpanded = false
                        }
                        DropdownItem(
                            icon = Icons.AutoMirrored.Filled.Comment,
                            label = "Add Context",
                            enabled = canAnnotate
                        ) {
                            val range = state.ranges.firstOrNull()
                            if (range != null) {
                                val normalized = WSelectionManager.normalize(range, snapshot.document.blocks)
                                val anchorText = WSelectionManager.getSelectedText(
                                    state.copy(ranges = listOf(normalized)),
                                    snapshot.document.blocks,
                                    snapshot.document.metadata.title
                                )
                                onCommand(WEditorCommand.StartCreatingAnnotation(normalized, anchorText))
                            }
                            isExpanded = false
                        }
                        DropdownItem(
                            icon = Icons.Default.Flag,
                            label = "Set Goal"
                        ) {
                            val point = state.ranges.firstOrNull()?.focus ?: snapshot.cursor.position
                            onCommand(WEditorCommand.SetGoal(point))
                            onCommand(WEditorCommand.ClearSelection)
                            isExpanded = false
                        }

                        DropdownDivider()

                        DropdownItem(
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            label = "New Tab with Selection",
                            enabled = hasSelection
                        ) {
                            val selectedBlocks = WSelectionManager.getSelectedBlocks(
                                state,
                                snapshot.document.blocks,
                                snapshot.document.metadata.title
                            )
                            if (selectedBlocks.isNotEmpty()) {
                                onCommand(WEditorCommand.CreateTabWithSelection(selectedBlocks))
                            }
                            isExpanded = false
                        }
                        DropdownItem(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            label = "Add to Tab",
                            enabled = hasSelection
                        ) {
                            val selectedBlocks = WSelectionManager.getSelectedBlocks(
                                state,
                                snapshot.document.blocks,
                                snapshot.document.metadata.title
                            )
                            if (selectedBlocks.isNotEmpty()) {
                                onCommand(WEditorCommand.AddToTab(selectedBlocks))
                            }
                            isExpanded = false
                        }

                        DropdownDivider()

                        DropdownItem(
                            icon = Icons.Default.Close,
                            label = "Cancel Selection"
                        ) {
                            onCommand(WEditorCommand.ClearSelection)
                            isExpanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    iconRotation: Float = 0f,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        if (active) SelectionToolbarActive else Color.Transparent,
        label = "toolbarActionBg"
    )
    val tint by animateColorAsState(
        if (active) Color.White else SelectionToolbarContent,
        label = "toolbarActionTint"
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(19.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

@Composable
private fun FontSizeScrollWheel(
    onAdjust: (Int) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val radiusPx = with(density) { 17.dp.toPx() }
    val stepPx = with(density) { 8.dp.toPx() }          // one ridge = one font-size step
    val ridgeRad = stepPx / radiusPx                     // angular spacing between ridges

    var pressed by remember { mutableStateOf(false) }
    var rollOffset by remember { mutableFloatStateOf(0f) } // how far the drum has rolled, in px
    var stepAccumulator by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val kick = remember { Animatable(0f) }               // little "click" pulse on every detent

    // Bulge out of the toolbar while touched
    val bulge by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "wheelBulge"
    )

    fun settleToDetent() {
        settleJob?.cancel()
        settleJob = scope.launch {
            val target = Math.round(rollOffset / stepPx) * stepPx
            animate(
                initialValue = rollOffset,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
            ) { value, _ -> rollOffset = value }
        }
    }

    fun roll(delta: Float) {
        settleJob?.cancel()
        rollOffset += delta
        stepAccumulator += delta
        if (kotlin.math.abs(stepAccumulator) >= stepPx) {
            val steps = (stepAccumulator / stepPx).toInt()
            // scroll down (positive drag) increases size
            onAdjust(steps)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            stepAccumulator -= steps * stepPx
            scope.launch {
                kick.snapTo(1f)
                kick.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 30.dp, height = 34.dp)
            .graphicsLayer {
                val b = bulge
                val k = kick.value
                scaleX = 1f + 0.28f * b - 0.04f * k
                scaleY = 1f + 0.18f * b + 0.03f * k
                shadowElevation = 14.dp.toPx() * b
                shape = RoundedCornerShape(10.dp)
                clip = true
            }
            .background(
                Color.White.copy(alpha = 0.05f + 0.05f * bulge),
                RoundedCornerShape(10.dp)
            )
            .semantics { contentDescription = "Font size wheel" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    settleJob?.cancel()
                    verticalDrag(down.id) { change ->
                        roll(change.positionChange().y)
                        change.consume()
                    }
                    pressed = false
                    stepAccumulator = 0f
                    settleToDetent()
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            if (delta != 0f) {
                                roll(delta.sign * stepPx)
                                settleToDetent()
                            }
                        }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.height / 2f - 2.dp.toPx()
                val phase = (rollOffset / radiusPx) % ridgeRad
                val count = (Math.PI / 2 / ridgeRad).toInt() + 1

                // Ridges wrapped around a cylinder: thicker and brighter facing front, thin at the edges
                for (i in -count..count) {
                    val theta = i * ridgeRad + phase
                    val c = kotlin.math.cos(theta)
                    if (c <= 0.05f) continue
                    val y = cy + r * kotlin.math.sin(theta).toFloat()
                    val w = size.width * (0.42f + 0.22f * bulge) * (0.55f + 0.45f * c.toFloat())
                    drawLine(
                        color = SelectionToolbarContent.copy(alpha = (0.25f + 0.55f * c.toFloat()).coerceIn(0f, 1f)),
                        start = Offset(cx - w / 2f, y),
                        end = Offset(cx + w / 2f, y),
                        strokeWidth = (0.8f + 1.0f * c.toFloat()).dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Barrel shading: dark rolling away at the top/bottom, soft light band in the middle
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.28f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.06f + 0.08f * bulge),
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f)
                    )
                )
            }
    )
}

@Composable
private fun ToolbarSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .height(20.dp)
            .width(1.dp)
            .background(SelectionToolbarDivider)
    )
}

@Composable
private fun DropdownDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(SelectionToolbarDivider)
    )
}

@Composable
private fun DropdownItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SelectionToolbarMuted.copy(alpha = alpha),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = SelectionToolbarContent.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnnotationInputPopup(
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pre-fill if editing
    val editingAnnotation = snapshot.activeAnnotationId?.let { id -> 
        snapshot.annotations.find { it.id == id } 
    }
    var text by remember { mutableStateOf(editingAnnotation?.content ?: "") }
    val localFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        localFocusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .widthIn(max = 300.dp)
            .padding(16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Add Context",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .focusRequester(localFocusRequester),
                placeholder = { Text("Enter context...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onCommand(WEditorCommand.CancelCreatingAnnotation) }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (editingAnnotation != null) {
                            onCommand(WEditorCommand.UpdateAnnotation(editingAnnotation.id, text))
                        } else {
                            val range = snapshot.pendingAnnotationRange
                            if (range != null) {
                                onCommand(WEditorCommand.CreateAnnotation(text, range, snapshot.pendingAnnotationAnchorText))
                            }
                        }
                        onCommand(WEditorCommand.CancelCreatingAnnotation)
                        onCommand(WEditorCommand.DismissAnnotation)
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
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

    val currentPlaceholderAlpha by animController.placeholderAlpha.asState()

    Box(modifier = modifier.onGloballyPositioned { editorSize = it.size }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            snapshot.document.blocks.forEach { block ->
                key(block.id) {
                    WBlockRenderer(
                        block = block,
                        snapshot = snapshot,
                        onTextLayout = { layoutState.layoutResults[block.id] = it },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            editorCoordinates?.let { root ->
                                // Calculate position relative to the editor root Box
                                val pos = root.localPositionOf(coords, Offset.Zero)
                                layoutState.blockOffsets[block.id] = pos
                                layoutState.blockHeights[block.id] = coords.size.height.toFloat()
                            }
                        },
                        placeholderAlpha = currentPlaceholderAlpha
                    )
                }
            }
        }

        // Overlay for Cursors and Selection Highlights
        EditorOverlayLayer(
            snapshot = snapshot,
            layoutState = layoutState,
            cursorBlinkVisible = cursorBlinkVisible
        )
    }
}

@Composable
private fun EditorOverlayLayer(
    snapshot: WEditorSnapshot,
    layoutState: WLayoutState,
    cursorBlinkVisible: Boolean
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val cursorColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val layoutResults = layoutState.layoutResults
        val blockOffsets = layoutState.blockOffsets

        // 1. Draw Selection Highlights
        if (snapshot.selection.isActive) {
            snapshot.selection.ranges.forEach { range ->
                val normalized = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, snapshot.document.blocks)
                
                snapshot.document.blocks.forEach { block ->
                    val layout = layoutResults[block.id] ?: return@forEach
                    val offset = blockOffsets[block.id] ?: Offset.Zero
                    
                    val startIdx = snapshot.document.blocks.indexOfFirst { it.id == normalized.anchor.blockId }
                    val endIdx = snapshot.document.blocks.indexOfFirst { it.id == normalized.focus.blockId }
                    val currentIdx = snapshot.document.blocks.indexOfFirst { it.id == block.id }

                    if (currentIdx in startIdx..endIdx) {
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

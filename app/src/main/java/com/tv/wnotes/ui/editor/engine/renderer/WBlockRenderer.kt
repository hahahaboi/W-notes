package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WDragPhase
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.renderer.WLayoutState

/**
 * Main dispatcher for rendering different block types.
 */
@Composable
fun WBlockRenderer(
    block: WBlock,
    snapshot: WEditorSnapshot,
    layoutState: WLayoutState,
    documentCoordinates: LayoutCoordinates?,
    onTextLayout: (TextLayoutResult) -> Unit,
    onCommand: (WEditorCommand) -> Unit,
    placeholderAlpha: Float = 1f,
    forceFullOpacity: Boolean = false
) {
    if (!block.isVisible) return

    val isBeingDragged = !forceFullOpacity && 
                        snapshot.drag.phase != WDragPhase.IDLE &&
                        com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.intersectsBlock(
                            state = com.tv.wnotes.ui.editor.engine.state.WSelectionState(ranges = snapshot.drag.selectedRanges, isActive = true),
                            blockId = block.id,
                            blocks = snapshot.document.blocks
                        )

    val targetAlpha = if (isBeingDragged) placeholderAlpha else 1f

    val baseModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = targetAlpha }
        .padding(
            start = (block.style.indentationLevel * 16).dp,
            bottom = block.style.paddingBottom.dp
        )

    val onPositioned: (LayoutCoordinates) -> Unit = { coords ->
        documentCoordinates?.let { docRoot ->
            val pos = docRoot.localPositionOf(coords, Offset.Zero)
            layoutState.blockOffsets[block.id] = pos
            layoutState.blockHeights[block.id] = coords.size.height.toFloat()
        }
    }

    when (block) {
        is WTextBlock -> TextBlockRenderer(block, snapshot, onTextLayout, onPositioned, baseModifier)
        is WChecklistBlock -> ChecklistBlockRenderer(block, snapshot, onTextLayout, onPositioned, baseModifier)
        is WMediaBlock -> MediaBlockRenderer(block, snapshot, onCommand, onPositioned, baseModifier)
    }
}

@Composable
private fun TextBlockRenderer(
    block: WTextBlock,
    snapshot: WEditorSnapshot,
    onTextLayout: (TextLayoutResult) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val annotatedString = remember(block.text, block.spans, block.revision) {
        buildAnnotatedStringFromModel(block.text, block.spans, colorScheme)
    }
    val textStyle = getTextStyle(block.type)
    
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    val annotationGeometries by remember(snapshot.annotations, layoutResult) {
        derivedStateOf {
            layoutResult?.let { layout ->
                snapshot.annotations
                    .filter { it.blockId == block.id }
                    .mapNotNull { annotation ->
                        WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, Offset.Zero)
                    }
            } ?: emptyList()
        }
    }

    Text(
        text = annotatedString,
        style = textStyle,
        onTextLayout = { 
            layoutResult = it
            onTextLayout(it) 
        },
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .drawAnnotationGeometry(
                geometries = annotationGeometries
            )
    )
}

@Composable
private fun ChecklistBlockRenderer(
    block: WChecklistBlock,
    snapshot: WEditorSnapshot,
    onTextLayout: (TextLayoutResult) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = block.isChecked,
            onCheckedChange = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val annotatedString = remember(block.text, block.spans, block.revision) {
            buildAnnotatedStringFromModel(block.text, block.spans, colorScheme)
        }
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        
        val annotationGeometries by remember(snapshot.annotations, layoutResult) {
            derivedStateOf {
                layoutResult?.let { layout ->
                    snapshot.annotations
                        .filter { it.blockId == block.id }
                        .mapNotNull { annotation ->
                            WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, Offset.Zero)
                        }
                } ?: emptyList()
            }
        }

        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge,
            onTextLayout = {
                layoutResult = it
                onTextLayout(it)
            },
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned(onPositioned)
                .drawAnnotationGeometry(
                    geometries = annotationGeometries
                )
        )
    }
}

@Composable
private fun MediaBlockRenderer(
    block: WMediaBlock,
    snapshot: WEditorSnapshot,
    onCommand: (WEditorCommand) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSelected by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .pointerInput(block.id) {
                detectTapGestures(onTap = { isSelected = !isSelected })
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(block.widthPercent)
                .aspectRatio(16f / 9f) // Default aspect ratio for images
                .clip(MaterialTheme.shapes.medium)
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) else Modifier)
        ) {
            AsyncImage(
                model = block.url,
                contentDescription = block.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isSelected) {
                // Resize Handle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(block.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaPercent = dragAmount.x / with(density) { 300.dp.toPx() } // Approximate scale factor
                                val newWidth = (block.widthPercent + deltaPercent).coerceIn(0.2f, 1.0f)
                                if (newWidth != block.widthPercent) {
                                    onCommand(WEditorCommand.UpdateBlock(block.copy(widthPercent = newWidth)))
                                }
                            }
                        }
                )
            }
        }
        
        block.caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

private fun buildAnnotatedStringFromModel(
    text: String,
    spans: List<WTextSpan>,
    colorScheme: ColorScheme
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val style = when (span.type) {
                SpanType.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                SpanType.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                SpanType.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
                SpanType.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                SpanType.Highlight -> SpanStyle(
                    background = colorScheme.primaryContainer,
                    color = colorScheme.onPrimaryContainer
                )
                SpanType.Code -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceVariant,
                    color = colorScheme.onSurfaceVariant
                )
                SpanType.Link -> SpanStyle(
                    color = colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
                else -> SpanStyle()
            }
            addStyle(style, span.start, span.end)
        }
    }
}

@Composable
private fun getTextStyle(type: TextBlockType): androidx.compose.ui.text.TextStyle {
    return when (type) {
        TextBlockType.H1 -> MaterialTheme.typography.headlineLarge
        TextBlockType.H2 -> MaterialTheme.typography.headlineMedium
        TextBlockType.H3 -> MaterialTheme.typography.headlineSmall
        TextBlockType.Quote -> MaterialTheme.typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> MaterialTheme.typography.bodyLarge
    }
}

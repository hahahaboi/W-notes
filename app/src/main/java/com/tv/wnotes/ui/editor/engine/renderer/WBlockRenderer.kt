package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WDragPhase
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.renderer.WLayoutState
import kotlin.math.roundToInt

/**
 * Note-wide values the block renderers need: the default font (used when nothing is selected)
 * and whether the pen is active.
 */
@Immutable
data class WBlockRenderContext(
    val baseFontSize: Int = 16,
    val baseFontFamily: String = "Default",
    val isDrawingMode: Boolean = false
)

val LocalWBlockRenderContext = compositionLocalOf { WBlockRenderContext() }

/**
 * Main dispatcher for rendering different block types.
 */
@Composable
fun WBlockRenderer(
    block: WBlock,
    annotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation>,
    layoutState: WLayoutState,
    rootCoordinates: WEditorRootCoordinates?,
    onTextLayout: (TextLayoutResult) -> Unit,
    onCommand: (WEditorCommand) -> Unit,
    allBlocks: List<WBlock> = emptyList(),
    isBeingDragged: Boolean = false,
    placeholderAlpha: Float = 1f
) {
    if (!block.isVisible) return

    val targetAlpha = if (isBeingDragged) placeholderAlpha else 1f

    val baseModifier = if (block.style.isFloating) {
        Modifier
            .offset { IntOffset(block.style.x.roundToInt(), block.style.y.roundToInt()) }
            .size(
                width = if (block.style.width > 0) block.style.width.dp else androidx.compose.ui.unit.Dp.Unspecified,
                height = if (block.style.height > 0) block.style.height.dp else androidx.compose.ui.unit.Dp.Unspecified
            )
    } else {
        Modifier
            .fillMaxWidth()
            .padding(
                start = (block.style.indentationLevel * 16).dp,
                bottom = block.style.paddingBottom.dp
            )
    }
    .graphicsLayer { alpha = targetAlpha }

    val onPositioned: (LayoutCoordinates) -> Unit = { coords ->
        rootCoordinates?.value?.let { docRoot ->
            val pos = docRoot.localPositionOf(coords, Offset.Zero)
            layoutState.blockOffsets[block.id] = pos
            layoutState.blockHeights[block.id] = coords.size.height.toFloat()
        }
    }

    when (block) {
        is WTextBlock -> TextBlockRenderer(block, annotations, allBlocks, onTextLayout, onPositioned, baseModifier)
        is WChecklistBlock -> ChecklistBlockRenderer(block, annotations, onTextLayout, onCommand, onPositioned, baseModifier)
        is WMediaBlock -> MediaBlockRenderer(block, onCommand, onPositioned, baseModifier)
        is WDrawingBlock -> DrawingBlockRenderer(block, onCommand, onPositioned, baseModifier)
        is WTextBoxBlock -> TextBoxBlockRenderer(block, annotations, onTextLayout, onPositioned, baseModifier)
    }
}

@Composable
private fun TextBlockRenderer(
    block: WTextBlock,
    annotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation>,
    allBlocks: List<WBlock>,
    onTextLayout: (TextLayoutResult) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val annotatedString = remember(block.text, block.spans, block.revision) {
        try {
            buildAnnotatedStringFromModel(block.text, block.spans, colorScheme)
        } catch (e: Exception) {
            AnnotatedString(block.text)
        }
    }
    val textStyle = getTextStyle(block.type, block.spans).copy(
        textAlign = block.style.textAlign()
    )
    
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    val annotationGeometries by remember(annotations, layoutResult) {
        derivedStateOf {
            layoutResult?.let { layout ->
                annotations.mapNotNull { annotation ->
                        WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, Offset.Zero)
                    }
            } ?: emptyList()
        }
    }

    Row(modifier = modifier) {
        if (block.type == TextBlockType.Bullet) {
            Text("• ", style = textStyle, modifier = Modifier.padding(end = 4.dp))
        } else if (block.type == TextBlockType.Numbered) {
            val index = remember(block, allBlocks) {
                var count = 1
                val idx = allBlocks.indexOfFirst { it.id == block.id }
                if (idx != -1) {
                    for (i in idx - 1 downTo 0) {
                        val prev = allBlocks[i]
                        if (prev is WTextBlock && prev.type == TextBlockType.Numbered) {
                            count++
                        } else break
                    }
                }
                count
            }
            Text("$index. ", style = textStyle, modifier = Modifier.padding(end = 4.dp))
        }

        Text(
            text = annotatedString,
            style = textStyle,
            onTextLayout = { 
                layoutResult = it
                onTextLayout(it) 
            },
            // Measured on the text itself so the cursor lines up after a bullet or number.
            modifier = Modifier.weight(1f)
                .onGloballyPositioned(onPositioned)
                .drawAnnotationGeometry(
                    geometries = annotationGeometries
                )
        )
    }
}

@Composable
private fun ChecklistBlockRenderer(
    block: WChecklistBlock,
    annotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation>,
    onTextLayout: (TextLayoutResult) -> Unit,
    onCommand: (WEditorCommand) -> Unit,
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
            onCheckedChange = { checked -> onCommand(WEditorCommand.UpdateBlock(block.copy(isChecked = checked))) },
            colors = CheckboxDefaults.colors(
                checkedColor = colorScheme.onSurface,
                uncheckedColor = colorScheme.outline,
                checkmarkColor = colorScheme.surface
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val annotatedString = remember(block.text, block.spans, block.revision) {
            try {
                buildAnnotatedStringFromModel(block.text, block.spans, colorScheme)
            } catch (e: Exception) {
                AnnotatedString(block.text)
            }
        }
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        
        val annotationGeometries by remember(annotations, layoutResult) {
            derivedStateOf {
                layoutResult?.let { layout ->
                    annotations.mapNotNull { annotation ->
                            WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, Offset.Zero)
                        }
                } ?: emptyList()
            }
        }

        val baseStyle = getTextStyle(TextBlockType.Body, block.spans).copy(textAlign = block.style.textAlign())
        Text(
            text = annotatedString,
            style = if (block.isChecked) {
                baseStyle.copy(
                    textDecoration = TextDecoration.LineThrough,
                    color = colorScheme.onSurfaceVariant
                )
            } else baseStyle,
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

@Composable
private fun DrawingBlockRenderer(
    block: WDrawingBlock,
    onCommand: (WEditorCommand) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDrawingMode = LocalWBlockRenderContext.current.isDrawingMode
    val inkColor = MaterialTheme.colorScheme.onSurface
    val density = androidx.compose.ui.platform.LocalDensity.current
    val strokeWidth = with(density) { 3.dp.toPx() }
    val currentBlock by rememberUpdatedState(block)
    // Points of the stroke being drawn right now; committed to the block when the finger lifts.
    val livePoints = remember { mutableStateListOf<Offset>() }

    Canvas(
        modifier = modifier
            .height(220.dp)
            .border(
                if (isDrawingMode) 1.dp else 0.5.dp,
                if (isDrawingMode) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .onGloballyPositioned(onPositioned)
            .pointerInput(isDrawingMode) {
                if (!isDrawingMode) return@pointerInput
                awaitEachGesture {
                    // Consuming the down keeps the editor from treating the stroke as a tap, scroll or selection.
                    val down = awaitFirstDown()
                    down.consume()
                    livePoints.clear()
                    livePoints.add(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        livePoints.add(change.position)
                    }
                    if (livePoints.size > 1) {
                        val stroke = WStroke(points = livePoints.toList(), color = 0, width = strokeWidth)
                        val latest = currentBlock
                        onCommand(WEditorCommand.UpdateBlock(latest.copy(strokes = latest.strokes + stroke)))
                    }
                    livePoints.clear()
                }
            }
    ) {
        // Ink always follows the theme so drawings stay readable in bright and dark mode.
        fun drawStroke(points: List<Offset>, width: Float) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(path, inkColor, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        block.strokes.forEach { drawStroke(it.points, it.width) }
        drawStroke(livePoints, strokeWidth)
    }
}

@Composable
private fun TextBoxBlockRenderer(
    block: WTextBoxBlock,
    annotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation>,
    onTextLayout: (TextLayoutResult) -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // An inline boxed paragraph: tapping inside places the cursor like any other text.
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .border(1.dp, colorScheme.outline, MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        val annotatedString = remember(block.text, block.spans, block.revision) {
            try {
                buildAnnotatedStringFromModel(block.text, block.spans, colorScheme)
            } catch (e: Exception) {
                AnnotatedString(block.text)
            }
        }
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        val annotationGeometries by remember(annotations, layoutResult) {
            derivedStateOf {
                layoutResult?.let { layout ->
                    annotations.mapNotNull { annotation ->
                            WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, Offset.Zero)
                        }
                } ?: emptyList()
            }
        }

        val textStyle = getTextStyle(TextBlockType.Body, block.spans).copy(textAlign = block.style.textAlign())
        if (block.text.isEmpty()) {
            Text(
                text = "Text box",
                style = textStyle,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Text(
            text = annotatedString,
            style = textStyle,
            onTextLayout = {
                layoutResult = it
                onTextLayout(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned(onPositioned)
                .drawAnnotationGeometry(geometries = annotationGeometries)
        )
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
                SpanType.FontSize -> {
                    val size = span.attributes["size"]?.toIntOrNull() ?: 16
                    SpanStyle(fontSize = size.sp)
                }
                SpanType.FontFamily -> SpanStyle(fontFamily = fontFamilyFor(span.attributes["family"]))
                else -> SpanStyle()
            }
            val safeStart = span.start.coerceIn(0, text.length)
            val safeEnd = span.end.coerceIn(safeStart, text.length)
            if (safeStart < safeEnd) {
                addStyle(style, safeStart, safeEnd)
            }
        }
    }
}

private fun WBlockStyle.textAlign(): TextAlign = when (alignment) {
    "center" -> TextAlign.Center
    "right" -> TextAlign.Right
    "justify" -> TextAlign.Justify
    else -> TextAlign.Left
}

private fun fontFamilyFor(name: String?): FontFamily = when (name) {
    "Serif" -> FontFamily.Serif
    "Sans-Serif" -> FontFamily.SansSerif
    "Monospace" -> FontFamily.Monospace
    "Cursive" -> FontFamily.Cursive
    else -> FontFamily.Default
}

@Composable
private fun getTextStyle(type: TextBlockType, spans: List<WTextSpan> = emptyList()): androidx.compose.ui.text.TextStyle {
    val context = LocalWBlockRenderContext.current
    val baseFamily = fontFamilyFor(context.baseFontFamily)
    val body = MaterialTheme.typography.bodyLarge.copy(
        fontSize = context.baseFontSize.sp,
        fontFamily = baseFamily
    )
    val style = when (type) {
        TextBlockType.H1 -> MaterialTheme.typography.headlineLarge.copy(fontFamily = baseFamily)
        TextBlockType.H2 -> MaterialTheme.typography.headlineMedium.copy(fontFamily = baseFamily)
        TextBlockType.H3 -> MaterialTheme.typography.headlineSmall.copy(fontFamily = baseFamily)
        TextBlockType.Quote -> body.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> body
    }
    // Line height is fixed per paragraph, so grow it to fit the largest font-size span.
    val largest = spans.filter { it.type == SpanType.FontSize }
        .mapNotNull { it.attributes["size"]?.toIntOrNull() }
        .maxOrNull()
    val needed = maxOf(style.fontSize.value, (largest ?: 0).toFloat()) * 1.25f
    return if (needed > style.lineHeight.value) style.copy(lineHeight = needed.sp) else style
}

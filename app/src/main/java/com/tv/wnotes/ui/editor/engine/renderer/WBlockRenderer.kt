package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tv.wnotes.ui.editor.engine.model.*
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WDragPhase

/**
 * Main dispatcher for rendering different block types.
 */
@Composable
fun WBlockRenderer(
    block: WBlock,
    snapshot: WEditorSnapshot,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
    placeholderAlpha: Float = 1f,
    forceFullOpacity: Boolean = false
) {
    if (!block.isVisible) return

    val isBeingDragged = !forceFullOpacity && 
                        snapshot.drag.phase != WDragPhase.IDLE &&
                        snapshot.drag.selectedRange?.let { it.anchor.blockId == block.id || it.focus.blockId == block.id } == true

    val targetAlpha = if (isBeingDragged) placeholderAlpha else 1f

    val blockModifier = modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = targetAlpha }
        .padding(
            start = (block.style.indentationLevel * 16).dp,
            bottom = block.style.paddingBottom.dp
        )

    when (block) {
        is WTextBlock -> TextBlockRenderer(block, snapshot, onTextLayout, blockModifier)
        is WChecklistBlock -> ChecklistBlockRenderer(block, snapshot, onTextLayout, blockModifier)
        is WMediaBlock -> MediaBlockRenderer(block, snapshot, blockModifier)
    }
}

@Composable
private fun TextBlockRenderer(
    block: WTextBlock,
    snapshot: WEditorSnapshot,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val annotatedString = remember(block.text, block.spans, block.revision) {
        buildAnnotatedStringFromModel(block.text, block.spans, colorScheme)
    }
    val textStyle = getTextStyle(block.type)

    Text(
        text = annotatedString,
        style = textStyle,
        onTextLayout = onTextLayout,
        modifier = modifier
    )
}

@Composable
private fun ChecklistBlockRenderer(
    block: WChecklistBlock,
    snapshot: WEditorSnapshot,
    onTextLayout: (TextLayoutResult) -> Unit,
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
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge,
            onTextLayout = onTextLayout,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MediaBlockRenderer(
    block: WMediaBlock,
    snapshot: WEditorSnapshot,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when(block.mediaType) {
                    MediaType.Image -> Icons.Default.Info
                    else -> Icons.Default.Notifications
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Media Placeholder: ${block.mediaType}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
            )
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

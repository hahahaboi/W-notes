package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.tv.wnotes.ui.editor.engine.model.SpanType
import com.tv.wnotes.ui.editor.engine.model.WTextSpan

/**
 * Maps internal WTextSpan models to Compose SpanStyles.
 */
object WSpanStyleProvider {

    @Composable
    fun provideStyle(span: WTextSpan): SpanStyle {
        return when (span.type) {
            SpanType.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanType.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            SpanType.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
            SpanType.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            SpanType.Highlight -> SpanStyle(
                background = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            SpanType.Code -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SpanType.Link -> SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
            SpanType.FontSize -> {
                val size = span.attributes["size"]?.toIntOrNull() ?: 16
                SpanStyle(fontSize = size.sp)
            }
            else -> SpanStyle()
        }
    }
}

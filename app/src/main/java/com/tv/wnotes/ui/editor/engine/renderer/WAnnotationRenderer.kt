package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.tv.wnotes.data.local.entity.ContextAnnotation
import com.tv.wnotes.ui.editor.engine.logic.AnnotationGeometry

/**
 * Visual configuration for Context Annotation outlines.
 */
object WAnnotationConfig {
    val StrokeWidth = 1.5.dp
    val PaddingHorizontal = 4.dp
    val PaddingVertical = 1.dp
    val Opacity = 0.6f
    val DefaultColor = Color.Gray
}

/**
 * Handles the calculation of geometry for Context Annotations.
 */
object WAnnotationGeometryCalculator {

    /**
     * Calculates the complete geometry for an annotation, including its visual path.
     */
    fun calculateGeometry(
        annotation: ContextAnnotation,
        layoutResult: TextLayoutResult,
        blockOffset: Offset
    ): AnnotationGeometry? {
        val start = annotation.startOffset
        val end = annotation.endOffset
        
        val textLength = layoutResult.layoutInput.text.length
        if (start >= end || start < 0 || start >= textLength) {
            return null
        }

        val safeEnd = end.coerceAtMost(textLength)
        val startLine = layoutResult.getLineForOffset(start)
        val endLine = if (safeEnd > 0) layoutResult.getLineForOffset(safeEnd - 1) else startLine

        val lineRects = mutableListOf<Rect>()
        for (lineIndex in startLine..endLine) {
            val lineStart = if (lineIndex == startLine) start else layoutResult.getLineStart(lineIndex)
            val lineEnd = if (lineIndex == endLine) safeEnd else layoutResult.getLineEnd(lineIndex)

            if (lineStart < lineEnd) {
                val horizontalStart = layoutResult.getHorizontalPosition(lineStart, usePrimaryDirection = true)
                val horizontalEnd = layoutResult.getHorizontalPosition(lineEnd, usePrimaryDirection = true)
                
                val top = layoutResult.getLineTop(lineIndex)
                val bottom = layoutResult.getLineBottom(lineIndex)

                val left = minOf(horizontalStart, horizontalEnd)
                val right = maxOf(horizontalStart, horizontalEnd)

                lineRects.add(
                    Rect(left, top, right, bottom).translate(blockOffset)
                )
            }
        }

        if (lineRects.isEmpty()) return null

        // Calculate aggregate bounding rect
        var boundingRect = lineRects.first()
        for (i in 1 until lineRects.size) {
            boundingRect = boundingRect.combine(lineRects[i])
        }

        // Build unified outline path using Union operation for multi-line continuity
        val outlinePath = Path()
        
        lineRects.forEach { rect ->
            // Apply asymmetrical padding to create "oblong" look
            // We can't use simple .inflate because horizontal and vertical differ
            val expandedRect = Rect(
                left = rect.left - 6f, // Approx 4dp in px depending on density, but let's use fixed for now or pass density
                top = rect.top - 2f,
                right = rect.right + 6f,
                bottom = rect.bottom + 2f
            )
            
            val segmentPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = expandedRect,
                        cornerRadius = CornerRadius(expandedRect.height / 2.2f) // Slightly less than half for more oblong ends
                    )
                )
            }
            if (outlinePath.isEmpty) {
                outlinePath.addPath(segmentPath)
            } else {
                outlinePath.op(outlinePath, segmentPath, PathOperation.Union)
            }
        }

        val startCursorRect = layoutResult.getCursorRect(start).translate(blockOffset)
        val endCursorRect = layoutResult.getCursorRect(safeEnd).translate(blockOffset)

        return AnnotationGeometry(
            annotationId = annotation.id,
            blockId = annotation.blockId,
            boundingRect = boundingRect,
            lineRects = lineRects,
            startCursorRect = startCursorRect,
            endCursorRect = endCursorRect,
            outlinePath = outlinePath
        )
    }

    /**
     * Legacy method for simple rect retrieval.
     */
    fun calculateAnnotationRects(
        annotation: ContextAnnotation,
        layoutResult: TextLayoutResult,
        blockOffset: Offset
    ): List<Rect> {
        return calculateGeometry(annotation, layoutResult, blockOffset)?.lineRects ?: emptyList()
    }
}

private fun Rect.combine(other: Rect): Rect {
    return Rect(
        left = minOf(this.left, other.left),
        top = minOf(this.top, other.top),
        right = maxOf(this.right, other.right),
        bottom = maxOf(this.bottom, other.bottom)
    )
}

/**
 * Extension to render calculated annotation geometries.
 */
fun Modifier.drawAnnotationGeometry(
    geometries: List<AnnotationGeometry>,
    color: Color = WAnnotationConfig.DefaultColor.copy(alpha = WAnnotationConfig.Opacity)
): Modifier = this.drawBehind {
    val strokeWidth = WAnnotationConfig.StrokeWidth.toPx()

    geometries.forEach { geometry ->
        geometry.outlinePath?.let { path ->
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } ?: run {
            // Fallback if path is null
            annotationRectsDraw(this, geometry.lineRects, color, strokeWidth, 4f)
        }
    }
}

/**
 * Kept for temporary compatibility with Phase 5 code during transition.
 */
fun Modifier.drawAnnotationRects(
    rects: List<List<Rect>>,
    color: Color
): Modifier = this.drawBehind {
    val strokeWidth = WAnnotationConfig.StrokeWidth.toPx()
    val paddingPx = 4f

    rects.forEach { annotationSegments ->
        annotationRectsDraw(this, annotationSegments, color, strokeWidth, paddingPx)
    }
}

private fun annotationRectsDraw(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    segments: List<Rect>,
    color: Color,
    strokeWidth: Float,
    padding: Float
) {
    segments.forEach { rect ->
        val expandedRect = Rect(
            left = rect.left - padding,
            top = rect.top - padding,
            right = rect.right + padding,
            bottom = rect.bottom + padding
        )
        
        drawScope.drawRoundRect(
            color = color,
            topLeft = expandedRect.topLeft,
            size = expandedRect.size,
            cornerRadius = CornerRadius(expandedRect.height / 2),
            style = Stroke(width = strokeWidth)
        )
    }
}

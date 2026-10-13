package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * Represents the calculated on-screen geometry of a Context Annotation.
 * Coordinates are typically in Editor-Content space (relative to the document root).
 */
data class AnnotationGeometry(
    val annotationId: String,
    val blockId: String,
    val boundingRect: Rect,
    val lineRects: List<Rect>,
    val startCursorRect: Rect,
    val endCursorRect: Rect,
    val outlinePath: Path? = null
)

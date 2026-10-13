package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange

/**
 * Interface for components that can translate screen coordinates to document positions.
 * Usually implemented by the Rendering Pipeline.
 */
interface WLayoutProvider {
    /**
     * Finds the selection point (block and character offset) for a given screen offset.
     */
    fun getSelectionPointForOffset(offset: Offset): WSelectionPoint?

    /**
     * Checks if a screen offset is within the current visual selection bounds.
     */
    fun isOffsetInsideSelection(offset: Offset): Boolean

    /**
     * Returns the physical rect of the insertion cursor for a given point.
     */
    fun getInsertionBeamForPoint(point: WSelectionPoint): Rect?

    /**
     * Returns the bounding boxes of the current selection.
     */
    fun getSelectionBounds(): List<Rect>

    /**
     * Returns the bounding boxes for a specific range.
     */
    fun getSelectionBoundsForRange(range: WSelectionRange): List<Rect>

    /**
     * Performs a hit-test to find the document position at a given screen offset.
     */
    fun hitTest(offset: Offset): WHitResult?

    /**
     * Returns the vertical pixel offset of the document's end.
     */
    fun getDocumentBottom(): Float

    /**
     * Returns the bounding boxes of a specific annotation.
     */
    fun getAnnotationBounds(annotationId: String): List<Rect>

    /**
     * Returns the full geometry of a specific annotation.
     */
    fun getAnnotationGeometry(annotationId: String): AnnotationGeometry?

    /**
     * Current display density.
     */
    val density: Float

    /**
     * Current zoom scale.
     */
    val zoom: Float

    /**
     * Current selection state.
     */
    val currentSelection: com.tv.wnotes.ui.editor.engine.state.WSelectionState?

    /**
     * Current list of blocks.
     */
    val currentBlocks: List<com.tv.wnotes.ui.editor.engine.model.WBlock>

    /**
     * Current list of annotations.
     */
    val currentAnnotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation>

    /**
     * Efficiently retrieves annotations for a specific block.
     */
    fun getAnnotationsForBlock(blockId: String): List<com.tv.wnotes.data.local.entity.ContextAnnotation>
}

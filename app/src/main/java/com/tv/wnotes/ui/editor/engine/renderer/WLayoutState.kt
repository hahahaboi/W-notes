package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import com.tv.wnotes.ui.editor.engine.logic.WBlockLayoutInfo
import com.tv.wnotes.ui.editor.engine.logic.WHitResult
import com.tv.wnotes.ui.editor.engine.logic.WHitTestEngine
import com.tv.wnotes.ui.editor.engine.logic.WLayoutProvider
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.state.WSelectionPoint
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange
import com.tv.wnotes.ui.editor.engine.state.WSelectionState
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Stable wrapper for coordinates to prevent unnecessary recompositions.
 */
@Stable
class WEditorRootCoordinates(initial: LayoutCoordinates? = null) {
    var value: LayoutCoordinates? by mutableStateOf(initial)
}

/**
 * Implementation of WLayoutProvider that holds active layout information
 * from the rendering pipeline.
 */
class WLayoutState : WLayoutProvider {
    val layoutResults = mutableStateMapOf<String, TextLayoutResult>()
    val blockOffsets = mutableStateMapOf<String, Offset>()
    val blockHeights = mutableStateMapOf<String, Float>()
    
    override var density: Float = 1f
    override var zoom: Float = 1.0f
    override var currentSelection by mutableStateOf<WSelectionState?>(null)
    override var currentBlocks by mutableStateOf<List<WBlock>>(emptyList())
    override var currentAnnotations by mutableStateOf<List<com.tv.wnotes.data.local.entity.ContextAnnotation>>(emptyList())
    
    var titleLayoutResult by mutableStateOf<TextLayoutResult?>(null)
    var titleOffset by mutableStateOf(Offset.Zero)

    // Internal index to avoid O(N) filtering in hit-tests
    private var annotationIndex = emptyMap<String, List<com.tv.wnotes.data.local.entity.ContextAnnotation>>()

    fun updateAnnotations(annotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation>) {
        currentAnnotations = annotations
        annotationIndex = annotations.groupBy { it.blockId }
    }

    override fun getAnnotationsForBlock(blockId: String): List<com.tv.wnotes.data.local.entity.ContextAnnotation> {
        return annotationIndex[blockId] ?: emptyList()
    }

    override fun getSelectionPointForOffset(offset: Offset): WSelectionPoint? {
        return hitTest(offset)?.let {
            WSelectionPoint(it.blockId, it.characterIndex)
        }
    }

    override fun isOffsetInsideSelection(offset: Offset): Boolean {
        val selection = currentSelection ?: return false
        if (!selection.isActive) return false
        
        val hit = hitTest(offset) ?: return false
        
        return com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.contains(
            state = selection,
            blockId = hit.blockId,
            offset = hit.characterIndex,
            blocks = currentBlocks
        )
    }

    override fun getInsertionBeamForPoint(point: WSelectionPoint): Rect? {
        if (point.blockId == "W_NOTE_TITLE") {
            val layout = titleLayoutResult ?: return null
            val validOffset = layout.validOffsetOrNull(point.offset) ?: return null
            val rect = layout.getCursorRect(validOffset)
            return rect.translate(titleOffset)
        }
        val layout = layoutResults[point.blockId] ?: return null
        val offset = blockOffsets[point.blockId] ?: Offset.Zero
        val validOffset = layout.validOffsetOrNull(point.offset) ?: return null
        val rect = layout.getCursorRect(validOffset)
        return rect.translate(offset)
    }

    override fun getSelectionBounds(): List<Rect> {
        val selection = currentSelection ?: return emptyList()
        if (!selection.isActive) return emptyList()
        
        val allRects = mutableListOf<Rect>()
        selection.ranges.forEach { range ->
            allRects.addAll(getSelectionBoundsForRange(range))
        }
        return allRects
    }

    override fun getSelectionBoundsForRange(range: WSelectionRange): List<Rect> {
        val allRects = mutableListOf<Rect>()
        val normalized = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, currentBlocks)
        
        // Handle Title Selection
        if (normalized.anchor.blockId == "W_NOTE_TITLE") {
            titleLayoutResult?.let { layout ->
                val textLength = layout.layoutInput.text.length
                val start = normalized.anchor.offset.coerceIn(0, textLength)
                val end = if (normalized.focus.blockId == "W_NOTE_TITLE") {
                    normalized.focus.offset.coerceIn(0, textLength)
                } else {
                    textLength
                }.coerceIn(0, textLength)
                
                if (start < end) {
                    layout.selectionPathOrNull(start, end)?.let { path ->
                        allRects.add(path.getBounds().translate(titleOffset))
                    }
                }
            }
            if (normalized.focus.blockId == "W_NOTE_TITLE") return allRects
        }

        val startBlockIdx = currentBlocks.indexOfFirst { it.id == normalized.anchor.blockId }
        val endBlockIdx = currentBlocks.indexOfFirst { it.id == normalized.focus.blockId }
        
        // Stale ranges are ignored to prevent invalid coordinate mapping
        if (startBlockIdx < 0 || endBlockIdx < 0) return allRects

        currentBlocks.forEachIndexed { currentIdx, block ->
            if (currentIdx in startBlockIdx..endBlockIdx) {
                val layout = layoutResults[block.id] ?: return@forEachIndexed
                val offset = blockOffsets[block.id] ?: Offset.Zero
                
                val textLength = layout.layoutInput.text.length
                val selectionStart = if (currentIdx == startBlockIdx) normalized.anchor.offset.coerceIn(0, textLength) else 0
                val selectionEnd = if (currentIdx == endBlockIdx) normalized.focus.offset.coerceIn(0, textLength) else textLength

                if (selectionStart < selectionEnd) {
                    layout.selectionPathOrNull(selectionStart, selectionEnd)?.let { path ->
                        allRects.add(path.getBounds().translate(offset))
                    }
                }
            }
        }
        return allRects
    }

    override fun getAnnotationBounds(annotationId: String): List<Rect> {
        return getAnnotationGeometry(annotationId)?.lineRects ?: emptyList()
    }

    override fun getAnnotationGeometry(annotationId: String): com.tv.wnotes.ui.editor.engine.logic.AnnotationGeometry? {
        val annotation = currentAnnotations.find { it.id == annotationId } ?: return null
        
        if (annotation.blockId == "W_NOTE_TITLE") {
            val layout = titleLayoutResult ?: return null
            return WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, titleOffset)
        }

        val layout = layoutResults[annotation.blockId] ?: return null
        val offset = blockOffsets[annotation.blockId] ?: Offset.Zero
        
        return WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, offset)
    }

    override fun hitTest(offset: Offset): WHitResult? {
        val layouts = mutableListOf<WBlockLayoutInfo>()
        
        titleLayoutResult?.let { layout ->
            layouts.add(WBlockLayoutInfo("W_NOTE_TITLE", layout, titleOffset, layout.size.height.toFloat()))
        }

        blockOffsets.forEach { (id, blockOffset) ->
            val layout = layoutResults[id] ?: return@forEach
            val height = blockHeights[id] ?: 0f
            // blockOffsets are measured on the text itself, so indentation is already included.
            layouts.add(WBlockLayoutInfo(id, layout, blockOffset, height))
        }
        
        layouts.sortBy { it.relativeOffset.y }

        return WHitTestEngine.hitTest(offset, layouts)
    }

    override fun getDocumentBottom(): Float {
        var bottom = 0f
        blockOffsets.forEach { (id, offset) ->
            val height = blockHeights[id] ?: 0f
            bottom = maxOf(bottom, offset.y + height)
        }
        return bottom
    }

    /**
     * Calculates the next position for a cursor move (Up/Down).
     */
    fun getNextPosition(current: WSelectionPoint, direction: com.tv.wnotes.ui.editor.engine.logic.NavigationDirection): WSelectionPoint {
        val layout = layoutResults[current.blockId] ?: return current
        val offset = blockOffsets[current.blockId] ?: Offset.Zero
        val validOffset = layout.validOffsetOrNull(current.offset) ?: return current
        val cursorRect = layout.getCursorRect(validOffset).translate(offset)
        
        return when (direction) {
            com.tv.wnotes.ui.editor.engine.logic.NavigationDirection.UP -> {
                val targetY = cursorRect.top - 10f // Move up slightly
                hitTest(Offset(cursorRect.center.x, targetY))?.let { 
                    WSelectionPoint(it.blockId, it.characterIndex) 
                } ?: current
            }
            com.tv.wnotes.ui.editor.engine.logic.NavigationDirection.DOWN -> {
                val targetY = cursorRect.bottom + 10f // Move down slightly
                hitTest(Offset(cursorRect.center.x, targetY))?.let { 
                    WSelectionPoint(it.blockId, it.characterIndex) 
                } ?: current
            }
            else -> current
        }
    }
}

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
import com.tv.wnotes.ui.editor.engine.state.WSelectionState

/**
 * Implementation of WLayoutProvider that holds active layout information
 * from the rendering pipeline.
 */
class WLayoutState : WLayoutProvider {
    val layoutResults = mutableStateMapOf<String, TextLayoutResult>()
    val blockOffsets = mutableStateMapOf<String, Offset>()
    val blockHeights = mutableStateMapOf<String, Float>()
    
    override var density: Float = 1f
    override var currentSelection by mutableStateOf<WSelectionState?>(null)
    override var currentBlocks by mutableStateOf<List<WBlock>>(emptyList())
    override var currentAnnotations by mutableStateOf<List<com.tv.wnotes.data.local.entity.ContextAnnotation>>(emptyList())
    
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
        val layout = layoutResults[point.blockId] ?: return null
        val offset = blockOffsets[point.blockId] ?: Offset.Zero
        val rect = layout.getCursorRect(point.offset)
        return rect.translate(offset)
    }

    override fun getSelectionBounds(): List<Rect> {
        val selection = currentSelection ?: return emptyList()
        if (!selection.isActive) return emptyList()
        
        val allRects = mutableListOf<Rect>()
        selection.ranges.forEach { range ->
            val normalized = com.tv.wnotes.ui.editor.engine.logic.WSelectionManager.normalize(range, currentBlocks)
            
            currentBlocks.forEach { block ->
                val layout = layoutResults[block.id] ?: return@forEach
                val offset = blockOffsets[block.id] ?: Offset.Zero
                
                val startIdx = currentBlocks.indexOfFirst { it.id == normalized.anchor.blockId }
                val endIdx = currentBlocks.indexOfFirst { it.id == normalized.focus.blockId }
                val currentIdx = currentBlocks.indexOfFirst { it.id == block.id }

                if (currentIdx in startIdx..endIdx) {
                    val selectionStart = if (currentIdx == startIdx) normalized.anchor.offset else 0
                    val selectionEnd = if (currentIdx == endIdx) normalized.focus.offset else {
                        if (block is com.tv.wnotes.ui.editor.engine.model.WTextBlock) block.text.length 
                        else if (block is com.tv.wnotes.ui.editor.engine.model.WChecklistBlock) block.text.length
                        else 0
                    }

                    if (selectionStart < selectionEnd) {
                        val path = layout.getPathForRange(selectionStart, selectionEnd)
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
        val layout = layoutResults[annotation.blockId] ?: return null
        val offset = blockOffsets[annotation.blockId] ?: Offset.Zero
        
        return WAnnotationGeometryCalculator.calculateGeometry(annotation, layout, offset)
    }

    override fun hitTest(offset: Offset): WHitResult? {
        val layouts = blockOffsets.mapNotNull { (id, blockOffset) ->
            val layout = layoutResults[id] ?: return@mapNotNull null
            val height = blockHeights[id] ?: 0f
            val block = currentBlocks.find { it.id == id }
            val indentation = (block?.style?.indentationLevel ?: 0) * 16f * density
            WBlockLayoutInfo(id, layout, blockOffset, height, indentation)
        }.sortedBy { it.relativeOffset.y }

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
        val cursorRect = layout.getCursorRect(current.offset).translate(offset)
        
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

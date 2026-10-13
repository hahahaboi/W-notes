package com.tv.wnotes.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.home.HomeItem

/**
 * Coordinates drag-and-drop operations for note cards in the workspace.
 */
@Stable
class WorkspaceDragCoordinator {
    var draggedItem by mutableStateOf<HomeItem?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var initialTouchPosition by mutableStateOf(Offset.Zero)
    
    // Maps ItemId to its global screen bounds
    private val itemBounds = mutableStateMapOf<String, Rect>()
    
    var hoveredTargetId by mutableStateOf<String?>(null)
        private set

    var isActuallyDragging by mutableStateOf(false)
        private set

    private var lastDraggedItem: HomeItem? = null

    fun updateItemBounds(id: String, bounds: Rect) {
        itemBounds[id] = bounds
    }

    fun removeItemBounds(id: String) {
        itemBounds.remove(id)
    }

    fun startDrag(item: HomeItem, touchPosition: Offset) {
        draggedItem = item
        lastDraggedItem = item
        initialTouchPosition = touchPosition
        dragOffset = Offset.Zero
        isActuallyDragging = false
    }

    fun updateDrag(amount: Offset) {
        dragOffset += amount
        if (dragOffset.getDistance() > 10f) {
            isActuallyDragging = true
        }
        updateHoverTarget()
    }

    private fun updateHoverTarget() {
        val draggedId = draggedItem?.id ?: return
        val currentBounds = itemBounds[draggedId]?.translate(dragOffset) ?: return
        
        // Find an item that overlaps significantly with the dragged item's center
        val center = currentBounds.center
        
        hoveredTargetId = itemBounds.entries
            .filter { it.key != draggedId }
            .find { it.value.contains(center) }
            ?.key
    }

    data class DropResult(val draggedItem: HomeItem, val targetId: String?)

    fun endDrag(): DropResult? {
        val item = draggedItem
        val targetId = hoveredTargetId
        reset()
        return if (item != null) DropResult(item, targetId) else null
    }

    fun cancel() {
        reset()
    }

    private fun reset() {
        draggedItem = null
        dragOffset = Offset.Zero
        initialTouchPosition = Offset.Zero
        hoveredTargetId = null
        isActuallyDragging = false
    }

    fun getBounds(id: String): Rect? = itemBounds[id]
}

val LocalWorkspaceDragCoordinator = staticCompositionLocalOf { WorkspaceDragCoordinator() }

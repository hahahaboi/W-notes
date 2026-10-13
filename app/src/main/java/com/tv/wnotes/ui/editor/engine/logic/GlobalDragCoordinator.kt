package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.state.WEditorSnapshot
import com.tv.wnotes.ui.editor.engine.state.WSelectionRange

/**
 * Coordinates drag-and-drop operations across multiple editor instances.
 */
@Stable
class GlobalDragCoordinator {
    var isDragging by mutableStateOf(false)
    var sourceTabId by mutableStateOf<Long?>(null)
    var sourceSnapshot by mutableStateOf<WEditorSnapshot?>(null)
    var sourceRanges by mutableStateOf<List<WSelectionRange>>(emptyList())
    var currentGlobalPosition by mutableStateOf(Offset.Unspecified)
    
    // Maps TabId to its global screen bounds
    private val tabBounds = mutableStateMapOf<Long, Rect>()

    fun updateTabBounds(tabId: Long, bounds: Rect) {
        tabBounds[tabId] = bounds
    }

    fun removeTabBounds(tabId: Long) {
        tabBounds.remove(tabId)
    }

    fun getTargetTabId(globalPosition: Offset): Long? {
        return tabBounds.entries.find { it.value.contains(globalPosition) }?.key
    }

    fun startDrag(tabId: Long, snapshot: WEditorSnapshot, ranges: List<WSelectionRange>) {
        isDragging = true
        sourceTabId = tabId
        sourceSnapshot = snapshot
        sourceRanges = ranges
    }

    fun endDrag() {
        isDragging = false
        sourceTabId = null
        sourceSnapshot = null
        sourceRanges = emptyList()
        currentGlobalPosition = Offset.Unspecified
    }
}

val LocalGlobalDragCoordinator = staticCompositionLocalOf { GlobalDragCoordinator() }

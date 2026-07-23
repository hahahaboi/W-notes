package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Interprets raw pointer and keyboard events and emits high-level Editor Commands.
 * This class is pure logic and doesn't know about UI state or document content.
 */
class WGestureCoordinator(
    var layoutProvider: WLayoutProvider,
    private val onCommand: (WEditorCommand) -> Unit
) {
    private var lastTapTime = 0L
    private var lastTapPosition: Offset? = null
    private var tapCount = 0
    private val MULTI_TAP_TIMEOUT = 300L
    private val MULTI_TAP_SLOP = 40f // approx 15-20dp

    var lastHitResult: WHitResult? = null
        private set

    fun onCommand(command: WEditorCommand) {
        onCommand.invoke(command)
    }

    fun isOffsetInsideSelection(offset: Offset): Boolean {
        return layoutProvider.isOffsetInsideSelection(offset)
    }

    fun updateLayoutProvider(newProvider: WLayoutProvider) {
        layoutProvider = newProvider
    }

    /**
     * Handles a single pointer tap event.
     */
    fun onTap(offset: Offset, isShiftPressed: Boolean = false) {
        val now = System.currentTimeMillis()
        val prevPosition = lastTapPosition
        
        val isSpatiallyClose = if (prevPosition != null) {
            (offset - prevPosition).getDistance() < MULTI_TAP_SLOP
        } else false

        if (now - lastTapTime < MULTI_TAP_TIMEOUT && isSpatiallyClose) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTime = now
        lastTapPosition = offset

        val hit = layoutProvider.hitTest(offset)
        lastHitResult = hit

        if (hit == null) {
            onCommand(WEditorCommand.ClearSelection)
            return
        }

        val point = WSelectionPoint(hit.blockId, hit.characterIndex)

        when {
            isShiftPressed -> onCommand(WEditorCommand.ExtendSelection(point))
            tapCount == 2 -> onCommand(WEditorCommand.SelectWord(point))
            tapCount == 3 -> onCommand(WEditorCommand.SelectParagraph(point))
            else -> onCommand(WEditorCommand.MoveCursor(point, hit.affinity))
        }
    }

    /**
     * Handles a double tap event.
     */
    fun onDoubleTap(offset: Offset) {
        val hit = layoutProvider.hitTest(offset)
        lastHitResult = hit

        val point = layoutProvider.getSelectionPointForOffset(offset) ?: return
        onCommand(WEditorCommand.SelectWord(point))
    }

    fun onLongPress(offset: Offset) {
        println("STAGE 3: GestureCoordinator.onLongPress() at $offset")
        val hit = layoutProvider.hitTest(offset)
        lastHitResult = hit
        if (hit == null) {
            println("Execution stopped: No hit result at $offset")
            return
        }

        val point = WSelectionPoint(hit.blockId, hit.characterIndex)
        
        val isInsideSelection = layoutProvider.isOffsetInsideSelection(offset)
        
        // Log details for investigation
        val bounds = layoutProvider.getSelectionBounds()
        println("DEBUG DRAG: offset=$offset, hit=$hit")
        println("DEBUG DRAG: selectionBounds=$bounds")
        println("STAGE 4: Selection exists? $isInsideSelection")

        if (isInsideSelection) {
            println("BRANCH a: prepareLift() via BeginDragSelection")
            onCommand(WEditorCommand.BeginDragSelection(point, offset))
        } else {
            println("BRANCH b: selectWord()")
            onCommand(WEditorCommand.SelectWord(point))
        }
    }

    /**
     * Handles drag start. Checks for handles first.
     */
    fun onDragStart(offset: Offset, selectionState: WSelectionState, density: Float) {
        val hitHandle = WSelectionHandleManager.hitTestHandles(offset, selectionState, density)
        if (hitHandle != null) {
            onCommand(WEditorCommand.UpdateActiveHandle(hitHandle))
        } else {
            // Future milestones: start regular drag or selection extension
        }
    }

    /**
     * Handles dragging. Updates handle or selection.
     */
    fun onDrag(offset: Offset, activeHandle: WSelectionHandleType?) {
        if (activeHandle != null) {
            val point = layoutProvider.getSelectionPointForOffset(offset) ?: return
            onCommand(WEditorCommand.MoveSelectionHandle(activeHandle, point))
        }
    }

    /**
     * Handles keyboard events.
     */
    fun onKeyEvent(type: KeyEventType, point: WSelectionPoint? = null) {
        when (type) {
            KeyEventType.SelectAll -> onCommand(WEditorCommand.SelectAll)
            KeyEventType.ClearSelection -> onCommand(WEditorCommand.ClearSelection)
            KeyEventType.ExtendSelection -> {
                if (point != null) onCommand(WEditorCommand.ExtendSelection(point))
            }
            KeyEventType.MoveCursor -> {
                if (point != null) onCommand(WEditorCommand.MoveCursor(point))
            }
        }
    }

    enum class KeyEventType {
        SelectAll, ClearSelection, ExtendSelection, MoveCursor
    }
}

package com.tv.wnotes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.*
import com.tv.wnotes.ui.editor.engine.logic.WGestureCoordinator
import com.tv.wnotes.ui.editor.engine.state.WSelectionState
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.state.WDragPhase
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface GestureDecision {
    data object Tap : GestureDecision
    data object LongPress : GestureDecision
    data class Drag(val change: PointerInputChange) : GestureDecision
}

/**
 * Custom Modifier for handling all rich text editor gestures.
 * Optimized for low-latency selection, dragging, and focus management.
 */
fun Modifier.editorGestures(
    coordinator: WGestureCoordinator,
    selectionState: WSelectionState,
    dragPhase: WDragPhase,
    focusRequester: FocusRequester,
    density: Float,
    onGestureDetected: (String) -> Unit
): Modifier = this.composed {
    val currentCoordinator by rememberUpdatedState(coordinator)
    val currentSelectionState by rememberUpdatedState(selectionState)
    val currentDragPhase by rememberUpdatedState(dragPhase)
    val currentDensity by rememberUpdatedState(density)
    val currentOnGestureDetected by rememberUpdatedState(onGestureDetected)

    this.pointerInput(Unit) {
        val viewConf = this.viewConfiguration
        awaitEachGesture {
            val down: PointerInputChange = awaitFirstDown()
            currentOnGestureDetected("Down")
            
            currentCoordinator.onCommand(WEditorCommand.UpdateTelemetry { it.copy(currentStage = 1, pointerPosition = down.position) })
            
            var dragStarted = false
            val longPressTimeout = viewConf.longPressTimeoutMillis
            
            val decision = withTimeoutOrNull(longPressTimeout) {
                var dragChange: PointerInputChange? = null
                while (dragChange == null) {
                    val event = awaitPointerEvent()
                    val change = event.changes.first()
                    
                    if (change.changedToUp()) {
                        return@withTimeoutOrNull GestureDecision.Tap
                    }
                    
                    val dragAmount = change.position - down.position
                    if (dragAmount.getDistance() > viewConf.touchSlop) {
                        dragChange = change
                    }
                }
                GestureDecision.Drag(dragChange)
            } ?: GestureDecision.LongPress
            
            var isSelectionDragging = false
            
            when (decision) {
                is GestureDecision.Tap -> {
                    currentOnGestureDetected("Tap")
                    down.consume()
                    focusRequester.requestFocus()
                    currentCoordinator.onTap(down.position)
                    return@awaitEachGesture // Exit this gesture sequence
                }
                is GestureDecision.LongPress -> {
                    if (currentDragPhase == WDragPhase.IDLE) {
                        currentOnGestureDetected("Long Press")
                        isSelectionDragging = !currentCoordinator.isOffsetInsideSelection(down.position)
                        currentCoordinator.onLongPress(down.position)
                    }
                }
                is GestureDecision.Drag -> {
                    dragStarted = true
                    currentOnGestureDetected("Drag Start")
                    isSelectionDragging = currentCoordinator.onDragStart(down.position, currentSelectionState, currentDensity)
                }
            }
            
            // Main movement loop
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.first()
                
                if (change.changedToUp()) {
                    if (currentDragPhase != WDragPhase.IDLE || dragStarted) {
                        currentOnGestureDetected("Drop")
                        currentCoordinator.onCommand(WEditorCommand.CompleteDrag(null))
                    }
                    if (currentSelectionState.activeHandle != null) {
                        currentCoordinator.onCommand(WEditorCommand.UpdateActiveHandle(null))
                    }
                    break
                }
                
                if (currentDragPhase != WDragPhase.IDLE) {
                    currentOnGestureDetected("Dragging (Lift)")
                    currentCoordinator.onCommand(WEditorCommand.UpdateDragPosition(change.position))
                    change.consume()
                } else if (isSelectionDragging && currentSelectionState.activeHandle == null) {
                    currentOnGestureDetected("Selection Dragging")
                    currentCoordinator.onSelectionDrag(change.position)
                    change.consume()
                } else {
                    val dragAmount = change.position - down.position
                    if (!dragStarted && dragAmount.getDistance() > viewConfiguration.touchSlop) {
                        dragStarted = true
                        currentOnGestureDetected("Drag Start")
                        isSelectionDragging = currentCoordinator.onDragStart(down.position, currentSelectionState, currentDensity)
                    }
                    
                    if (dragStarted) {
                        currentCoordinator.onDrag(change.position, currentSelectionState.activeHandle)
                        if (currentSelectionState.activeHandle != null || isSelectionDragging) {
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}

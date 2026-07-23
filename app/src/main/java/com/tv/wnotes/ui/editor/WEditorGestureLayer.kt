package com.tv.wnotes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun WEditorGestureLayer(
    coordinator: WGestureCoordinator,
    selectionState: WSelectionState,
    dragPhase: WDragPhase,
    focusRequester: FocusRequester,
    onGestureDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    
    // Use rememberUpdatedState to ensure the gesture loop always has the latest state
    // without restarting the pointerInput block.
    val currentDragPhase by rememberUpdatedState(dragPhase)
    val currentSelectionState by rememberUpdatedState(selectionState)
    val currentCoordinator by rememberUpdatedState(coordinator)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Unified Gesture Detection
            .pointerInput(Unit) { // KEY IS Unit TO PREVENT RESTARTS
                val viewConf = this.viewConfiguration
                awaitEachGesture {
                    val down: PointerInputChange = awaitFirstDown()
                    onGestureDetected("Down")
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
                    
                    when (decision) {
                        is GestureDecision.Tap -> {
                            onGestureDetected("Tap")
                            down.consume()
                            focusRequester.requestFocus()
                            currentCoordinator.onTap(down.position)
                            return@awaitEachGesture // Exit this gesture sequence
                        }
                        is GestureDecision.LongPress -> {
                            if (currentDragPhase == WDragPhase.IDLE) {
                                onGestureDetected("Long Press")
                                currentCoordinator.onLongPress(down.position)
                            }
                        }
                        is GestureDecision.Drag -> {
                            // Normal drag started before long press timeout
                            dragStarted = true
                            onGestureDetected("Drag Start")
                            currentCoordinator.onDragStart(down.position, currentSelectionState, density)
                        }
                    }
                    
                    // Main movement loop (for Drag or Long Press follow-up)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        
                        if (change.changedToUp()) {
                            if (currentDragPhase != WDragPhase.IDLE || dragStarted) {
                                onGestureDetected("Drop")
                                currentCoordinator.onCommand(WEditorCommand.CompleteDrag(null))
                            }
                            break
                        }
                        
                        if (currentDragPhase != WDragPhase.IDLE) {
                            onGestureDetected("Dragging (Lift)")
                            currentCoordinator.onCommand(WEditorCommand.UpdateDragPosition(change.position))
                            change.consume()
                        } else {
                            val dragAmount = change.position - down.position
                            if (!dragStarted && dragAmount.getDistance() > viewConfiguration.touchSlop) {
                                dragStarted = true
                                onGestureDetected("Drag Start")
                                currentCoordinator.onDragStart(down.position, currentSelectionState, density)
                            }
                            
                            if (dragStarted) {
                                currentCoordinator.onDrag(change.position, currentSelectionState.activeHandle)
                                change.consume()
                            }
                        }
                    }
                }
            }
    )
}

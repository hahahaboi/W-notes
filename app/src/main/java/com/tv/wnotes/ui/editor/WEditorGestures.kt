package com.tv.wnotes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
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
    data object MultiTouch : GestureDecision
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
    currentZoom: Float,
    verticalScrollState: androidx.compose.foundation.ScrollState,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onGestureDetected: (String) -> Unit,
    coordinateTranslation: () -> Offset = { Offset.Zero }
): Modifier = this.composed {
    val currentCoordinator by rememberUpdatedState(coordinator)
    val currentSelectionState by rememberUpdatedState(selectionState)
    val currentDragPhase by rememberUpdatedState(dragPhase)
    val currentDensity by rememberUpdatedState(density)
    val currentOnGestureDetected by rememberUpdatedState(onGestureDetected)
    val zoomValue by rememberUpdatedState(currentZoom)
    val vScroll by rememberUpdatedState(verticalScrollState)
    val hScroll by rememberUpdatedState(horizontalScrollState)
    val currentTranslation by rememberUpdatedState(coordinateTranslation)

    this.pointerInput(Unit) {
        val viewConf = this.viewConfiguration
        awaitEachGesture {
            val down: PointerInputChange = awaitFirstDown()
            currentOnGestureDetected("Down")
            
            // Immediate focus on first touch to hide keyboard latency
            try { focusRequester.requestFocus() } catch (_: Exception) {}
            
            val translatedDown = down.position + currentTranslation()
            currentCoordinator.onCommand(WEditorCommand.UpdateTelemetry { it.copy(currentStage = 1, pointerPosition = translatedDown) })
            
            var dragStarted = false
            val longPressTimeout = viewConf.longPressTimeoutMillis
            
            val decision = withTimeoutOrNull(longPressTimeout) {
                var dragChange: PointerInputChange? = null
                while (dragChange == null) {
                    val event = awaitPointerEvent()
                    
                    if (event.changes.size > 1) {
                        return@withTimeoutOrNull GestureDecision.MultiTouch
                    }

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
                    currentCoordinator.onTap(translatedDown)
                    return@awaitEachGesture
                }
                is GestureDecision.LongPress -> {
                    if (currentDragPhase == WDragPhase.IDLE || currentDragPhase == WDragPhase.COPY_ARMED) {
                        currentOnGestureDetected("Long Press")
                        isSelectionDragging = !currentCoordinator.isOffsetInsideSelection(translatedDown)
                        currentCoordinator.onLongPress(translatedDown)
                    }
                }
                is GestureDecision.Drag -> {
                    dragStarted = true
                    currentOnGestureDetected("Drag Start")
                    isSelectionDragging = currentCoordinator.onDragStart(translatedDown, currentSelectionState, currentDensity)
                }
                is GestureDecision.MultiTouch -> {
                    currentOnGestureDetected("Pinch Zoom")
                    handlePinchZoom(zoomValue, vScroll, hScroll, currentCoordinator)
                    return@awaitEachGesture
                }
            }
            
            // Main movement loop
            while (true) {
                val event = awaitPointerEvent()
                
                if (event.changes.size > 1) {
                    currentOnGestureDetected("Pinch Zoom (Mid-gesture transition)")
                    handlePinchZoom(zoomValue, vScroll, hScroll, currentCoordinator)
                    break
                }

                val change = event.changes.first()
                val translatedPos = change.position + currentTranslation()
                
                if (change.changedToUp()) {
                    if ((currentDragPhase != WDragPhase.IDLE) || dragStarted) {
                        currentOnGestureDetected("Drop")
                        currentCoordinator.onCommand(WEditorCommand.CompleteDrag(null))
                    }
                    if (currentSelectionState.activeHandle != null) {
                        currentCoordinator.onCommand(WEditorCommand.UpdateActiveHandle(null))
                    }
                    break
                }
                
                if (currentDragPhase != WDragPhase.IDLE && currentDragPhase != WDragPhase.COPY_ARMED) {
                    currentOnGestureDetected("Dragging (Lift)")
                    currentCoordinator.onCommand(WEditorCommand.UpdateDragPosition(translatedPos))
                    change.consume()
                } else if (isSelectionDragging && currentSelectionState.activeHandle == null) {
                    currentOnGestureDetected("Selection Dragging")
                    currentCoordinator.onSelectionDrag(translatedPos)
                    change.consume()
                } else {
                    val dragAmount = change.position - down.position
                    if (!dragStarted && dragAmount.getDistance() > viewConfiguration.touchSlop) {
                        dragStarted = true
                        currentOnGestureDetected("Drag Start")
                        isSelectionDragging = currentCoordinator.onDragStart(translatedDown, currentSelectionState, currentDensity)
                    }
                    
                    if (dragStarted) {
                        currentCoordinator.onDrag(translatedPos, currentSelectionState.activeHandle)
                        if (currentSelectionState.activeHandle != null || isSelectionDragging) {
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.handlePinchZoom(
    currentZoom: Float,
    verticalScrollState: androidx.compose.foundation.ScrollState,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    coordinator: WGestureCoordinator
) {
    var zoom = currentZoom
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.isEmpty() || event.changes.any { it.changedToUp() }) break
        
        val zoomChange = event.calculateZoom()
        if (zoomChange != 1f) {
            val centroid = event.calculateCentroid()
            val newZoom = (zoom * zoomChange).coerceIn(0.5f, 2.0f)
            val effectiveZoomChange = newZoom / zoom
            
            // Adjust scroll to keep centroid stable
            val vOffset = (verticalScrollState.value + centroid.y) * effectiveZoomChange - centroid.y
            val hOffset = (horizontalScrollState.value + centroid.x) * effectiveZoomChange - centroid.x
            
            zoom = newZoom
            coordinator.onCommand(WEditorCommand.UpdateZoom(zoom))
            
            // Dispatch scroll updates
            val vDelta = vOffset - verticalScrollState.value
            val hDelta = hOffset - horizontalScrollState.value
            
            verticalScrollState.dispatchRawDelta(vDelta)
            horizontalScrollState.dispatchRawDelta(hDelta)
            
            event.changes.forEach { it.consume() }
        }
    }
}

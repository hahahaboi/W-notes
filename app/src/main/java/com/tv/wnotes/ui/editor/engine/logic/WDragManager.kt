package com.tv.wnotes.ui.editor.engine.logic

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.state.*

/**
 * Pure Kotlin Drag Manager for WEditor.
 * Single source of truth for the drag lifecycle.
 */
class WDragManager(
    private var layoutProvider: WLayoutProvider,
    private val onEvent: (WDragEvent) -> Unit,
    private val onTelemetry: ((WTelemetryState) -> WTelemetryState) -> Unit = {}
) {
    private val _state = mutableStateOf(WDragState())
    val state: WDragState get() = _state.value

    fun updateLayoutProvider(newProvider: WLayoutProvider) {
        this.layoutProvider = newProvider
    }

    /**
     * Transition: IDLE -> PREPARING_LIFT
     */
    fun prepareLift(range: WSelectionRange, position: Offset, snapshot: WEditorSnapshot) {
        println("DROP STAGE 1: Drag Started")
        onTelemetry { it.copy(dragPhase = WDragPhase.PREPARING_LIFT, pointerPosition = position) }
        println("STAGE 5: prepareLift() for range $range at $position")
        val preview = WDragPreviewManager.buildPreview(snapshot, layoutProvider, position)
        _state.value = WDragState(
            phase = WDragPhase.PREPARING_LIFT,
            selectedRange = range,
            fingerPosition = position,
            liftProgress = 0f,
            preview = preview
        )
        println("STAGE 6: dragState = PreparingLift")
    }

    /**
     * Transition: PREPARING_LIFT -> LIFTING
     */
    fun startLifting() {
        if (state.phase != WDragPhase.PREPARING_LIFT) {
            println("Execution stopped: startLifting called while phase was ${state.phase}")
            return
        }
        _state.value = state.copy(
            phase = WDragPhase.LIFTING,
            preview = state.preview.copy(elevation = 8f, scale = 1.05f)
        )
        println("STAGE 7: dragState = Lifting")
    }

    /**
     * Updates the visual lift progress (0.0 -> 1.0).
     */
    fun updateLiftProgress(progress: Float) {
        if (state.phase != WDragPhase.LIFTING) return
        val clampedProgress = progress.coerceIn(0f, 1f)
        
        _state.value = state.copy(
            liftProgress = clampedProgress,
            preview = state.preview.copy(
                elevation = 8f + (clampedProgress * 8f), // 8 -> 16
                scale = 1.05f + (clampedProgress * 0.03f), // 1.05 -> 1.08
                opacity = 1.0f - (clampedProgress * 0.04f) // 1.0 -> 0.96
            )
        )
        
        if (clampedProgress >= 1.0f) {
            _state.value = state.copy(phase = WDragPhase.DRAGGING)
            println("STAGE 8: dragState = Dragging")
        }
    }

    /**
     * Updates current finger position and calculates insertion caret.
     */
    fun updatePosition(position: Offset, snapshot: WEditorSnapshot) {
        if (state.phase == WDragPhase.IDLE) return
        
        onTelemetry { it.copy(currentStage = 2, pointerPosition = position, dragPhase = state.phase) }

        // 1. Calculate preview offset
        val initialPos = state.fingerPosition
        val previewOffset = if (initialPos != Offset.Unspecified) {
            position - initialPos
        } else {
            Offset.Zero
        }

        // 2. Calculate predictive insertion caret
        val caret = WInsertionCaretManager.calculateInsertionState(
            fingerPosition = position,
            dragState = state,
            layoutProvider = layoutProvider,
            blocks = snapshot.document.blocks
        )

        _state.value = state.copy(
            fingerPosition = position,
            previewOffset = previewOffset,
            preview = state.preview.copy(position = position),
            insertionCaret = caret
        )
        println("DROP STAGE 6: Drop Caret Updated - Block: ${caret.insertionPoint?.blockId}, Offset: ${caret.insertionPoint?.offset}")
        
        onTelemetry { 
            it.copy(
                currentStage = 5,
                hitTestResult = if (caret.isVisible) "Success (Stage 3)" else "Failed",
                hoverBlock = caret.insertionPoint?.blockId ?: "None (Stage 4)",
                hoverLine = caret.lineIndex,
                hoverOffset = caret.insertionPoint?.offset ?: -1,
                dropCaretVisible = caret.isVisible,
                dropCaretScreenPosition = caret.drawRect.topLeft,
                dropValid = caret.isValidTarget,
                virtualLineCount = caret.virtualLineCount,
                isVirtual = caret.isVirtual
            ) 
        }
    }

    /**
     * Transition: DRAGGING -> DROPPED (Success)
     */
    fun endDrag(targetPoint: WSelectionPoint?) {
        println("DROP STAGE 8: Drop Released")
        onTelemetry { it.copy(currentStage = 7, dragPhase = WDragPhase.DROPPED) }
        val range = state.selectedRange
        val virtualLines = state.insertionCaret.virtualLineCount
        
        if (state.phase == WDragPhase.DRAGGING && range != null && targetPoint != null && state.insertionCaret.isValidTarget) {
            _state.value = state.copy(phase = WDragPhase.DROPPED)
            onEvent(WDragEvent.DropExecuted(range, targetPoint, virtualLines))
        } else {
            cancel()
        }
    }

    /**
     * Called by UI after landing animation is complete.
     */
    fun completeDropCleanup() {
        println("STAGE 15: Cleanup complete")
        reset()
    }

    /**
     * Transition: Any -> IDLE (Failure/Cancel)
     */
    fun cancel() {
        if (state.phase != WDragPhase.IDLE) {
            onEvent(WDragEvent.DragCancelled)
            reset()
        }
    }

    private fun reset() {
        _state.value = WDragState()
    }
}

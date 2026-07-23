package com.tv.wnotes.ui.editor

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.state.*
import com.tv.wnotes.ui.editor.engine.logic.*
import com.tv.wnotes.ui.editor.engine.input.WInputController
import com.tv.wnotes.ui.editor.engine.clipboard.WClipboardManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * The bridge between the application and the WEditor engine.
 */
@Stable
class WEditorHost(
    val engineState: com.tv.wnotes.ui.editor.engine.state.WEditorState,
    val editorState: WEditorState,
    val historyManager: WHistoryManager,
    val inputController: WInputController,
    val clipboardManager: WClipboardManager,
    val gestureCoordinator: WGestureCoordinator,
    val dragManager: WDragManager
) {
    val snapshot: State<WEditorSnapshot> = engineState.snapshot

    fun updateLayoutProvider(layoutProvider: WLayoutProvider) {
        gestureCoordinator.updateLayoutProvider(layoutProvider)
        dragManager.updateLayoutProvider(layoutProvider)
    }

    /**
     * Synchronizes the engine state with the UI-level manager state.
     */
    fun syncInternalState() {
        val current = engineState.snapshot.value
        if (current.drag != dragManager.state) {
            engineState.updateSnapshot(current.copy(drag = dragManager.state))
        }
    }
}

@Composable
fun rememberWEditorHost(
    initialDocument: WDocument,
    layoutProvider: WLayoutProvider = EmptyLayoutProvider
): WEditorHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = remember(initialDocument.id) {
        val engineState = com.tv.wnotes.ui.editor.engine.state.WEditorState(initialDocument)
        val uiState = WEditorState(engineState.snapshot)
        val history = WHistoryManager()
        val clipboard = WClipboardManager(context)
        val input = WInputController(engineState, history, clipboard)
        
        val drag = WDragManager(
            layoutProvider = layoutProvider, 
            onEvent = { event ->
                when (event) {
                    is WDragEvent.DropExecuted -> {
                        val currentSnapshot = engineState.snapshot.value
                        println("STAGE 13: MOVE_RANGE executed")
                        println("DROP STAGE 9: MOVE_RANGE Executed")
                        engineState.updateTelemetry { it.copy(currentStage = 8, moveRangePending = true) }
                        
                        val normalization = WDropWhitespaceEngine.normalizeDropWhitespace(
                            event.sourceRange,
                            event.targetPoint,
                            currentSnapshot.document
                        )
                        
                        engineState.updateTelemetry { it.copy(smartWhitespace = normalization.telemetry) }

                        val result = WMutationEngine.moveRange(
                            currentSnapshot,
                            event.sourceRange,
                            event.targetPoint,
                            prefix = normalization.prefix,
                            suffix = normalization.suffix,
                            virtualLineCount = event.virtualLineCount
                        )
                        engineState.applyMutation(result)
                        println("DROP STAGE 10: Selection Updated")
                        
                        engineState.updateTelemetry { 
                            it.copy(
                                currentStage = if (result is com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Success) 10 else 8,
                                moveRangePending = false,
                                moveRangeSuccess = result is com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Success,
                                failureReason = if (result is com.tv.wnotes.ui.editor.engine.logic.WMutationResult.Failure) result.reason else null
                            )
                        }
                    }
                    is WDragEvent.DragCancelled -> {
                        println("Drag cancelled")
                        engineState.resetTelemetry()
                    }
                    else -> {}
                }
            },
            onTelemetry = { engineState.updateTelemetry(it) }
        )

        val gesture = WGestureCoordinator(
            layoutProvider = layoutProvider,
            onCommand = { command ->
                when (command) {
                    is WEditorCommand.MoveCursor -> {
                        engineState.moveCursor(command.point, command.affinity)
                    }
                    is WEditorCommand.SelectWord -> {
                        engineState.selectWord(command.point)
                    }
                    is WEditorCommand.SelectParagraph -> {
                        engineState.selectParagraph(command.point)
                    }
                    is WEditorCommand.ExtendSelection -> {
                        engineState.extendSelection(command.point)
                    }
                    is WEditorCommand.ClearSelection -> {
                        engineState.clearSelection()
                    }
                    is WEditorCommand.SplitBlock -> {
                        input.onEnter()
                    }
                    is WEditorCommand.DeleteBackward -> {
                        input.onDeleteBackward()
                    }
                    is WEditorCommand.DeleteForward -> {
                        input.onDeleteForward()
                    }
                    is WEditorCommand.NavigateCursor -> {
                        input.onNavigation(command.direction, layoutProvider)
                    }
                    is WEditorCommand.UpdateTelemetry -> {
                        engineState.updateTelemetry(command.transform)
                    }
                    is WEditorCommand.BeginDragSelection -> {
                        println("STAGE 4.5: WEditorHost receiving BeginDragSelection")
                        val currentSnapshot = engineState.snapshot.value
                        val currentSelection = currentSnapshot.selection
                        if (currentSelection.isActive && currentSelection.ranges.isNotEmpty()) {
                            // Start the lift lifecycle
                            drag.prepareLift(currentSelection.ranges.first(), command.position, currentSnapshot)
                            
                            // Automatic transition for the "Lift" experience
                            scope.launch {
                                kotlinx.coroutines.delay(150)
                                drag.startLifting()
                                repeat(10) { i ->
                                    kotlinx.coroutines.delay(16)
                                    drag.updateLiftProgress((i + 1) / 10f)
                                }
                            }
                        } else {
                            println("Execution stopped: No active selection in snapshot")
                        }
                    }
                    is WEditorCommand.StartPreparingLift -> {
                        drag.prepareLift(command.range, command.position, engineState.snapshot.value)
                    }
                    is WEditorCommand.ExecuteLift -> {
                        drag.startLifting()
                    }
                    is WEditorCommand.UpdateLiftProgress -> {
                        drag.updateLiftProgress(command.progress)
                    }
                    is WEditorCommand.UpdateDragPosition -> {
                        drag.updatePosition(command.position, engineState.snapshot.value)
                    }
                    is WEditorCommand.CompleteDrag -> {
                        drag.endDrag(drag.state.insertionCaret.insertionPoint)
                    }
                    is WEditorCommand.CompleteDropCleanup -> {
                        drag.completeDropCleanup()
                    }
                    is WEditorCommand.CancelDrag -> {
                        drag.cancel()
                    }
                    else -> println("WEditor Command Received: $command")
                }
            }
        )
        
        WEditorHost(
            engineState = engineState,
            editorState = uiState,
            historyManager = history,
            inputController = input,
            clipboardManager = clipboard,
            gestureCoordinator = gesture,
            dragManager = drag
        )
    }

    // Keep the snapshot in sync with drag manager state
    val currentDragState = host.dragManager.state
    SideEffect {
        host.syncInternalState()
    }

    return host
}

private object EmptyLayoutProvider : WLayoutProvider {
    override fun getSelectionPointForOffset(offset: Offset): WSelectionPoint? = null
    override fun isOffsetInsideSelection(offset: Offset): Boolean = false
    override fun getInsertionBeamForPoint(point: WSelectionPoint): androidx.compose.ui.geometry.Rect? = null
    override fun getSelectionBounds(): List<androidx.compose.ui.geometry.Rect> = emptyList()
    override fun hitTest(offset: Offset): WHitResult? = null
}

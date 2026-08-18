package com.tv.wnotes.ui.editor

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.state.*
import com.tv.wnotes.ui.editor.engine.logic.*
import com.tv.wnotes.ui.editor.engine.input.WInputController
import com.tv.wnotes.ui.editor.engine.input.WShortcut
import com.tv.wnotes.ui.editor.engine.clipboard.WClipboardManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList

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
    val dragManager: WDragManager,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    val snapshot: State<WEditorSnapshot> = engineState.snapshot
    private var pendingAnnotationJob: kotlinx.coroutines.Job? = null

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

    fun dispatchCommand(command: WEditorCommand) {
        // Gesture Disambiguation: Cancel pending annotation show if any other gesture starts
        if (command !is WEditorCommand.ShowAnnotation && command !is WEditorCommand.DismissAnnotation) {
            pendingAnnotationJob?.cancel()
        }

        // Automatic Dismissal: Dismiss current annotation if user interacts elsewhere
        if (snapshot.value.activeAnnotationId != null) {
            when (command) {
                is WEditorCommand.MoveCursor,
                is WEditorCommand.SelectWord,
                is WEditorCommand.SelectParagraph,
                is WEditorCommand.BeginSelection,
                is WEditorCommand.ClearSelection,
                is WEditorCommand.DeleteBackward,
                is WEditorCommand.DeleteForward,
                is WEditorCommand.SplitBlock -> {
                    engineState.dismissAnnotation()
                }
                else -> {}
            }
        }

        when (command) {
            is WEditorCommand.ShowAnnotation -> {
                pendingAnnotationJob?.cancel()
                pendingAnnotationJob = scope.launch {
                    kotlinx.coroutines.delay(300) // MULTI_TAP_TIMEOUT
                    engineState.showAnnotation(command.id)
                }
            }
            is WEditorCommand.MoveCursor -> engineState.moveCursor(command.point, command.affinity)
            is WEditorCommand.SelectWord -> engineState.selectWord(command.point)
            is WEditorCommand.SelectParagraph -> engineState.selectParagraph(command.point)
            is WEditorCommand.ExtendSelection -> engineState.extendSelection(command.point)
            is WEditorCommand.UpdateActiveHandle -> engineState.updateActiveHandle(command.handleType)
            is WEditorCommand.MoveSelectionHandle -> engineState.moveSelectionHandle(command.handleType, command.point)
            is WEditorCommand.ActivateSelection -> engineState.activateSelection(command.rangeId)
            is WEditorCommand.RemoveSelectionRange -> engineState.removeSelectionRange(command.rangeId)
            is WEditorCommand.SetSelectionInteraction -> engineState.setInteractionMode(command.mode)
            is WEditorCommand.ApplySpan -> {
                val result = WMutationEngine.applySpanToSelection(engineState.snapshot.value, command.spanType)
                engineState.applyMutation(result)
            }
            is WEditorCommand.InsertBlock -> {
                val result = WMutationEngine.insertBlock(engineState.snapshot.value, command.block, command.index)
                engineState.applyMutation(result)
            }
            is WEditorCommand.UpdateBlock -> {
                val result = WMutationEngine.updateBlock(engineState.snapshot.value, command.block)
                engineState.applyMutation(result)
            }
            is WEditorCommand.ClearSelection -> engineState.clearSelection()
            is WEditorCommand.ClearSelectionAndHideCursor -> engineState.clearSelectionAndHideCursor()
            is WEditorCommand.SelectAll -> inputController.onKeyboardShortcut(WShortcut.SelectAll)
            is WEditorCommand.Copy -> inputController.onKeyboardShortcut(WShortcut.Copy)
            is WEditorCommand.Cut -> inputController.onKeyboardShortcut(WShortcut.Cut)
            is WEditorCommand.Paste -> inputController.onKeyboardShortcut(WShortcut.Paste)
            is WEditorCommand.SplitBlock -> inputController.onEnter()
            is WEditorCommand.DeleteBackward -> inputController.onDeleteBackward()
            is WEditorCommand.DeleteForward -> inputController.onDeleteForward()
            is WEditorCommand.NavigateCursor -> inputController.onNavigation(command.direction, gestureCoordinator.layoutProvider)
            is WEditorCommand.UpdateTelemetry -> engineState.updateTelemetry(command.transform)
            is WEditorCommand.BeginDragSelection -> {
                val currentSnapshot = engineState.snapshot.value
                val currentSelection = currentSnapshot.selection
                if (currentSelection.isActive && currentSelection.ranges.isNotEmpty()) {
                    dragManager.prepareLift(currentSelection.ranges, command.position, currentSnapshot)
                    scope.launch {
                        kotlinx.coroutines.delay(150)
                        dragManager.startLifting()
                        repeat(10) { i ->
                            kotlinx.coroutines.delay(16)
                            dragManager.updateLiftProgress((i + 1) / 10f)
                        }
                    }
                }
            }
            is WEditorCommand.TapBelowDocument -> {
                val currentSnapshot = engineState.snapshot.value
                val bottomY = gestureCoordinator.layoutProvider.getDocumentBottom()
                val distance = command.offset.y - bottomY
                val density = gestureCoordinator.layoutProvider.density
                val lineHeightPx = 24 * density
                val numLines = (distance / lineHeightPx).toInt().coerceAtLeast(1)
                val result = WMutationEngine.extendDocument(currentSnapshot, numLines)
                engineState.applyMutation(result)
            }
            is WEditorCommand.StartPreparingLift -> dragManager.prepareLift(listOf(command.range), command.position, engineState.snapshot.value)
            is WEditorCommand.ExecuteLift -> dragManager.startLifting()
            is WEditorCommand.UpdateLiftProgress -> dragManager.updateLiftProgress(command.progress)
            is WEditorCommand.UpdateDragPosition -> dragManager.updatePosition(command.position, engineState.snapshot.value)
            is WEditorCommand.CompleteDrag -> dragManager.endDrag(dragManager.state.insertionCaret.insertionPoint)
            is WEditorCommand.CompleteDropCleanup -> dragManager.completeDropCleanup()
            is WEditorCommand.CancelDrag -> dragManager.cancel()
            is WEditorCommand.CreateAnnotation -> {
                val result = WMutationEngine.annotateRange(engineState.snapshot.value, command.content, command.range, command.anchorText)
                if (result is WMutationResult.Success) {
                    inputController.onOperation(WOperation.GeneralMutation(engineState.snapshot.value, result.snapshot, result.mutationType))
                }
            }
            is WEditorCommand.UpdateAnnotation -> {
                val result = WMutationEngine.updateAnnotation(engineState.snapshot.value, command.id, command.content)
                if (result is WMutationResult.Success) {
                    inputController.onOperation(WOperation.GeneralMutation(engineState.snapshot.value, result.snapshot, result.mutationType))
                }
            }
            is WEditorCommand.DeleteAnnotation -> {
                val result = WMutationEngine.deleteAnnotation(engineState.snapshot.value, command.id)
                if (result is WMutationResult.Success) {
                    inputController.onOperation(WOperation.GeneralMutation(engineState.snapshot.value, result.snapshot, result.mutationType))
                }
            }
            is WEditorCommand.DismissAnnotation -> engineState.dismissAnnotation()
            is WEditorCommand.StartCreatingAnnotation -> {
                engineState.startCreatingAnnotation(command.range, command.anchorText)
            }
            is WEditorCommand.CancelCreatingAnnotation -> engineState.cancelCreatingAnnotation()
            is WEditorCommand.MakeTabFromSelection -> { /* Handled in remember block via lambda */ }
            else -> println("WEditor Command Received: $command")
        }
    }
}

@Composable
fun rememberWEditorHost(
    initialDocument: WDocument,
    initialAnnotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation> = emptyList(),
    initialSnapshot: WEditorSnapshot? = null,
    layoutProvider: WLayoutProvider = EmptyLayoutProvider,
    onMakeTab: (String) -> Unit = {},
    initialCursorAtEnd: Boolean = false
): WEditorHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnMakeTab by rememberUpdatedState(onMakeTab)

    val host = remember(initialDocument.id) {
        val engineState = if (initialSnapshot != null) {
            com.tv.wnotes.ui.editor.engine.state.WEditorState(initialSnapshot)
        } else {
            com.tv.wnotes.ui.editor.engine.state.WEditorState(initialDocument, initialAnnotations, initialCursorAtEnd)
        }
        val uiState = WEditorState(engineState.snapshot)
        val history = WHistoryManager()
        val clipboard = WClipboardManager(context)
        val input = WInputController(engineState, history, clipboard)
        
        // We use a late-init approach or a proxy for onCommand to avoid circular dependency
        var hostInstance: WEditorHost? = null
        
        val onCommandProxy: (WEditorCommand) -> Unit = { command ->
            if (command is WEditorCommand.MakeTabFromSelection) {
                val selectedText = WSelectionManager.getSelectedText(
                    engineState.snapshot.value.selection,
                    engineState.snapshot.value.document.blocks
                )
                if (selectedText.isNotEmpty()) {
                    currentOnMakeTab(selectedText)
                    engineState.clearSelection()
                }
            } else {
                hostInstance?.dispatchCommand(command)
            }
        }

        val drag = WDragManager(
            layoutProvider = layoutProvider, 
            onEvent = { event ->
                if (event is WDragEvent.DropExecuted) {
                    val currentSnapshot = engineState.snapshot.value
                    val normalization = WDropWhitespaceEngine.normalizeDropWhitespace(
                        event.sourceRanges.first(),
                        event.targetPoint,
                        currentSnapshot.document
                    )
                    val result = WMutationEngine.moveRanges(
                        currentSnapshot,
                        event.sourceRanges,
                        event.targetPoint,
                        prefix = normalization.prefix,
                        suffix = normalization.suffix,
                        virtualLineCount = event.virtualLineCount
                    )
                    engineState.applyMutation(result)
                }
            },
            onTelemetry = { engineState.updateTelemetry(it) }
        )

        val gesture = WGestureCoordinator(layoutProvider, onCommandProxy)

        WEditorHost(
            engineState = engineState,
            editorState = uiState,
            historyManager = history,
            inputController = input,
            clipboardManager = clipboard,
            gestureCoordinator = gesture,
            dragManager = drag,
            scope = scope
        ).also { hostInstance = it }
    }

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
    override fun getAnnotationBounds(annotationId: String): List<androidx.compose.ui.geometry.Rect> = emptyList()
    override fun getAnnotationGeometry(annotationId: String): com.tv.wnotes.ui.editor.engine.logic.AnnotationGeometry? = null
    override fun hitTest(offset: Offset): WHitResult? = null
    override fun getDocumentBottom(): Float = 0f
    override val density: Float = 1f
    override val currentSelection: com.tv.wnotes.ui.editor.engine.state.WSelectionState? = null
    override val currentBlocks: List<com.tv.wnotes.ui.editor.engine.model.WBlock> = emptyList()
    override val currentAnnotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation> = emptyList()
}

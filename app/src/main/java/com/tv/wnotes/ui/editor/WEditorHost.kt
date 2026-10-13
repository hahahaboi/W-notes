package com.tv.wnotes.ui.editor

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.tv.wnotes.ui.editor.engine.model.WBlock
import com.tv.wnotes.ui.editor.engine.model.WDocument
import com.tv.wnotes.ui.editor.engine.state.*
import com.tv.wnotes.ui.editor.engine.logic.*
import com.tv.wnotes.ui.editor.engine.input.WInputController
import com.tv.wnotes.ui.editor.engine.input.WShortcut
import com.tv.wnotes.ui.editor.engine.clipboard.WClipboardManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import com.tv.wnotes.ui.editor.engine.state.WEditorState as EngineState

/**
 * The bridge between the application and the WEditor engine.
 */
@Stable
class WEditorHost(
    val engineState: EngineState,
    val inputController: WInputController,
    val gestureCoordinator: WGestureCoordinator,
    val dragManager: WDragManager,
    val verticalScrollState: androidx.compose.foundation.ScrollState,
    private val scope: kotlinx.coroutines.CoroutineScope,
    val onCreateTabWithSelection: (List<WBlock>) -> Unit = {},
    val onAddToTab: (List<WBlock>) -> Unit = {},
    val onCrossTabMove: (Long, Long, List<WSelectionRange>, WSelectionPoint, Boolean) -> Unit = { _, _, _, _, _ -> }
) {
    val snapshot: State<WEditorSnapshot> = engineState.snapshot
    private var pendingAnnotationJob: kotlinx.coroutines.Job? = null
    private var navigationHighlightJob: kotlinx.coroutines.Job? = null

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
        if (snapshot.value.activeAnnotationId != null || snapshot.value.activeTieIds != null || dragManager.state.phase == WDragPhase.COPY_ARMED) {
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
                    engineState.dismissTiePopup()
                    if (dragManager.state.phase == WDragPhase.COPY_ARMED) {
                        dragManager.cancel()
                    }
                }
                else -> {}
            }
        }

        when (command) {
            is WEditorCommand.ShowAnnotation -> {
                pendingAnnotationJob?.cancel()
                pendingAnnotationJob = scope.launch {
                    delay(300.milliseconds) // MULTI_TAP_TIMEOUT
                    engineState.showAnnotation(command.id)
                }
            }
            is WEditorCommand.MoveCursor -> engineState.moveCursor(command.point, command.affinity)
            is WEditorCommand.SelectWord -> engineState.selectWord(command.point)
            is WEditorCommand.SelectParagraph -> engineState.selectParagraph(command.point)
            is WEditorCommand.ExtendSelection -> engineState.extendSelection(command.point)
            is WEditorCommand.UpdateActiveHandle -> engineState.updateActiveHandle(command.handleType)
            is WEditorCommand.MoveSelectionHandle -> engineState.moveSelectionHandle(command.handleType, command.point)
            is WEditorCommand.ActivateSelection -> {
                engineState.activateSelection(command.rangeId)
                dragManager.armCopy()
            }
            is WEditorCommand.RemoveSelectionRange -> engineState.removeSelectionRange(command.rangeId)
            is WEditorCommand.SetSelectionInteraction -> engineState.setInteractionMode(command.mode)
            is WEditorCommand.SetSelection -> engineState.setSelection(command.selection)
            is WEditorCommand.ApplySpan -> {
                val result = WMutationEngine.applySpanToSelection(engineState.snapshot.value, command.spanType)
                inputController.applyMutation(result)
            }
            is WEditorCommand.InsertBlock -> {
                val result = WMutationEngine.insertBlock(engineState.snapshot.value, command.block, command.index)
                inputController.applyMutation(result)
            }
            is WEditorCommand.UpdateBlock -> {
                val result = WMutationEngine.updateBlock(engineState.snapshot.value, command.block)
                inputController.applyMutation(result)
            }
            is WEditorCommand.ToggleBlockType -> {
                val result = WMutationEngine.toggleBlockType(engineState.snapshot.value, command.type)
                inputController.applyMutation(result)
            }
            is WEditorCommand.IncreaseIndent -> {
                val result = WMutationEngine.adjustIndent(engineState.snapshot.value, 1)
                inputController.applyMutation(result)
            }
            is WEditorCommand.DecreaseIndent -> {
                val result = WMutationEngine.adjustIndent(engineState.snapshot.value, -1)
                inputController.applyMutation(result)
            }
            is WEditorCommand.SetAlignment -> {
                val result = WMutationEngine.setAlignment(engineState.snapshot.value, command.alignment)
                inputController.applyMutation(result)
            }
            is WEditorCommand.SetFontSize -> {
                val result = WMutationEngine.applyFontSize(engineState.snapshot.value, command.size)
                inputController.applyMutation(result)
            }
            is WEditorCommand.AdjustFontSize -> {
                val result = WMutationEngine.adjustFontSize(engineState.snapshot.value, command.delta)
                inputController.applyMutation(result)
            }
            is WEditorCommand.ApplyFont -> {
                val result = WMutationEngine.applyFont(engineState.snapshot.value, command.fontFamily)
                inputController.applyMutation(result)
            }
            is WEditorCommand.UpdateTitle -> {
                val result = WMutationEngine.updateTitle(engineState.snapshot.value, command.title, command.cursorOffset)
                inputController.applyMutation(result)
            }
            is WEditorCommand.Undo -> inputController.onKeyboardShortcut(WShortcut.Undo)
            is WEditorCommand.Redo -> inputController.onKeyboardShortcut(WShortcut.Redo)
            is WEditorCommand.AIAssist -> {
                // TODO: Connect to AI service
                println("AI Assist requested for selection: ${snapshot.value.selection}")
            }
            is WEditorCommand.AddTextBox -> {
                val result = WMutationEngine.addTextBox(engineState.snapshot.value)
                inputController.applyMutation(result)
            }
            is WEditorCommand.SetDrawingMode -> {
                if (command.active) {
                    // Give the pen somewhere to draw: an area right below the cursor.
                    inputController.applyMutation(WMutationEngine.ensureDrawingBlock(engineState.snapshot.value))
                }
                engineState.setDrawingMode(command.active)
            }
            is WEditorCommand.ActivateNormalText -> {
                engineState.setDrawingMode(false)
                engineState.clearSelection()
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
            is WEditorCommand.UpdateZoom -> engineState.updateZoom(command.zoom)
            is WEditorCommand.BeginDragSelection -> {
                val currentSnapshot = engineState.snapshot.value
                val currentSelection = currentSnapshot.selection
                if (currentSelection.isActive && currentSelection.ranges.isNotEmpty()) {
                    dragManager.prepareLift(currentSelection.ranges, command.position, currentSnapshot)
                    scope.launch {
                        delay(150.milliseconds)
                        dragManager.startLifting()
                        repeat(10) { i ->
                            delay(16.milliseconds)
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
                inputController.applyMutation(result)
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
                inputController.applyMutation(result)
            }
            is WEditorCommand.UpdateAnnotation -> {
                val result = WMutationEngine.updateAnnotation(engineState.snapshot.value, command.id, command.content)
                inputController.applyMutation(result)
            }
            is WEditorCommand.DeleteAnnotation -> {
                val result = WMutationEngine.deleteAnnotation(engineState.snapshot.value, command.id)
                inputController.applyMutation(result)
            }
            is WEditorCommand.DismissAnnotation -> engineState.dismissAnnotation()
            is WEditorCommand.StartCreatingAnnotation -> {
                engineState.startCreatingAnnotation(command.range, command.anchorText)
            }
            is WEditorCommand.CancelCreatingAnnotation -> engineState.cancelCreatingAnnotation()
            
            is WEditorCommand.SetNavigationHighlight -> {
                engineState.setNavigationHighlight(command.range)
                if (command.range != null) {
                    navigationHighlightJob?.cancel()
                    navigationHighlightJob = scope.launch {
                        delay(2000.milliseconds)
                        engineState.setNavigationHighlight(null)
                    }
                }
            }

            is WEditorCommand.SetSearchHighlights -> {
                engineState.setSearchHighlights(command.ranges, command.activeRange)
            }

            // Tab Ties
            is WEditorCommand.StartTieTabs -> engineState.setTyingTabs(true)
            is WEditorCommand.CancelTieTabs -> engineState.setTyingTabs(false)
            is WEditorCommand.PlaceTabTie -> {
                val result = WMutationEngine.createTie(
                    engineState.snapshot.value,
                    command.destinationTabId,
                    command.range,
                    command.anchorText
                )
                inputController.applyMutation(result)
                engineState.setTyingTabs(false)
            }
            is WEditorCommand.ShowTabTieDestinations -> {
                engineState.setActiveTieIds(command.tieIds)
                if (!snapshot.value.isMarkerSidebarOpen) {
                    engineState.toggleMarkerSidebar()
                }
            }
            is WEditorCommand.JumpToTabTie -> {
                val tie = snapshot.value.ties.find { it.id == command.tieId }
                if (tie != null) {
                    onCrossTabMove(
                        tie.sourceTabId,
                        tie.destinationTabId,
                        emptyList<WSelectionRange>(),
                        WSelectionPoint(tie.destinationBlockId, tie.destinationStartOffset),
                        false
                    )
                }
            }

            is WEditorCommand.ToggleMarkerSidebar -> engineState.toggleMarkerSidebar()
            is WEditorCommand.CreateMarker -> {
                val result = WMutationEngine.createMarker(engineState.snapshot.value, command.label, command.range, command.anchorText)
                inputController.applyMutation(result)
            }
            is WEditorCommand.UpdateMarkerLabel -> {
                val result = WMutationEngine.updateMarkerLabel(engineState.snapshot.value, command.id, command.newLabel)
                inputController.applyMutation(result)
            }
            is WEditorCommand.UpdateMarkerType -> {
                val result = WMutationEngine.updateMarkerType(engineState.snapshot.value, command.id, command.newType)
                inputController.applyMutation(result)
            }
            is WEditorCommand.DeleteMarker -> {
                val result = WMutationEngine.deleteMarker(engineState.snapshot.value, command.id)
                inputController.applyMutation(result)
            }
            is WEditorCommand.NavigateToMarker -> {
                val marker = snapshot.value.markers.find { it.id == command.markerId }
                if (marker != null) {
                    val blockExists = snapshot.value.document.blocks.any { it.id == marker.startBlockId }
                    if (blockExists) {
                        engineState.setActiveMarker(marker.id)
                        val point = WSelectionPoint(marker.startBlockId, marker.startOffset)
                        engineState.moveCursor(point, CursorAffinity.AFTER)
                        
                        // Set temporary highlight
                        val range = WSelectionRange(
                            anchor = WSelectionPoint(marker.startBlockId, marker.startOffset),
                            focus = WSelectionPoint(marker.endBlockId, marker.endOffset)
                        )
                        engineState.setNavigationHighlight(range)
                        
                        // Clear highlight after delay
                        navigationHighlightJob?.cancel()
                        navigationHighlightJob = scope.launch {
                        delay(2000.milliseconds)
                            engineState.setNavigationHighlight(null)
                        }
                    } else {
                        // Optional: show feedback that marker is invalid
                        // For Phase 3, we just delete it if it's found to be invalid during navigation attempt
                        dispatchCommand(WEditorCommand.DeleteMarker(marker.id))
                    }
                }
            }
            is WEditorCommand.NavigateToNextMarker -> {
                val currentMarkers = WMarkerManager.sortMarkers(snapshot.value.markers, snapshot.value.document.blocks)
                if (currentMarkers.isEmpty()) return
                
                val cursor = snapshot.value.cursor.position
                val blocks = snapshot.value.document.blocks
                
                // Find the first marker starting after the cursor
                val nextMarker = currentMarkers.find { marker ->
                    val markerStart = WSelectionPoint(marker.startBlockId, marker.startOffset)
                    comparePoints(markerStart, cursor, blocks) > 0
                } ?: currentMarkers.first() // Wrap around or stay at end? Phase 5: find first after caret
                
                dispatchCommand(WEditorCommand.NavigateToMarker(nextMarker.id))
            }
            is WEditorCommand.NavigateToPreviousMarker -> {
                val currentMarkers = WMarkerManager.sortMarkers(snapshot.value.markers, snapshot.value.document.blocks)
                if (currentMarkers.isEmpty()) return

                val cursor = snapshot.value.cursor.position
                val blocks = snapshot.value.document.blocks

                // Find the last marker starting before the cursor
                val prevMarker = currentMarkers.findLast { marker ->
                    val markerStart = WSelectionPoint(marker.startBlockId, marker.startOffset)
                    comparePoints(markerStart, cursor, blocks) < 0
                } ?: currentMarkers.last()
                
                dispatchCommand(WEditorCommand.NavigateToMarker(prevMarker.id))
            }
            is WEditorCommand.OpenQuickJump -> engineState.setQuickJumpOpen(true)
            is WEditorCommand.CloseQuickJump -> engineState.setQuickJumpOpen(false)
            is WEditorCommand.CreateTabWithSelection -> onCreateTabWithSelection(command.selectedBlocks)
            is WEditorCommand.AddToTab -> onAddToTab(command.selectedBlocks)
            is WEditorCommand.SetGoal -> engineState.setGoal(command.point)
            is WEditorCommand.MoveGoal -> engineState.moveGoal(command.point)
            is WEditorCommand.RemoveGoal -> engineState.removeGoal()
            is WEditorCommand.MakeTabFromSelection -> {} // Obsolete
            else -> println("WEditor Command Received: $command")
        }
    }
}

@Composable
fun rememberWEditorHost(
    initialDocument: WDocument,
    initialAnnotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation> = emptyList(),
    initialMarkers: List<com.tv.wnotes.data.local.entity.TextMarker> = emptyList(),
    initialTies: List<TabTie> = emptyList(),
    initialSnapshot: WEditorSnapshot? = null,
    layoutProvider: WLayoutProvider = EmptyLayoutProvider,
    onCreateTabWithSelection: (List<WBlock>) -> Unit = {},
    onAddToTab: (List<WBlock>) -> Unit = {},
    initialCursorAtEnd: Boolean = false,
    onCommand: (WEditorCommand) -> Unit = {},
    onCrossTabMove: (Long, Long, List<WSelectionRange>, WSelectionPoint, Boolean) -> Unit = { _, _, _, _, _ -> }
): WEditorHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnCreateTabWithSelection by rememberUpdatedState(onCreateTabWithSelection)
    val currentOnAddToTab by rememberUpdatedState(onAddToTab)
    val currentOnCommand by rememberUpdatedState(onCommand)
    val currentOnCrossTabMove by rememberUpdatedState(onCrossTabMove)

    val host = remember(initialDocument.id) {
        val tabId = initialDocument.id.toLongOrNull() ?: -1L
        
        val engineState = if (initialSnapshot != null) {
            EngineState(initialSnapshot)
        } else {
            EngineState(
                initialDocument,
                initialAnnotations,
                initialMarkers,
                initialTies,
                cursorAtEnd = initialCursorAtEnd
            )
        }
        val history = WHistoryManager()
        val clipboard = WClipboardManager(context)
        val input = WInputController(engineState, history, clipboard)
        
        // We use a late-init approach or a proxy for onCommand to avoid circular dependency
        var hostInstance: WEditorHost? = null
        
        val onCommandProxy: (WEditorCommand) -> Unit = { command ->
            currentOnCommand(command)
            hostInstance?.dispatchCommand(command)
        }

        val drag = WDragManager(
            tabId = tabId,
            layoutProvider = layoutProvider,
            onEvent = { event ->
                when (event) {
                    is WDragEvent.DropExecuted -> {
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
                            virtualLineCount = event.virtualLineCount,
                            isCopy = event.isCopy
                        )
                        input.applyMutation(result)
                    }
                    else -> {}
                }
            },
            onTelemetry = { engineState.updateTelemetry(it) }
        )

        val gesture = WGestureCoordinator(layoutProvider, onCommandProxy)
        val scrollState = androidx.compose.foundation.ScrollState(initial = 0)

        WEditorHost(
            engineState = engineState,
            inputController = input,
            gestureCoordinator = gesture,
            dragManager = drag,
            verticalScrollState = scrollState,
            scope = scope,
            onCreateTabWithSelection = { currentOnCreateTabWithSelection(it) },
            onAddToTab = { currentOnAddToTab(it) },
            onCrossTabMove = currentOnCrossTabMove
        ).also { hostInstance = it }
    }

    SideEffect {
        host.syncInternalState()
    }

    return host
}

/**
 * Helper to compare two document positions.
 */
private fun comparePoints(p1: WSelectionPoint, p2: WSelectionPoint, blocks: List<com.tv.wnotes.ui.editor.engine.model.WBlock>): Int {
    if (p1.blockId == p2.blockId) return p1.offset - p2.offset
    val idx1 = blocks.indexOfFirst { it.id == p1.blockId }
    val idx2 = blocks.indexOfFirst { it.id == p2.blockId }
    return idx1 - idx2
}

private object EmptyLayoutProvider : WLayoutProvider {
    override fun getSelectionPointForOffset(offset: Offset): WSelectionPoint? = null
    override fun isOffsetInsideSelection(offset: Offset): Boolean = false
    override fun getInsertionBeamForPoint(point: WSelectionPoint): androidx.compose.ui.geometry.Rect? = null
    override fun getSelectionBounds(): List<androidx.compose.ui.geometry.Rect> = emptyList()
    override fun getSelectionBoundsForRange(range: WSelectionRange): List<androidx.compose.ui.geometry.Rect> = emptyList()
    override fun getAnnotationBounds(annotationId: String): List<androidx.compose.ui.geometry.Rect> = emptyList()
    override fun getAnnotationGeometry(annotationId: String): AnnotationGeometry? = null
    override fun hitTest(offset: Offset): WHitResult? = null
    override fun getDocumentBottom(): Float = 0f
    override val density: Float = 1f
    override val zoom: Float = 1.0f
    override val currentSelection: WSelectionState? = null
    override val currentBlocks: List<com.tv.wnotes.ui.editor.engine.model.WBlock> = emptyList()
    override val currentAnnotations: List<com.tv.wnotes.data.local.entity.ContextAnnotation> = emptyList()
    override fun getAnnotationsForBlock(blockId: String): List<com.tv.wnotes.data.local.entity.ContextAnnotation> = emptyList()
}

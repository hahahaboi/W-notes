package com.tv.wnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.ui.editor.engine.renderer.WLayoutState
import com.tv.wnotes.ui.editor.engine.state.WDragPhase
import com.tv.wnotes.ui.editor.engine.logic.WEditorCommand
import com.tv.wnotes.ui.editor.engine.renderer.WDragAnimationController
import com.tv.wnotes.ui.editor.engine.logic.WDragInternalState
import androidx.compose.ui.focus.FocusRequester

/**
 * Root composable of the next-generation editor.
 */
@Composable
fun WEditor(
    host: WEditorHost,
    title: String,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot by host.snapshot
    val layoutState = remember { WLayoutState() }
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val scope = rememberCoroutineScope()
    val animController = remember { WDragAnimationController(scope) }
    val focusRequester = remember { FocusRequester() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    
    // Sync density with layout state
    SideEffect {
        layoutState.density = density
    }
    
    var gestureText by remember { mutableStateOf("Gesture: IDLE") }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var contentRelativeOffset by remember { mutableStateOf(Offset.Zero) }
    var isInputLayerPlaced by remember { mutableStateOf(value = false) }
    
    // Toggle this to show/hide the telemetry overlay for debugging
    val showTelemetry = false
    
    // Auto-focus when the input layer is actually placed in the layout.
    // This avoids "BringIntoViewRequester to not be used before parents are placed" crash.
    LaunchedEffect(isInputLayerPlaced, editorCoordinates) {
        if ((isInputLayerPlaced) && (editorCoordinates != null)) {
            // Wait for at least one frame to ensure the placement is committed.
            kotlinx.coroutines.yield()
            try {
                if (editorCoordinates?.isAttached == true) {
                    focusRequester.requestFocus()
                }
            } catch (_: Exception) {
                // Fallback for cases where it's still not ready
            }
        }
    }

    // Update the host with the real layout provider implementation
    SideEffect {
        host.updateLayoutProvider(layoutState)
    }

    val cursor = snapshot.cursor
    val selection = snapshot.selection
    val drag = snapshot.drag

    // Sync animation controller with drag phase changes
    LaunchedEffect(drag.phase) {
        val internalState = when(drag.phase) {
            WDragPhase.IDLE -> WDragInternalState.Idle
            WDragPhase.PREPARING_LIFT -> WDragInternalState.PreparingLift
            WDragPhase.LIFTING -> WDragInternalState.Lifting
            WDragPhase.DRAGGING -> WDragInternalState.Dragging
            WDragPhase.DROPPED -> WDragInternalState.Dropped
            WDragPhase.CANCELLED -> WDragInternalState.Cancelled
        }
        animController.onDragStateChanged(internalState)
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current.density

    // Sync state for hit-testing accuracy - Use SideEffect to avoid lag in touch processing
    SideEffect {
        layoutState.currentSelection = selection
        layoutState.currentBlocks = snapshot.document.blocks
        layoutState.currentAnnotations = snapshot.annotations
        layoutState.density = currentDensity
    }

    // Update drag position in anim controller
    LaunchedEffect(drag.fingerPosition) {
        if (drag.fingerPosition != androidx.compose.ui.geometry.Offset.Unspecified) {
            animController.updateDragPosition(drag.fingerPosition)
        }
    }
    
    // Landing animation logic
    LaunchedEffect(drag.phase) {
        if (drag.phase == WDragPhase.DROPPED) {
            val targetPos = drag.insertionCaret.drawRect.topLeft
            println("STAGE 14: Landing animation started towards $targetPos")
            animController.animateDrop(targetPos)
            host.gestureCoordinator.onCommand(WEditorCommand.CompleteDropCleanup)
        }
    }

    // Sync physical handle positions with layout - DOCUMENT LOCAL
    // We remove scrollState.value to avoid expensive ViewModel updates during scroll
    LaunchedEffect(selection, editorCoordinates, snapshot.document.revision) {
        if (selection.isActive) {
            val (start, end) = com.tv.wnotes.ui.editor.engine.logic.WSelectionHandleManager.calculateHandlePositions(snapshot, layoutState)
            host.engineState.updateHandlePositions(start, end)
        }
    }

    // Cursor blinking logic (Local state for better performance and reliability)
    var cursorBlinkVisible by remember { mutableStateOf(true) }
    LaunchedEffect(cursor.position, cursor.isVisible, snapshot.document.revision) {
        if (cursor.isVisible) {
            cursorBlinkVisible = true
            while (true) {
                kotlinx.coroutines.delay(500)
                cursorBlinkVisible = !cursorBlinkVisible
            }
        } else {
            cursorBlinkVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { editorCoordinates = it }
    ) {
        if (editorCoordinates != null) {
            // 3. Input Layer (Hidden Proxy) - Zero-sized and below gestures
            // Moved inside the if-block to ensure it is only placed when parent coordinates are ready.
            WEditorInputLayer(
                host = host,
                focusRequester = focusRequester,
                onPlaced = { isInputLayerPlaced = true }
            )

            // 1. Content Surface
            WEditorSurface(
                snapshot = snapshot,
                layoutState = layoutState,
                editorCoordinates = editorCoordinates,
                animController = animController,
                cursorBlinkVisible = cursorBlinkVisible,
                scrollState = scrollState,
                title = title,
                onTitleChange = onTitleChange,
                gestureCoordinator = host.gestureCoordinator,
                focusRequester = focusRequester,
                onGestureDetected = { gestureText = it },
                onCaretRendered = {
                    host.engineState.updateTelemetry { it.copy(currentStage = 6, dropCaretVisible = true) }
                },
                onContentPositioned = { contentRelativeOffset = it },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Interaction Overlay (Drag Preview, Multi-Selection Toolbar)
            // Stays on top to allow toolbar clicks
            WEditorOverlay(
                snapshot = snapshot,
                layoutState = layoutState,
                animController = animController,
                onCommand = { host.gestureCoordinator.onCommand(it) },
                contentRelativeOffset = { contentRelativeOffset },
                modifier = Modifier.fillMaxSize()
            )

            // 5. Scrollbar Layer - TOPMOST
            com.tv.wnotes.ui.components.WScrollbar(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // 5. Telemetry Dashboard (Above everything)
        if (showTelemetry) {
            val tel = snapshot.telemetry
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .width(240.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(8.dp)
            ) {
                Text("WEDITOR TELEMETRY (STAGE 13)", color = Color.Yellow, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))

                DebugRow("Current Stage", tel.currentStage.toString())
                DebugRow("Pointer Pos", "${tel.pointerPosition.x.toInt()}, ${tel.pointerPosition.y.toInt()}")
                DebugRow("Drag Phase", tel.dragPhase.toString())
                
                Spacer(Modifier.height(4.dp))
                Text("HIT TEST", color = Color.Cyan, fontSize = 9.sp)
                DebugRow("Result", tel.hitTestResult)
                DebugRow("Hover Block", tel.hoverBlock.take(8))
                DebugRow("Hover Line", tel.hoverLine.toString())
                DebugRow("Hover Offset", tel.hoverOffset.toString())
                
                Spacer(Modifier.height(4.dp))
                Text("DROP CARET", color = Color.Green, fontSize = 9.sp)
                DebugRow("Visible", tel.dropCaretVisible.toString())
                DebugRow("Screen Pos", "${tel.dropCaretScreenPosition.x.toInt()}, ${tel.dropCaretScreenPosition.y.toInt()}")
                DebugRow("Valid Target", tel.dropValid.toString())
                DebugRow("Virtual Lines", tel.virtualLineCount.toString())
                DebugRow("Is Virtual", tel.isVirtual.toString())
                
                Spacer(Modifier.height(4.dp))
                Text("LIFECYCLE", color = Color.Magenta, fontSize = 9.sp)
                DebugRow("Move Pending", tel.moveRangePending.toString())
                DebugRow("Move Success", tel.moveRangeSuccess.toString())
                
                if (tel.failureReason != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("FAILURE: ${tel.failureReason}", color = Color.Red, fontSize = 9.sp)
                }

                Spacer(Modifier.height(8.dp))
                val history = snapshot.history
                DebugRow("Undo Stack", history.undoStack.size.toString())
                DebugRow("Redo Stack", history.redoStack.size.toString())

                Spacer(Modifier.height(8.dp))
                Text("SMART WHITESPACE", color = Color.Green, fontSize = 10.sp)
                val sw = tel.smartWhitespace
                DebugRow("Before/After", "${sw.whitespaceBefore}/${sw.whitespaceAfter}")
                DebugRow("Start/End", "${sw.beginningOfBlock}/${sw.endOfBlock}")
                DebugRow("Words/Punc", "${sw.betweenWords}/${sw.adjacentPunctuation}")
                DebugRow("NL/Applied", "${sw.adjacentNewline}/${sw.insertedLeadingSpace || sw.insertedTrailingSpace}")
                DebugRow("Final Offset", sw.finalDestinationOffset.toString())
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 9.sp)
        Text(value, color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}

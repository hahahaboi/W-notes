package com.tv.wnotes.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.*

@Composable
fun DraggableTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    placeholder: @Composable () -> Unit = {}
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var dragPosition by remember { mutableStateOf<Int?>(null) }
    var isDraggingSelection by remember { mutableStateOf(false) }
    var dragCurrentOffset by remember { mutableStateOf(Offset.Zero) }
    
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // Store current state in refs to avoid pointerInput restarts
    val currentState = rememberUpdatedState(value)

    // Animations for the bubble
    val bubbleScale by animateFloatAsState(
        targetValue = if (isDraggingSelection) 1.3f else 0.7f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "bubbleScale"
    )
    
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDraggingSelection) 1f else 0f,
        animationSpec = tween(100),
        label = "bubbleAlpha"
    )

    // Make the source text invisible while dragging
    val draggingTransformation = remember(isDraggingSelection, value.selection) {
        VisualTransformation { text ->
            val annotated = buildAnnotatedString {
                append(text.text)
                if (isDraggingSelection && !value.selection.collapsed) {
                    addStyle(
                        style = SpanStyle(color = Color.Transparent),
                        start = value.selection.min,
                        end = value.selection.max
                    )
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { // USE Unit KEY TO PREVENT RESTARTS
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    val layout = textLayoutResult ?: return@awaitEachGesture
                    val offsetIndex = layout.getOffsetForPosition(down.position)
                    
                    val currentVal = currentState.value
                    val isInExistingSelection = !currentVal.selection.collapsed && 
                                                offsetIndex >= currentVal.selection.min && 
                                                offsetIndex < currentVal.selection.max

                    if (isInExistingSelection) {
                        down.consume()
                        
                        var longPressTriggered = false
                        try {
                            withTimeout(400L) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.first()
                                    if (change.changedToUp()) {
                                        // Tap inside selection -> Move cursor
                                        onValueChange(currentVal.copy(selection = TextRange(offsetIndex)))
                                        change.consume()
                                        throw CancellationException("Tap")
                                    }
                                    if ((change.position - down.position).getDistance() > 15f) {
                                        throw CancellationException("DragStartedEarly")
                                    }
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            longPressTriggered = true
                        } catch (e: CancellationException) {
                            // Handled tap
                        }

                        if (longPressTriggered) {
                            isDraggingSelection = true
                            dragCurrentOffset = down.position
                            dragPosition = offsetIndex
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val move = event.changes.firstOrNull()
                                if (move == null || !move.pressed) break
                                
                                move.consume()
                                dragCurrentOffset = move.position
                                dragPosition = layout.getOffsetForPosition(move.position)
                            }
                            
                            if (isDraggingSelection) {
                                val targetIdx = dragPosition
                                if (targetIdx != null) {
                                    val selectedText = currentVal.text.substring(currentVal.selection.min, currentVal.selection.max)
                                    val start = currentVal.selection.min
                                    val end = currentVal.selection.max
                                    val sb = StringBuilder(currentVal.text)
                                    sb.delete(start, end)
                                    
                                    var finalTarget = targetIdx
                                    if (finalTarget > start) {
                                        finalTarget = (finalTarget - (end - start)).coerceAtLeast(0)
                                    }
                                    finalTarget = finalTarget.coerceIn(0, sb.length)
                                    
                                    sb.insert(finalTarget, selectedText)
                                    onValueChange(
                                        TextFieldValue(
                                            text = sb.toString(),
                                            selection = TextRange(finalTarget, finalTarget + selectedText.length)
                                        )
                                    )
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                isDraggingSelection = false
                                dragPosition = null
                            }
                        }
                    } else {
                        // Paragraph Selection logic
                        var isLongPress = false
                        try {
                            withTimeout(400L) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.first()
                                    if (change.changedToUp()) break
                                    if ((change.position - down.position).getDistance() > 15f) break
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            isLongPress = true
                        }

                        if (isLongPress) {
                            val text = currentVal.text
                            var start = offsetIndex
                            while (start > 0 && text[start - 1] != '\n') start--
                            var end = offsetIndex
                            while (end < text.length && text[end] != '\n') end++
                            if (end < text.length && text[end] == '\n') end++
                            
                            onValueChange(currentVal.copy(selection = TextRange(start, end)))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            down.consume()
                        }
                    }
                }
            }
    ) {
        BasicTextField(
            value = value,
            onValueChange = { 
                if (!isDraggingSelection) onValueChange(it) 
            },
            modifier = Modifier.fillMaxSize(),
            textStyle = textStyle.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onTextLayout = { textLayoutResult = it },
            visualTransformation = draggingTransformation,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    if (value.text.isEmpty()) {
                        placeholder()
                    }
                    innerTextField()
                    
                    if (isDraggingSelection || bubbleAlpha > 0.01f) {
                        dragPosition?.let { pos ->
                            val layout = textLayoutResult
                            if (layout != null && isDraggingSelection) {
                                val cursorRect = layout.getCursorRect(pos)
                                Canvas(Modifier.fillMaxSize()) {
                                    val color = Color.Gray
                                    drawRect(color, cursorRect.topLeft, Size(3.dp.toPx(), cursorRect.height))
                                    drawRect(color, Offset(cursorRect.left - 6.dp.toPx(), cursorRect.top), Size(15.dp.toPx(), 2.5.dp.toPx()))
                                    drawRect(color, Offset(cursorRect.left - 6.dp.toPx(), cursorRect.bottom - 2.5.dp.toPx()), Size(15.dp.toPx(), 2.5.dp.toPx()))
                                }
                            }
                        }
                        
                        if (!value.selection.collapsed) {
                            val selectedText = value.text.substring(value.selection.min, value.selection.max)
                            val ghostText = if (selectedText.length > 25) selectedText.take(25) + "..." else selectedText
                            
                            Surface(
                                modifier = Modifier
                                    .graphicsLayer {
                                        translationX = dragCurrentOffset.x - (90.dp.toPx())
                                        translationY = dragCurrentOffset.y - (100.dp.toPx())
                                        scaleX = bubbleScale
                                        scaleY = bubbleScale
                                        alpha = bubbleAlpha
                                        rotationZ = if (isDraggingSelection) 4f else 0f
                                    }
                                    .widthIn(max = 200.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 32.dp
                            ) {
                                Text(
                                    text = ghostText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = textStyle.copy(
                                        fontSize = 14.sp, 
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

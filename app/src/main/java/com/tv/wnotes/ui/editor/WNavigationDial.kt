package com.tv.wnotes.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Title
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tv.wnotes.data.local.entity.Note
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DialButtonSize = 56.dp
private val DialSize = 248.dp
private val DialItemRadius = 86.dp
private val DialItemSize = 64.dp
private val SwipeThreshold = 48.dp

private enum class DialPage { Actions, Recents, Search }

private data class DialAction(
    val label: String,
    val icon: ImageVector,
    val angleDegrees: Float,
    val closesDial: Boolean = false,
    val onClick: () -> Unit
)

/**
 * A draggable floating button that opens into a dial of editor shortcuts.
 * Swiping horizontally inside the dial flips between the shortcuts and the recently edited notes.
 * The Search shortcut swaps the dial for a find-in-note box; its back arrow returns to the shortcuts.
 *
 * Must be placed in a Box that fills the editor area; the button can be dragged anywhere inside it.
 */
@Composable
fun WNavigationDial(
    recentNotes: List<Note>,
    onChangeTitle: () -> Unit,
    onPreviousParagraph: () -> Unit,
    onNextParagraph: () -> Unit,
    onPreviousWord: () -> Unit,
    onNextWord: () -> Unit,
    onOpenNote: (Long) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResultLabel: String?,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var buttonX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var buttonY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(DialPage.Actions) }
    // +1 slides content in from the right, -1 from the left.
    var slideDirection by remember { mutableIntStateOf(1) }

    // Let the editor know when the search box is showing, so it can highlight matches or clear them.
    val isSearching = isOpen && page == DialPage.Search
    LaunchedEffect(isSearching) { onSearchActiveChange(isSearching) }

    val actions = remember(onChangeTitle, onPreviousParagraph, onNextParagraph, onPreviousWord, onNextWord) {
        // Angles are clockwise from 3 o'clock: "previous" sits on the left, "next" on the right.
        listOf(
            DialAction("Title", Icons.Filled.Title, -90f, closesDial = true, onClick = onChangeTitle),
            DialAction("Next para", Icons.Filled.KeyboardDoubleArrowDown, -30f, onClick = onNextParagraph),
            DialAction("Next word", Icons.Filled.ChevronRight, 30f, onClick = onNextWord),
            DialAction("Search", Icons.Filled.Search, 90f, onClick = {
                slideDirection = 1
                page = DialPage.Search
            }),
            DialAction("Prev word", Icons.Filled.ChevronLeft, 150f, onClick = onPreviousWord),
            DialAction("Prev para", Icons.Filled.KeyboardDoubleArrowUp, 210f, onClick = onPreviousParagraph),
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val buttonPx = with(density) { DialButtonSize.toPx() }
        val dialPx = with(density) { DialSize.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }

        // Default spot: bottom-right, clear of the stapled-note bar.
        val defaultX = maxWidthPx - buttonPx - marginPx
        val defaultY = maxHeightPx - buttonPx - with(density) { 96.dp.toPx() }
        val maxButtonX = (maxWidthPx - buttonPx).coerceAtLeast(0f)
        val maxButtonY = (maxHeightPx - buttonPx).coerceAtLeast(0f)
        val clampedX = (if (buttonX.isNaN()) defaultX else buttonX).coerceIn(0f, maxButtonX)
        val clampedY = (if (buttonY.isNaN()) defaultY else buttonY).coerceIn(0f, maxButtonY)

        if (isOpen) {
            // Transparent catcher so tapping anywhere else closes the dial.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isOpen = false }
            )
        }

        // The dial opens centred on the button, nudged back inside the editor if needed.
        val dialX = (clampedX + buttonPx / 2 - dialPx / 2)
            .coerceIn(0f, (maxWidthPx - dialPx).coerceAtLeast(0f))
        val dialY = (clampedY + buttonPx / 2 - dialPx / 2)
            .coerceIn(0f, (maxHeightPx - dialPx).coerceAtLeast(0f))

        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.4f, animationSpec = tween(220)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.4f, animationSpec = tween(160)),
            modifier = Modifier.offset { IntOffset(dialX.roundToInt(), dialY.roundToInt()) }
        ) {
            val swipeThresholdPx = with(density) { SwipeThreshold.toPx() }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .size(DialSize)
                    .pointerInput(page == DialPage.Search) {
                        // Swiping only flips between shortcuts and recent notes, not while searching.
                        if (page == DialPage.Search) return@pointerInput
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragTotal += amount
                            },
                            onDragEnd = {
                                if (abs(dragTotal) > swipeThresholdPx) {
                                    slideDirection = if (dragTotal < 0) 1 else -1
                                    page = if (page == DialPage.Recents) DialPage.Actions else DialPage.Recents
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val dir = slideDirection
                        (slideInHorizontally(tween(220)) { it * dir } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -it * dir } + fadeOut(tween(160)))
                    },
                    label = "DialPage"
                ) { target ->
                    when (target) {
                        DialPage.Recents -> RecentNotesPage(
                            notes = recentNotes,
                            onOpenNote = { id ->
                                isOpen = false
                                onOpenNote(id)
                            }
                        )
                        DialPage.Search -> SearchPage(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            resultLabel = searchResultLabel,
                            onPrevious = onSearchPrevious,
                            onNext = onSearchNext,
                            onBack = {
                                slideDirection = -1
                                page = DialPage.Actions
                            }
                        )
                        DialPage.Actions -> ActionsPage(
                            actions = actions,
                            onAction = { action ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (action.closesDial) isOpen = false
                                action.onClick()
                            },
                            onClose = { isOpen = false }
                        )
                    }
                }
            }
        }

        if (!isOpen) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                    .size(DialButtonSize)
                    .pointerInput(maxWidthPx, maxHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (buttonX.isNaN() || buttonY.isNaN()) {
                                buttonX = defaultX
                                buttonY = defaultY
                            }
                            buttonX = (buttonX + dragAmount.x).coerceIn(0f, maxButtonX)
                            buttonY = (buttonY + dragAmount.y).coerceIn(0f, maxButtonY)
                        }
                    }
                    .clip(CircleShape)
                    .clickable {
                        page = DialPage.Actions
                        isOpen = true
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Adjust, contentDescription = "Open navigation dial")
                }
            }
        }
    }
}

@Composable
private fun ActionsPage(
    actions: List<DialAction>,
    onAction: (DialAction) -> Unit,
    onClose: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        actions.forEach { action ->
            val radians = Math.toRadians(action.angleDegrees.toDouble())
            val dx = DialItemRadius * cos(radians).toFloat()
            val dy = DialItemRadius * sin(radians).toFloat()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .offset(x = dx, y = dy)
                    .size(DialItemSize)
                    .clip(CircleShape)
                    .clickable { onAction(action) }
            ) {
                Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(22.dp))
                Text(
                    text = action.label,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Close dial", modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            PageDots(selected = 0)
        }
    }
}

@Composable
private fun RecentNotesPage(
    notes: List<Note>,
    onOpenNote: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Recently edited",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (notes.isEmpty()) {
            Text(
                text = "No other notes yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            notes.forEach { note ->
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onOpenNote(note.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PageDots(selected = 1)
    }
}

@Composable
private fun SearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    resultLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to dial", modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Find words",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val hasMatches = resultLabel != null && resultLabel != "No matches"
            IconButton(onClick = onPrevious, enabled = hasMatches, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(20.dp))
            }
            Text(
                text = resultLabel ?: " ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
            IconButton(onClick = onNext, enabled = hasMatches, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PageDots(selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

package com.tv.wnotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class WScrollbarOrientation {
    VERTICAL, HORIZONTAL
}

/**
 * A high-performance, low-latency draggable scrollbar for WNotes.
 */
@Composable
fun WScrollbar(
    scrollState: ScrollState,
    orientation: WScrollbarOrientation = WScrollbarOrientation.VERTICAL,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    BoxWithConstraints(modifier = if (orientation == WScrollbarOrientation.VERTICAL) modifier.fillMaxHeight() else modifier.fillMaxWidth()) {
        val totalSize = if (orientation == WScrollbarOrientation.VERTICAL) constraints.maxHeight.toFloat() else constraints.maxWidth.toFloat()
        if (totalSize <= 0f) return@BoxWithConstraints

        val viewportSize = totalSize
        val contentSize = viewportSize + scrollState.maxValue
        if (contentSize <= viewportSize) return@BoxWithConstraints

        val thumbSizePx = (viewportSize / contentSize * totalSize).coerceAtLeast(with(density) { 40.dp.toPx() })
        val thumbOffsetPx = (scrollState.value.toFloat() / scrollState.maxValue.coerceAtLeast(1) * (totalSize - thumbSizePx))

        ScrollbarTrack(
            orientation = orientation,
            thumbSize = with(density) { thumbSizePx.toDp() },
            thumbOffset = { with(density) { thumbOffsetPx.toDp() } },
            onRawDelta = { delta ->
                // Map track delta to content delta
                val trackToContentRatio = scrollState.maxValue.toFloat() / (totalSize - thumbSizePx).coerceAtLeast(1f)
                scrollState.dispatchRawDelta(delta * trackToContentRatio)
            }
        )
    }
}

@Composable
fun WScrollbar(
    gridState: LazyGridState,
    orientation: WScrollbarOrientation = WScrollbarOrientation.VERTICAL,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    BoxWithConstraints(modifier = if (orientation == WScrollbarOrientation.VERTICAL) modifier.fillMaxHeight() else modifier.fillMaxWidth()) {
        val totalSize = if (orientation == WScrollbarOrientation.VERTICAL) constraints.maxHeight.toFloat() else constraints.maxWidth.toFloat()
        val layoutInfo = gridState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        
        if (totalItems <= 0 || totalSize <= 0f) return@BoxWithConstraints

        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@BoxWithConstraints

        val viewportSize = if (orientation == WScrollbarOrientation.VERTICAL) layoutInfo.viewportSize.height.toFloat() else layoutInfo.viewportSize.width.toFloat()
        val columns = if (orientation == WScrollbarOrientation.VERTICAL) visibleColumns(visibleItems) else 1
        val rows = if (orientation == WScrollbarOrientation.HORIZONTAL) visibleRows(visibleItems) else 1
        
        val totalVisibleSize = visibleItems.sumOf { (if (orientation == WScrollbarOrientation.VERTICAL) it.size.height else it.size.width).toLong() }.toFloat()
        val avgItemSize = totalVisibleSize / visibleItems.size
        val estimatedTotalSize = if (orientation == WScrollbarOrientation.VERTICAL) {
            (totalItems.toFloat() / columns) * avgItemSize
        } else {
            (totalItems.toFloat() / rows) * avgItemSize
        }
        
        if (estimatedTotalSize <= viewportSize) return@BoxWithConstraints

        val thumbSizePx = (viewportSize / estimatedTotalSize * totalSize).coerceAtLeast(with(density) { 40.dp.toPx() })
        val firstVisibleIndex = gridState.firstVisibleItemIndex
        val scrollProgress = (firstVisibleIndex.toFloat() / (if (orientation == WScrollbarOrientation.VERTICAL) columns else rows)) / 
                             (totalItems.toFloat() / (if (orientation == WScrollbarOrientation.VERTICAL) columns else rows)).coerceAtLeast(1f)
        val thumbOffsetPx = scrollProgress * (totalSize - thumbSizePx)

        ScrollbarTrack(
            orientation = orientation,
            thumbSize = with(density) { thumbSizePx.toDp() },
            thumbOffset = { with(density) { thumbOffsetPx.toDp() } },
            onRawDelta = { delta ->
                val trackToContentRatio = (estimatedTotalSize - viewportSize) / (totalSize - thumbSizePx).coerceAtLeast(1f)
                gridState.dispatchRawDelta(delta * trackToContentRatio)
            }
        )
    }
}

@Composable
fun WScrollbar(
    listState: LazyListState,
    orientation: WScrollbarOrientation = WScrollbarOrientation.VERTICAL,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    BoxWithConstraints(modifier = if (orientation == WScrollbarOrientation.VERTICAL) modifier.fillMaxHeight() else modifier.fillMaxWidth()) {
        val totalSize = if (orientation == WScrollbarOrientation.VERTICAL) constraints.maxHeight.toFloat() else constraints.maxWidth.toFloat()
        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        
        if (totalItems <= 0 || totalSize <= 0f) return@BoxWithConstraints

        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@BoxWithConstraints

        val viewportSize = if (orientation == WScrollbarOrientation.VERTICAL) layoutInfo.viewportSize.height.toFloat() else layoutInfo.viewportSize.width.toFloat()
        val totalVisibleSize = visibleItems.sumOf { (if (orientation == WScrollbarOrientation.VERTICAL) it.size else it.size).toLong() }.toFloat()
        val avgItemSize = totalVisibleSize / visibleItems.size
        val estimatedTotalSize = avgItemSize * totalItems
        
        if (estimatedTotalSize <= viewportSize) return@BoxWithConstraints

        val thumbSizePx = (viewportSize / estimatedTotalSize * totalSize).coerceAtLeast(with(density) { 40.dp.toPx() })
        val firstVisibleIndex = listState.firstVisibleItemIndex
        val scrollProgress = firstVisibleIndex.toFloat() / totalItems.coerceAtLeast(1)
        val thumbOffsetPx = scrollProgress * (totalSize - thumbSizePx)

        ScrollbarTrack(
            orientation = orientation,
            thumbSize = with(density) { thumbSizePx.toDp() },
            thumbOffset = { with(density) { thumbOffsetPx.toDp() } },
            onRawDelta = { delta ->
                val trackToContentRatio = (estimatedTotalSize - viewportSize) / (totalSize - thumbSizePx).coerceAtLeast(1f)
                listState.dispatchRawDelta(delta * trackToContentRatio)
            }
        )
    }
}

private fun visibleColumns(items: List<androidx.compose.foundation.lazy.grid.LazyGridItemInfo>): Int {
    if (items.isEmpty()) return 1
    val firstY = items[0].offset.y
    return items.count { it.offset.y == firstY }.coerceAtLeast(1)
}

private fun visibleRows(items: List<androidx.compose.foundation.lazy.grid.LazyGridItemInfo>): Int {
    if (items.isEmpty()) return 1
    val firstX = items[0].offset.x
    return items.count { it.offset.x == firstX }.coerceAtLeast(1)
}

@Composable
private fun ScrollbarTrack(
    orientation: WScrollbarOrientation,
    thumbSize: androidx.compose.ui.unit.Dp,
    thumbOffset: () -> androidx.compose.ui.unit.Dp,
    onRawDelta: (Float) -> Unit,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
) {
    val thickness = 32.dp // Large hit target for track
    val thumbThickness = 6.dp

    Box(
        modifier = if (orientation == WScrollbarOrientation.VERTICAL) {
            Modifier.fillMaxHeight().width(thickness)
        } else {
            Modifier.fillMaxWidth().height(thickness)
        }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    
                    when (event.type) {
                        PointerEventType.Scroll -> {
                            val change = event.changes.first()
                            val scrollDelta = if (orientation == WScrollbarOrientation.VERTICAL) {
                                change.scrollDelta.y
                            } else {
                                change.scrollDelta.x
                            }
                            if (scrollDelta != 0f) {
                                onRawDelta(scrollDelta * 64f)
                            }
                        }
                        PointerEventType.Press -> { }
                        PointerEventType.Move -> {
                            if (event.changes.any { it.pressed }) {
                                val change = event.changes.first()
                                val delta = if (orientation == WScrollbarOrientation.VERTICAL) {
                                    change.position.y - change.previousPosition.y
                                } else {
                                    change.position.x - change.previousPosition.x
                                }
                                
                                if (delta != 0f) {
                                    onRawDelta(delta)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .align(if (orientation == WScrollbarOrientation.VERTICAL) Alignment.CenterEnd else Alignment.BottomCenter)
                .padding(if (orientation == WScrollbarOrientation.VERTICAL) PaddingValues(end = 4.dp) else PaddingValues(bottom = 4.dp))
                .then(
                    if (orientation == WScrollbarOrientation.VERTICAL) {
                        Modifier
                            .width(thumbThickness)
                            .height(thumbSize)
                            .graphicsLayer { translationY = thumbOffset().toPx() }
                    } else {
                        Modifier
                            .height(thumbThickness)
                            .width(thumbSize)
                            .graphicsLayer { translationX = thumbOffset().toPx() }
                    }
                )
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tv.wnotes.ui.editor.engine.logic.WDragInternalState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Coordinates all drag-and-drop related animations.
 * Decoupled from the document model and direct drag logic.
 */
@Stable
class WDragAnimationController(
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    // Lift animations
    val scale = Animatable(1f)
    val elevation = Animatable(0f)
    val rotation = Animatable(0f)
    val opacity = Animatable(1f)
    val placeholderAlpha = Animatable(1f)

    // Position follow (smooth interpolation)
    val dragOffset = Animatable(Offset.Zero, Offset.VectorConverter)
    
    // Insertion indicator animations
    val indicatorAlpha = Animatable(0f)
    val indicatorRect = Animatable(Rect.Zero, Rect.VectorConverter)

    fun onDragStateChanged(newState: WDragInternalState) {
        when (newState) {
            WDragInternalState.Lifting -> {
                scope.launch {
                    launch { scale.animateTo(1.08f, spring(stiffness = Spring.StiffnessLow)) }
                    launch { elevation.animateTo(16f, tween(200)) }
                    launch { opacity.animateTo(0.96f, tween(200)) }
                    launch { rotation.animateTo(1f, spring()) }
                    launch { placeholderAlpha.animateTo(0.3f, tween(180)) }
                }
            }
            WDragInternalState.Dragging -> {
                scope.launch { indicatorAlpha.animateTo(1f, tween(100)) }
            }
            WDragInternalState.Cancelled, WDragInternalState.Idle -> {
                resetAnimations()
            }
            else -> {}
        }
    }

    fun updateDragPosition(targetOffset: Offset) {
        scope.launch {
            dragOffset.animateTo(targetOffset, spring(stiffness = Spring.StiffnessHigh))
        }
    }

    fun updateIndicator(targetRect: Rect?) {
        scope.launch {
            if (targetRect != null) {
                if (indicatorAlpha.value < 0.1f) {
                    indicatorRect.snapTo(targetRect)
                    indicatorAlpha.animateTo(1f, tween(100))
                } else {
                    indicatorRect.animateTo(targetRect, spring(stiffness = Spring.StiffnessMediumLow))
                }
            } else {
                indicatorAlpha.animateTo(0f, tween(100))
            }
        }
    }

    private fun resetAnimations() {
        scope.launch {
            launch { scale.animateTo(1f, tween(150)) }
            launch { elevation.animateTo(0f, tween(150)) }
            launch { rotation.animateTo(0f, tween(150)) }
            launch { opacity.animateTo(1f, tween(150)) }
            launch { placeholderAlpha.animateTo(1f, tween(150)) }
            launch { indicatorAlpha.animateTo(0f, tween(150)) }
        }
    }

    suspend fun animateDrop(targetOffset: Offset) {
        coroutineScope {
            launch { dragOffset.animateTo(targetOffset, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
            launch { scale.animateTo(1f, tween(200)) }
            launch { elevation.animateTo(0f, tween(200)) }
            launch { rotation.animateTo(0f, tween(200)) }
            launch { opacity.animateTo(1f, tween(200)) }
            launch { placeholderAlpha.animateTo(1f, tween(100)) }
            launch { indicatorAlpha.animateTo(0f, tween(100)) }
        }
    }
}

private val Rect.Companion.VectorConverter: TwoWayConverter<Rect, AnimationVector4D>
    get() = TwoWayConverter(
        convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
        convertFromVector = { Rect(it.v1, it.v2, it.v3, it.v4) }
    )

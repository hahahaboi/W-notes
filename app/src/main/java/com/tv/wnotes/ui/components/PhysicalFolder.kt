package com.tv.wnotes.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val FolderTabShape = GenericShape { size, _ ->
    val tabWidth = size.width * 0.4f
    val tabHeight = size.height * 0.15f
    val cornerRadius = 8f

    moveTo(0f, tabHeight)
    lineTo(0f, cornerRadius)
    quadraticTo(0f, 0f, cornerRadius, 0f)
    lineTo(tabWidth - cornerRadius, 0f)
    quadraticTo(tabWidth, 0f, tabWidth, cornerRadius)
    lineTo(tabWidth + 20f, tabHeight)
    lineTo(size.width, tabHeight)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun PhysicalFolder(
    modifier: Modifier = Modifier,
    isOpen: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val folderColor = Color(0xFFFFFFFF) // White
    val pageColors = listOf(
        Color(0xFFFFFFFF), // Pure White
        Color(0xFFFBFBFB), // Very light off-white
        Color(0xFFF7F7F7)  // Light off-white
    )
    val borderColor = Color(0xFFEEEEEE) // Subtle light gray border

    val transition = updateTransition(targetState = isOpen, label = "FolderOpen")

    val flapRotation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600, easing = FastOutSlowInEasing) },
        label = "FlapRotation"
    ) { if (it) -15f else 0f }

    val flapTranslationY by transition.animateDp(
        transitionSpec = { tween(durationMillis = 600, easing = FastOutSlowInEasing) },
        label = "FlapTranslationY"
    ) { if (it) 20.dp else 0.dp }

    val pageRevealOffset by transition.animateDp(
        transitionSpec = { tween(durationMillis = 600, easing = FastOutSlowInEasing) },
        label = "PageRevealOffset"
    ) { if (it) (-16).dp else 0.dp }

    val pageSpread by transition.animateDp(
        transitionSpec = { tween(durationMillis = 600, easing = FastOutSlowInEasing) },
        label = "PageSpread"
    ) { if (it) 6.dp else 4.dp }

    Box(modifier = modifier.padding(top = 12.dp)) {
        // Stacked Pages
        pageColors.forEachIndexed { index, color ->
            val baseOffset = (index + 1)
            val currentSpread = pageSpread * baseOffset.toFloat()
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .align(Alignment.TopCenter)
                    .offset(
                        x = (currentSpread - 12.dp).coerceAtLeast((-12).dp), 
                        y = (-currentSpread) + pageRevealOffset
                    )
                    .zIndex(index.toFloat())
                    .border(BorderStroke(0.5.dp, borderColor), RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                color = color,
                shadowElevation = 1.dp
            ) {}
        }

        // Front Folder Flap with Tab
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .graphicsLayer {
                    rotationX = flapRotation
                    translationY = flapTranslationY.toPx()
                    cameraDistance = 8f * density
                }
                .shadow(4.dp, FolderTabShape)
                .border(BorderStroke(0.5.dp, borderColor), FolderTabShape)
                .clip(FolderTabShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            folderColor,
                            Color(0xFFF9F9F9) // Subtle tonal variation at bottom
                        )
                    )
                )
        ) {
            // Subtle highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp) // Below the tab edge
                    .background(Color.White.copy(alpha = 0.5f))
            )

            content()
        }
    }
}

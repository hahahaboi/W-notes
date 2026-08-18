package com.tv.wnotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

@Composable
fun HorizontalStapleConnector(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
) {
    Canvas(modifier = modifier.width(48.dp).fillMaxHeight()) {
        val midY = size.height / 2
        val startX = 0f
        val endX = size.width
        
        // Draw dotted line
        drawLine(
            color = color,
            start = Offset(startX, midY),
            end = Offset(endX, midY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        
        // Draw glowing dots at ends
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = Offset(startX, midY)
        )
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = 12.dp.toPx(),
            center = Offset(startX, midY)
        )
        
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = Offset(endX, midY)
        )
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = 12.dp.toPx(),
            center = Offset(endX, midY)
        )
    }
}

@Composable
fun VerticalStapleRail(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
) {
    Canvas(modifier = modifier.width(16.dp).fillMaxHeight()) {
        val midX = size.width / 2
        
        drawLine(
            color = color,
            start = Offset(midX, 0f),
            end = Offset(midX, size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }
}

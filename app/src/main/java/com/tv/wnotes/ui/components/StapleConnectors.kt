package com.tv.wnotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HorizontalStapleConnector(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
) {
    Box(
        modifier = modifier
            .width(36.dp)
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 14.dp)) {
            val strokeWidth = 2.dp.toPx()
            val arrowHeadSize = 4.5f.dp.toPx()
            
            val topY = size.height * 0.28f
            val bottomY = size.height * 0.72f
            val startX = 0f
            val endX = size.width
            
            // Top arrow pointing RIGHT (→)
            drawLine(
                color = color,
                start = Offset(startX, topY),
                end = Offset(endX, topY),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(endX - arrowHeadSize, topY - arrowHeadSize),
                end = Offset(endX, topY),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(endX - arrowHeadSize, topY + arrowHeadSize),
                end = Offset(endX, topY),
                strokeWidth = strokeWidth
            )
            
            // Bottom arrow pointing LEFT (←)
            drawLine(
                color = color,
                start = Offset(endX, bottomY),
                end = Offset(startX, bottomY),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(startX + arrowHeadSize, bottomY - arrowHeadSize),
                end = Offset(startX, bottomY),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(startX + arrowHeadSize, bottomY + arrowHeadSize),
                end = Offset(startX, bottomY),
                strokeWidth = strokeWidth
            )
        }
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
            strokeWidth = 2.dp.toPx()
        )
    }
}

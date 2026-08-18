package com.tv.wnotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

@Composable
fun WAnimatedIcon(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    strokeWidth: Float? = null
) {
    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height

        // Define normalized points for Two Slanted Lines (\\) at progress = 0
        // Line 1 (Seg 1 & 2)
        val cP0 = Offset(0.35f, 0.35f)
        val cP1 = Offset(0.4f, 0.5f)
        val cP2 = Offset(0.4f, 0.5f)
        val cP3 = Offset(0.45f, 0.65f)
        // Line 2 (Seg 3 & 4)
        val cP4 = Offset(0.55f, 0.35f)
        val cP5 = Offset(0.6f, 0.5f)
        val cP6 = Offset(0.6f, 0.5f)
        val cP7 = Offset(0.65f, 0.65f)

        // Define normalized points for Expanded (W) at progress = 1
        // Seg 1: Top-Left to Bottom-Left
        val eP0 = Offset(0.15f, 0.25f)
        val eP1 = Offset(0.35f, 0.8f)
        // Seg 2: Bottom-Left to Middle-Top
        val eP2 = Offset(0.35f, 0.8f)
        val eP3 = Offset(0.5f, 0.45f)
        // Seg 3: Middle-Top to Bottom-Right
        val eP4 = Offset(0.5f, 0.45f)
        val eP5 = Offset(0.65f, 0.8f)
        // Seg 4: Bottom-Right to Top-Right
        val eP6 = Offset(0.65f, 0.8f)
        val eP7 = Offset(0.85f, 0.25f)

        // Interpolated points
        val p0 = Offset(lerp(cP0.x, eP0.x, progress) * width, lerp(cP0.y, eP0.y, progress) * height)
        val p1 = Offset(lerp(cP1.x, eP1.x, progress) * width, lerp(cP1.y, eP1.y, progress) * height)
        val p2 = Offset(lerp(cP2.x, eP2.x, progress) * width, lerp(cP2.y, eP2.y, progress) * height)
        val p3 = Offset(lerp(cP3.x, eP3.x, progress) * width, lerp(cP3.y, eP3.y, progress) * height)
        val p4 = Offset(lerp(cP4.x, eP4.x, progress) * width, lerp(cP4.y, eP4.y, progress) * height)
        val p5 = Offset(lerp(cP5.x, eP5.x, progress) * width, lerp(cP5.y, eP5.y, progress) * height)
        val p6 = Offset(lerp(cP6.x, eP6.x, progress) * width, lerp(cP6.y, eP6.y, progress) * height)
        val p7 = Offset(lerp(cP7.x, eP7.x, progress) * width, lerp(cP7.y, eP7.y, progress) * height)

        val finalStrokeWidth = strokeWidth ?: 2.5.dp.toPx()

        // Draw the 4 segments
        drawLine(color, p0, p1, finalStrokeWidth, StrokeCap.Round)
        drawLine(color, p2, p3, finalStrokeWidth, StrokeCap.Round)
        drawLine(color, p4, p5, finalStrokeWidth, StrokeCap.Round)
        drawLine(color, p6, p7, finalStrokeWidth, StrokeCap.Round)
    }
}

@Preview(showBackground = true)
@Composable
fun WAnimatedIconPreview() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.Text("Two Slanted Lines (0.0)")
        WAnimatedIcon(progress = 0f, color = Color.Black, modifier = Modifier.size(24.dp))
        
        androidx.compose.material3.Text("Halfway (0.5)")
        WAnimatedIcon(progress = 0.5f, color = Color.Black, modifier = Modifier.size(24.dp))
        
        androidx.compose.material3.Text("Expanded (1.0)")
        WAnimatedIcon(progress = 1f, color = Color.Black, modifier = Modifier.size(24.dp))
    }
}

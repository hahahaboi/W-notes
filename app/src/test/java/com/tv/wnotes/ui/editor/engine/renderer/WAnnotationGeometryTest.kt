package com.tv.wnotes.ui.editor.engine.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class WAnnotationGeometryTest {

    @Test
    fun testRectCombine() {
        val r1 = Rect(10f, 10f, 20f, 20f)
        val r2 = Rect(15f, 15f, 30f, 30f)
        
        val combined = combine(r1, r2)
        assertEquals(10f, combined.left)
        assertEquals(10f, combined.top)
        assertEquals(30f, combined.right)
        assertEquals(30f, combined.bottom)
    }

    @Test
    fun testRectCombineDisjoint() {
        val r1 = Rect(0f, 0f, 10f, 10f)
        val r2 = Rect(20f, 20f, 30f, 30f)
        
        val combined = combine(r1, r2)
        assertEquals(0f, combined.left)
        assertEquals(0f, combined.top)
        assertEquals(30f, combined.right)
        assertEquals(30f, combined.bottom)
    }

    private fun combine(r1: Rect, r2: Rect): Rect {
        return Rect(
            left = minOf(r1.left, r2.left),
            top = minOf(r1.top, r2.top),
            right = maxOf(r1.right, r2.right),
            bottom = maxOf(r1.bottom, r2.bottom)
        )
    }
}

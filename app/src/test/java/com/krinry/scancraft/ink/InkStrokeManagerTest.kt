package com.krinry.scancraft.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for custom InkStrokeManager coordinate flattening and vector smoothing.
 */
class InkStrokeManagerTest {

    @Test
    fun testInkFlatPointsConversion() {
        val points = listOf(
            Offset(10f, 20f),
            Offset(30f, 40f),
            Offset(50f, 60f)
        )
        val flat = InkStrokeManager.toFlatPoints(points)
        assertEquals(6, flat.size)
        assertEquals(10f, flat[0], 0.001f)
        assertEquals(20f, flat[1], 0.001f)
        assertEquals(30f, flat[2], 0.001f)
        assertEquals(40f, flat[3], 0.001f)
        assertEquals(50f, flat[4], 0.001f)
        assertEquals(60f, flat[5], 0.001f)
    }

    @Test
    fun testEmptyPointsReturnsEmptyArray() {
        val flat = InkStrokeManager.toFlatPoints(emptyList())
        assertEquals(0, flat.size)
    }

    @Test
    fun testStrokeCreationAndSmoothing() {
        val brush = InkStrokeManager.createPenBrush(4f, 0xFF000000.toInt())
        val points = listOf(
            Offset(0f, 0f),
            Offset(50f, 100f),
            Offset(100f, 0f)
        )
        val stroke = InkStrokeManager.createStroke(points, brush)
        assertNotNull(stroke)
        assertEquals(3, stroke!!.points.size)
        assertTrue(stroke.smoothedPoints.size > stroke.points.size)

        // Single point should not create a stroke
        val singlePointStroke = InkStrokeManager.createStroke(listOf(Offset(10f, 10f)), brush)
        assertNull(singlePointStroke)
    }
}

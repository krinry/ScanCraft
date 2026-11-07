package com.krinry.scancraft.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for InkStrokeManager coordinate flattening and vector serialization.
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
}

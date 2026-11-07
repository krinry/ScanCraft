package com.krinry.scancraft.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 & Tier 2: Tests for DocumentQuadrilateral geometry and conversions.
 */
class DocumentQuadrilateralTest {

    @Test
    fun testPointFDistanceTo() {
        val p1 = PointF(0f, 0f)
        val p2 = PointF(3f, 4f)
        assertEquals(5f, p1.distanceTo(p2), 0.001f)
        assertEquals(5f, p2.distanceTo(p1), 0.001f)

        val p3 = PointF(10f, 20f)
        assertEquals(0f, p3.distanceTo(p3), 0.001f)
    }

    @Test
    fun testFloatArrayRoundTrip() {
        val original = DocumentQuadrilateral(
            topLeft = PointF(12.5f, 25.0f),
            topRight = PointF(450.0f, 30.5f),
            bottomRight = PointF(440.0f, 800.0f),
            bottomLeft = PointF(15.0f, 790.0f)
        )

        val arr = original.toFloatArray()
        assertEquals(8, arr.size)
        assertEquals(12.5f, arr[0], 0.001f)
        assertEquals(25.0f, arr[1], 0.001f)
        assertEquals(450.0f, arr[2], 0.001f)
        assertEquals(30.5f, arr[3], 0.001f)
        assertEquals(440.0f, arr[4], 0.001f)
        assertEquals(800.0f, arr[5], 0.001f)
        assertEquals(15.0f, arr[6], 0.001f)
        assertEquals(790.0f, arr[7], 0.001f)

        val reconstructed = DocumentQuadrilateral.fromFloatArray(arr)
        assertEquals(original.topLeft, reconstructed.topLeft)
        assertEquals(original.topRight, reconstructed.topRight)
        assertEquals(original.bottomRight, reconstructed.bottomRight)
        assertEquals(original.bottomLeft, reconstructed.bottomLeft)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromFloatArrayWithInvalidSizeThrows() {
        val shortArray = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        DocumentQuadrilateral.fromFloatArray(shortArray)
    }

    @Test
    fun testDefaultForDimensionsDefaultMargin() {
        val width = 1000f
        val height = 2000f
        val quad = DocumentQuadrilateral.defaultForDimensions(width, height)

        // default marginFraction = 0.08f (80px x, 160px y)
        assertEquals(80f, quad.topLeft.x, 0.001f)
        assertEquals(160f, quad.topLeft.y, 0.001f)

        assertEquals(920f, quad.topRight.x, 0.001f)
        assertEquals(160f, quad.topRight.y, 0.001f)

        assertEquals(920f, quad.bottomRight.x, 0.001f)
        assertEquals(1840f, quad.bottomRight.y, 0.001f)

        assertEquals(80f, quad.bottomLeft.x, 0.001f)
        assertEquals(1840f, quad.bottomLeft.y, 0.001f)
    }

    @Test
    fun testDefaultForDimensionsCustomMargin() {
        val width = 500f
        val height = 800f
        val quad = DocumentQuadrilateral.defaultForDimensions(width, height, marginFraction = 0.10f)

        // marginFraction = 0.10f (50px x, 80px y)
        assertEquals(50f, quad.topLeft.x, 0.001f)
        assertEquals(80f, quad.topLeft.y, 0.001f)

        assertEquals(450f, quad.topRight.x, 0.001f)
        assertEquals(80f, quad.topRight.y, 0.001f)

        assertEquals(450f, quad.bottomRight.x, 0.001f)
        assertEquals(720f, quad.bottomRight.y, 0.001f)

        assertEquals(50f, quad.bottomLeft.x, 0.001f)
        assertEquals(720f, quad.bottomLeft.y, 0.001f)
    }

    @Test
    fun testAreaCalculation() {
        // Standard rectangle of 100 x 200 = 20,000 area
        val rect = DocumentQuadrilateral(
            topLeft = PointF(0f, 0f),
            topRight = PointF(100f, 0f),
            bottomRight = PointF(100f, 200f),
            bottomLeft = PointF(0f, 200f)
        )
        assertEquals(20000f, rect.area(), 0.1f)
    }

    @Test
    fun testIsValidConvexValidPolygon() {
        val validQuad = DocumentQuadrilateral(
            topLeft = PointF(10f, 15f),
            topRight = PointF(400f, 20f),
            bottomRight = PointF(420f, 600f),
            bottomLeft = PointF(25f, 580f)
        )
        assertTrue(validQuad.isValidConvex())
    }

    @Test
    fun testIsValidConvexSelfIntersectingBowtie() {
        // Crossing lines: Top-Left to Bottom-Right and Top-Right to Bottom-Left
        val bowtie = DocumentQuadrilateral(
            topLeft = PointF(0f, 0f),
            topRight = PointF(100f, 100f),
            bottomRight = PointF(0f, 100f),
            bottomLeft = PointF(100f, 0f)
        )
        org.junit.Assert.assertFalse(bowtie.isValidConvex())
    }

    @Test
    fun testIsValidConvexConcave() {
        // Concave quad where top-right is indented inwards beyond the diagonal
        val concave = DocumentQuadrilateral(
            topLeft = PointF(0f, 0f),
            topRight = PointF(40f, 60f),
            bottomRight = PointF(100f, 100f),
            bottomLeft = PointF(0f, 100f)
        )
        org.junit.Assert.assertFalse(concave.isValidConvex())
    }
}

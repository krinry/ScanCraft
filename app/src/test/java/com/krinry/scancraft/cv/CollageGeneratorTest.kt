package com.krinry.scancraft.cv

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test

/**
 * Production unit tests for CollageLayout and CollageGenerator:
 * 1. Collage layout enum definitions and max page capacity
 * 2. Standard A4 geometry calculations and slot bounds
 * 3. Exact slot rects and gutter dimensions across all layouts
 * 4. Pairwise non-overlapping slot invariants and non-standard aspect ratio stress
 */
class CollageGeneratorTest {

    @Test
    fun testCollageLayoutEnumValues() {
        val layouts = CollageLayout.values()
        assertEquals(9, layouts.size)

        assertEquals("2-in-1 Vertical", CollageLayout.TWO_VERTICAL.title)
        assertEquals(2, CollageLayout.TWO_VERTICAL.maxPages)

        assertEquals("2-in-1 Side by Side", CollageLayout.TWO_HORIZONTAL.title)
        assertEquals(2, CollageLayout.TWO_HORIZONTAL.maxPages)

        assertEquals("3-in-1 Vertical", CollageLayout.THREE_VERTICAL.title)
        assertEquals(3, CollageLayout.THREE_VERTICAL.maxPages)

        assertEquals("3-in-1 (1 Top, 2 Bottom)", CollageLayout.THREE_TOP_TWO_BOTTOM.title)
        assertEquals(3, CollageLayout.THREE_TOP_TWO_BOTTOM.maxPages)

        assertEquals("4-in-1 Grid", CollageLayout.FOUR_GRID.title)
        assertEquals(4, CollageLayout.FOUR_GRID.maxPages)

        assertEquals("6-in-1 Grid", CollageLayout.SIX_GRID.title)
        assertEquals(6, CollageLayout.SIX_GRID.maxPages)

        assertEquals("Driver License", CollageLayout.DRIVER_LICENSE.title)
        assertEquals(2, CollageLayout.DRIVER_LICENSE.maxPages)

        assertEquals("Passport", CollageLayout.PASSPORT.title)
        assertEquals(2, CollageLayout.PASSPORT.maxPages)

        assertEquals("Question Book", CollageLayout.QUESTION_BOOK.title)
        assertEquals(2, CollageLayout.QUESTION_BOOK.maxPages)
    }

    @Test
    fun testStandardA4CollageGeometryCalculations() {
        val canvasWidth = 2480
        val canvasHeight = 3508

        val margin = (canvasWidth * 0.04f).toInt()
        val spacing = (canvasWidth * 0.02f).toInt()
        val contentW = canvasWidth - (margin * 2)
        val contentH = canvasHeight - (margin * 2)

        assertEquals(99, margin)
        assertEquals(49, spacing)
        assertEquals(2282, contentW)
        assertEquals(3310, contentH)
    }

    @Test
    fun testTwoVerticalSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.TWO_VERTICAL, 2480, 3508)
        assertEquals(2, slots.size)
        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(2381, slots[0].right)
        assertEquals(1729, slots[0].bottom)

        assertEquals(99, slots[1].left)
        assertEquals(1778, slots[1].top)
        assertEquals(2381, slots[1].right)
        assertEquals(3408, slots[1].bottom)

        assertEquals(49, slots[1].top - slots[0].bottom)
    }

    @Test
    fun testTwoHorizontalSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.TWO_HORIZONTAL, 2480, 3508)
        assertEquals(2, slots.size)
        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(1215, slots[0].right)
        assertEquals(3409, slots[0].bottom)

        assertEquals(1264, slots[1].left)
        assertEquals(99, slots[1].top)
        assertEquals(2380, slots[1].right)
        assertEquals(3409, slots[1].bottom)

        assertEquals(49, slots[1].left - slots[0].right)
    }

    @Test
    fun testThreeVerticalSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.THREE_VERTICAL, 2480, 3508)
        assertEquals(3, slots.size)
        assertEquals(49, slots[1].top - slots[0].bottom)
        assertEquals(49, slots[2].top - slots[1].bottom)
    }

    @Test
    fun testFourGridSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.FOUR_GRID, 2480, 3508)
        assertEquals(4, slots.size)
        assertEquals(49, slots[1].left - slots[0].right)
        assertEquals(49, slots[2].top - slots[0].bottom)
    }

    @Test
    fun testAllLayoutsProduceValidNonOverlappingSlots() {
        val canvasW = 2480
        val canvasH = 3508
        val margin = (canvasW * 0.04f).toInt()

        for (layout in CollageLayout.values()) {
            val slots = CollageGenerator.calculateSlotRects(layout, canvasW, canvasH)
            assertEquals("Layout $layout must produce maxPages slots", layout.maxPages, slots.size)

            for (idx in slots.indices) {
                val slot = slots[idx]
                assertTrue(slot.left >= margin)
                assertTrue(slot.top >= margin)
                assertTrue(slot.right <= canvasW - margin)
                assertTrue(slot.bottom <= canvasH - margin)
                assertTrue(slot.right > slot.left)
                assertTrue(slot.bottom > slot.top)
            }

            for (i in 0 until slots.size) {
                for (j in i + 1 until slots.size) {
                    val s1 = slots[i]
                    val s2 = slots[j]
                    val xOverlap = maxOf(s1.left, s2.left) < minOf(s1.right, s2.right)
                    val yOverlap = maxOf(s1.top, s2.top) < minOf(s1.bottom, s2.bottom)
                    assertFalse("Slots $i and $j in layout $layout must not overlap", xOverlap && yOverlap)
                }
            }
        }
    }

    @Test
    fun testNonStandardCanvasDimensionsStress() {
        val testDimensions = listOf(
            Pair(1000, 1000),
            Pair(1200, 1800),
            Pair(3000, 2000),
            Pair(500, 1000)
        )

        for ((w, h) in testDimensions) {
            val margin = (w * 0.04f).toInt()
            for (layout in CollageLayout.values()) {
                val slots = CollageGenerator.calculateSlotRects(layout, w, h)
                assertEquals(layout.maxPages, slots.size)
                for (s in slots) {
                    assertTrue(s.left >= margin)
                    assertTrue(s.top >= margin)
                    assertTrue(s.right <= w - margin)
                    assertTrue(s.bottom <= h - margin)
                }
            }
        }
    }
}

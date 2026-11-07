package com.krinry.scancraft.cv

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Production unit tests for IdCardCropper:
 * 1. ISO/IEC 7810 ID-1 standard aspect ratio math and viewport ratios
 * 2. CameraX FILL_CENTER screen-to-bitmap projection
 * 3. Boundary clamping, zero dimension fallbacks, and extreme aspect ratio stability
 * 4. Property-based invariant fuzzing
 */
class IdCardCropperTest {

    @Test
    fun testIdCardAspectRatioConstant() {
        assertEquals(1.586f, IdCardCropper.ID1_ASPECT_RATIO, 0.001f)
    }

    @Test
    fun testCardWidthRatioConstant() {
        assertEquals(0.85f, IdCardCropper.CARD_WIDTH_RATIO, 0.001f)
    }

    @Test
    fun testCalculateCropRectStandardScreen() {
        val bitmapWidth = 3000
        val bitmapHeight = 4000
        val viewWidth = 1080
        val viewHeight = 2400

        val rect = IdCardCropper.calculateCropRect(bitmapWidth, bitmapHeight, viewWidth, viewHeight)
        val rectW = rect.right - rect.left
        val rectH = rect.bottom - rect.top

        assertTrue("cropX >= 0", rect.left >= 0)
        assertTrue("cropY >= 0", rect.top >= 0)
        assertTrue("cropRight <= bitmapWidth", rect.right <= bitmapWidth)
        assertTrue("cropBottom <= bitmapHeight", rect.bottom <= bitmapHeight)

        assertEquals(735, rect.left)
        assertEquals(1517, rect.top)
        assertEquals(1530, rectW)
        assertEquals(964, rectH)

        val cropAspect = rectW.toFloat() / rectH.toFloat()
        assertTrue(abs(cropAspect - IdCardCropper.ID1_ASPECT_RATIO) < 0.01f)
    }

    @Test
    fun testCalculateCropRectFallbackZeroDimensions() {
        val rect = IdCardCropper.calculateCropRect(
            bitmapWidth = 1000,
            bitmapHeight = 800,
            viewWidth = 0,
            viewHeight = 0
        )
        val rectW = rect.right - rect.left
        val rectH = rect.bottom - rect.top

        assertTrue(rectW > 0)
        assertTrue(rectH > 0)
        assertEquals(850, rectW)
        assertEquals(536, rectH)
        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= 1000)
        assertTrue(rect.bottom <= 800)
    }

    @Test
    fun testExtremeScreenAspectRatiosMaintainBounds() {
        data class ScreenCase(val bW: Int, val bH: Int, val vW: Int, val vH: Int)

        val testConfigs = listOf(
            ScreenCase(3000, 4000, 1080, 2400),
            ScreenCase(4000, 3000, 2400, 1080),
            ScreenCase(4000, 4000, 1080, 1080),
            ScreenCase(1000, 4000, 3840, 1080),
            ScreenCase(4000, 1000, 1080, 3840),
            ScreenCase(100, 100, 2000, 2000),
            ScreenCase(8000, 6000, 200, 200)
        )

        for ((bW, bH, vW, vH) in testConfigs) {
            val rect = IdCardCropper.calculateCropRect(bW, bH, vW, vH)
            val rectW = rect.right - rect.left
            val rectH = rect.bottom - rect.top

            assertTrue("Left bound >= 0 for ($bW, $bH, $vW, $vH): ${rect.left}", rect.left >= 0)
            assertTrue("Top bound >= 0 for ($bW, $bH, $vW, $vH): ${rect.top}", rect.top >= 0)
            assertTrue("Right bound <= bitmapWidth for ($bW, $bH, $vW, $vH): ${rect.right} <= $bW", rect.right <= bW)
            assertTrue("Bottom bound <= bitmapHeight for ($bW, $bH, $vW, $vH): ${rect.bottom} <= $bH", rect.bottom <= bH)
            assertTrue("Width > 0 for ($bW, $bH, $vW, $vH): $rectW", rectW > 0)
            assertTrue("Height > 0 for ($bW, $bH, $vW, $vH): $rectH", rectH > 0)
        }
    }

    @Test
    fun testPropertyBasedFuzzingInvariants() {
        val random = Random(42)
        val iterations = 100

        for (i in 0 until iterations) {
            val bW = random.nextInt(10, 4000)
            val bH = random.nextInt(10, 4000)
            val vW = random.nextInt(50, 3000)
            val vH = random.nextInt(50, 3000)

            val rect = IdCardCropper.calculateCropRect(bW, bH, vW, vH)
            val rectW = rect.right - rect.left
            val rectH = rect.bottom - rect.top

            assertTrue("left >= 0 [iter $i]", rect.left >= 0)
            assertTrue("top >= 0 [iter $i]", rect.top >= 0)
            assertTrue("right <= bW [iter $i]", rect.right <= bW)
            assertTrue("bottom <= bH [iter $i]", rect.bottom <= bH)
            assertTrue("rectW > 0 [iter $i]", rectW > 0)
            assertTrue("rectH > 0 [iter $i]", rectH > 0)
        }
    }
}

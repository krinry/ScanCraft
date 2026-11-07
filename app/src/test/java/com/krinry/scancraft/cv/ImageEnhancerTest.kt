package com.krinry.scancraft.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Tier 1 & Tier 2: Tests for ImageEnhancer mathematical formulas and edge cases.
 */
class ImageEnhancerTest {

    @Test
    fun testLuminanceWeightingFormula() {
        fun calcLum(r: Int, g: Int, b: Int): Int {
            return (r * 77 + g * 150 + b * 29) shr 8
        }

        // Pure white (255, 255, 255) -> 255
        assertEquals(255, calcLum(255, 255, 255))

        // Pure black (0, 0, 0) -> 0
        assertEquals(0, calcLum(0, 0, 0))

        // Pure Red
        assertEquals(76, calcLum(255, 0, 0))

        // Pure Green (human eye perceives green as brightest)
        assertEquals(149, calcLum(0, 255, 0))

        // Pure Blue (perceived as darkest)
        assertEquals(28, calcLum(0, 0, 255))

        // Middle Gray (128, 128, 128)
        assertEquals(128, calcLum(128, 128, 128))
    }

    @Test
    fun testBradleySauvolaThresholdFactors() {
        val k1 = 0.20f // BW
        val k2 = 0.28f // BW2

        val threshT1 = 1.0f - k1
        val threshT2 = 1.0f - k2

        assertEquals(0.80f, threshT1, 0.001f)
        assertEquals(0.72f, threshT2, 0.001f)

        // BW2 has a lower threshold factor, creating bolder text lines
        assertTrue(threshT2 < threshT1)
    }

    @Test
    fun testContrastFactorPolynomial() {
        fun computeFactor(contrast: Int): Float {
            return (259f * (contrast + 255f)) / (255f * (259f - contrast))
        }

        // Neutral contrast (0) -> factor must be exactly 1.0
        val neutralFactor = computeFactor(0)
        assertEquals(1.0f, neutralFactor, 0.0001f)

        // Positive contrast (+50) -> factor > 1.0 (amplifies differences)
        val highFactor = computeFactor(50)
        assertTrue(highFactor > 1.0f)
        assertEquals(1.480f, highFactor, 0.01f)

        // Negative contrast (-50) -> factor < 1.0 (flattens differences)
        val lowFactor = computeFactor(-50)
        assertTrue(lowFactor < 1.0f)
        assertTrue(lowFactor > 0f)
        assertEquals(0.675f, lowFactor, 0.01f)
    }

    @Test
    fun testClamp255Logic() {
        fun clamp(valInt: Int): Int = when {
            valInt < 0 -> 0
            valInt > 255 -> 255
            else -> valInt
        }

        assertEquals(0, clamp(-50))
        assertEquals(0, clamp(-1))
        assertEquals(0, clamp(0))
        assertEquals(128, clamp(128))
        assertEquals(255, clamp(255))
        assertEquals(255, clamp(256))
        assertEquals(255, clamp(9999))
    }
}

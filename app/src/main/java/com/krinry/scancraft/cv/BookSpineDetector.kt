package com.krinry.scancraft.cv

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * High-performance, clean-room Book Spine Detection & 1D Cylindrical Unwarping Engine.
 *
 * Implements:
 * 1. Rapid vertical luminance valley profiling downsampled to 320x240.
 * 2. 1D Gaussian kernel smoothing to isolate the central binding crease shadow.
 * 3. 1D cylindrical curvature unwarping expanding compressed text lines near the spine:
 *    x_unwarped = X_spine +/- W * (d / W)^gamma (gamma ~= 0.85).
 * 4. Quadratic crease shadow compensation restoring page gutter brightness.
 */
object BookSpineDetector {

    const val SAMPLE_WIDTH = 320
    const val SAMPLE_HEIGHT = 240
    const val DEFAULT_GAMMA = 0.85f
    const val DEFAULT_SHADOW_BOOST = 1.35f
    const val DEFAULT_GUTTER_FRACTION = 0.22f

    val GAUSSIAN_KERNEL_11: FloatArray by lazy {
        val radius = 5
        val sigma = 2.5f
        val kernel = FloatArray(2 * radius + 1)
        var sum = 0f
        for (i in -radius..radius) {
            val w = exp(-(i * i).toFloat() / (2f * sigma * sigma))
            kernel[i + radius] = w
            sum += w
        }
        for (i in kernel.indices) {
            kernel[i] /= sum
        }
        kernel
    }

    /**
     * Detects the horizontal coordinate of the book spine in full-resolution image coordinates.
     */
    fun detectSpine(spreadBitmap: Bitmap): Int {
        val fullWidth = spreadBitmap.width
        val fullHeight = spreadBitmap.height
        if (fullWidth < 64 || fullHeight < 64) {
            return fullWidth / 2
        }

        val sampleBmp = Bitmap.createScaledBitmap(spreadBitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, true)
        val pixels = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
        sampleBmp.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        if (sampleBmp != spreadBitmap) {
            sampleBmp.recycle()
        }

        val profile = computeLuminanceProfile(pixels, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        val smoothed = smoothProfile(profile)
        val spineSampleX = findValleySpineIndex(smoothed)

        val fullSpineX = ((spineSampleX + 0.5f) / SAMPLE_WIDTH * fullWidth).toInt()
        val minX = (fullWidth * 0.25f).toInt().coerceAtLeast(1)
        val maxX = (fullWidth * 0.75f).toInt().coerceAtMost(fullWidth - 1)
        return fullSpineX.coerceIn(minX, maxX)
    }

    /**
     * Computes the vertical projection luminance profile P(x) = (1/H) * sum_y(0.299R + 0.587G + 0.114B).
     */
    fun computeLuminanceProfile(pixels: IntArray, width: Int, height: Int): FloatArray {
        val profile = FloatArray(width)
        val invH = 1.0f / height
        for (x in 0 until width) {
            var colLumSum = 0f
            for (y in 0 until height) {
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                colLumSum += lum
            }
            profile[x] = colLumSum * invH
        }
        return profile
    }

    /**
     * Convolves luminance profile with a 1D Gaussian smoothing filter.
     */
    fun smoothProfile(profile: FloatArray, kernel: FloatArray = GAUSSIAN_KERNEL_11): FloatArray {
        val radius = kernel.size / 2
        val smoothed = FloatArray(profile.size)
        for (x in profile.indices) {
            var acc = 0f
            var weightSum = 0f
            for (k in -radius..radius) {
                val sampleX = x + k
                if (sampleX in profile.indices) {
                    val w = kernel[k + radius]
                    acc += profile[sampleX] * w
                    weightSum += w
                }
            }
            smoothed[x] = if (weightSum > 0f) acc / weightSum else profile[x]
        }
        return smoothed
    }

    /**
     * Searches central band [0.35W, 0.65W] for the global crease shadow valley minimum.
     */
    fun findValleySpineIndex(
        smoothed: FloatArray,
        startFraction: Float = 0.35f,
        endFraction: Float = 0.65f
    ): Int {
        val width = smoothed.size
        val startX = (width * startFraction).toInt().coerceIn(0, width - 1)
        val endX = (width * endFraction).toInt().coerceIn(startX, width - 1)

        var minLum = Float.MAX_VALUE
        var bestX = width / 2
        var maxLum = -Float.MAX_VALUE

        for (x in startX..endX) {
            val v = smoothed[x]
            if (v < minLum) {
                minLum = v
                bestX = x
            }
            if (v > maxLum) {
                maxLum = v
            }
        }

        // Fallback to exact center if profile is flat without significant crease shadow
        if (maxLum - minLum < 2.5f) {
            return width / 2
        }

        return bestX
    }

    /**
     * Precomputes 1D Look-Up Tables (LUTs) for inverse unwarp coordinate mapping and shadow compensation.
     *
     * @param isLeftPage true if Left Page (spine on right), false if Right Page (spine on left).
     * @return Pair of (srcXTable: FloatArray, shadowGainTable: FloatArray) of size width.
     */
    fun computeUnwarpLUT(
        width: Int,
        isLeftPage: Boolean,
        gamma: Float = DEFAULT_GAMMA,
        shadowBoost: Float = DEFAULT_SHADOW_BOOST,
        gutterFraction: Float = DEFAULT_GUTTER_FRACTION
    ): Pair<FloatArray, FloatArray> {
        val srcXTable = FloatArray(width)
        val shadowGainTable = FloatArray(width)
        val beta = 1.0f / gamma // ~1.17647 for gamma=0.85
        val maxD = if (width > 1) (width - 1).toFloat() else 1f
        val gutterWidth = (maxD * gutterFraction).coerceAtLeast(1f)

        for (xDst in 0 until width) {
            val dDst = if (isLeftPage) (width - 1 - xDst).toFloat() else xDst.toFloat()
            val normD = (dDst / maxD).coerceIn(0f, 1f)
            val normSrcD = normD.toDouble().pow(beta.toDouble()).toFloat()
            val dSrc = normSrcD * maxD

            val xSrc = if (isLeftPage) {
                (maxD - dSrc).coerceIn(0f, maxD)
            } else {
                dSrc.coerceIn(0f, maxD)
            }
            srcXTable[xDst] = xSrc

            val gain = if (dDst < gutterWidth) {
                val t = dDst / gutterWidth
                1.0f + (shadowBoost - 1.0f) * (1.0f - t) * (1.0f - t)
            } else {
                1.0f
            }
            shadowGainTable[xDst] = gain
        }

        return Pair(srcXTable, shadowGainTable)
    }

    /**
     * Unwarps Left Page (spine located at right edge x = W).
     */
    fun unwarpLeftPage(
        pageBitmap: Bitmap,
        gamma: Float = DEFAULT_GAMMA,
        shadowBoost: Float = DEFAULT_SHADOW_BOOST,
        gutterFraction: Float = DEFAULT_GUTTER_FRACTION
    ): Bitmap = transformPage(pageBitmap, isLeftPage = true, gamma, shadowBoost, gutterFraction)

    /**
     * Unwarps Right Page (spine located at left edge x = 0).
     */
    fun unwarpRightPage(
        pageBitmap: Bitmap,
        gamma: Float = DEFAULT_GAMMA,
        shadowBoost: Float = DEFAULT_SHADOW_BOOST,
        gutterFraction: Float = DEFAULT_GUTTER_FRACTION
    ): Bitmap = transformPage(pageBitmap, isLeftPage = false, gamma, shadowBoost, gutterFraction)

    /**
     * Splits spread at spineX and applies 1D cylindrical curvature unwarping and crease shadow removal.
     */
    fun splitAndUnwarpSpread(
        spreadBitmap: Bitmap,
        spineX: Int = detectSpine(spreadBitmap),
        gamma: Float = DEFAULT_GAMMA,
        shadowBoost: Float = DEFAULT_SHADOW_BOOST
    ): Pair<Bitmap, Bitmap> {
        val w = spreadBitmap.width
        val h = spreadBitmap.height
        val validSpineX = spineX.coerceIn((w * 0.15f).toInt().coerceAtLeast(1), (w * 0.85f).toInt().coerceAtMost(w - 1))

        val leftRaw = Bitmap.createBitmap(spreadBitmap, 0, 0, validSpineX, h)
        val leftUnwarped = unwarpLeftPage(leftRaw, gamma, shadowBoost)
        if (leftUnwarped != leftRaw) {
            leftRaw.recycle()
        }

        val rightWidth = w - validSpineX
        val rightRaw = Bitmap.createBitmap(spreadBitmap, validSpineX, 0, rightWidth, h)
        val rightUnwarped = unwarpRightPage(rightRaw, gamma, shadowBoost)
        if (rightUnwarped != rightRaw) {
            rightRaw.recycle()
        }

        return Pair(leftUnwarped, rightUnwarped)
    }

    private fun transformPage(
        srcBitmap: Bitmap,
        isLeftPage: Boolean,
        gamma: Float,
        shadowBoost: Float,
        gutterFraction: Float
    ): Bitmap {
        val width = srcBitmap.width
        val height = srcBitmap.height
        if (width < 2 || height < 2) return srcBitmap

        val (srcXTable, shadowGainTable) = computeUnwarpLUT(width, isLeftPage, gamma, shadowBoost, gutterFraction)

        val srcPixels = IntArray(width * height)
        srcBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val dstPixels = IntArray(width * height)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (xDst in 0 until width) {
                val xSrcFloat = srcXTable[xDst]
                val x0 = xSrcFloat.toInt().coerceIn(0, width - 1)
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val frac = xSrcFloat - x0

                val p0 = srcPixels[rowOffset + x0]
                val p1 = srcPixels[rowOffset + x1]

                val r0 = (p0 shr 16) and 0xFF
                val g0 = (p0 shr 8) and 0xFF
                val b0 = p0 and 0xFF

                val r1 = (p1 shr 16) and 0xFF
                val g1 = (p1 shr 8) and 0xFF
                val b1 = p1 and 0xFF

                val r = r0 + ((r1 - r0) * frac).toInt()
                val g = g0 + ((g1 - g0) * frac).toInt()
                val b = b0 + ((b1 - b0) * frac).toInt()

                val gain = shadowGainTable[xDst]
                val finalR = (r * gain).toInt().coerceIn(0, 255)
                val finalG = (g * gain).toInt().coerceIn(0, 255)
                val finalB = (b * gain).toInt().coerceIn(0, 255)

                dstPixels[rowOffset + xDst] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        val dstBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dstBitmap.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return dstBitmap
    }
}

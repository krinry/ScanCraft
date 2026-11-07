package com.krinry.scancraft.cv

import android.graphics.Bitmap
import com.krinry.scancraft.data.model.EnhanceMode
import kotlin.math.max
import kotlin.math.min

object ImageEnhancer {

    fun applyFilterInPlace(bitmap: Bitmap, modeId: Int) {
        when (modeId) {
            EnhanceMode.MAGIC_COLOR.id -> applyMagicColor(bitmap)
            EnhanceMode.NO_SHADOW.id -> applyNoShadow(bitmap)
            EnhanceMode.LIGHTEN.id -> applyLighten(bitmap)
            EnhanceMode.DEBLUR.id -> applyDeblur(bitmap)
            EnhanceMode.COLOR_DOC.id -> applyColorDoc(bitmap)
            EnhanceMode.BW.id -> applyBradleySauvolaBW(bitmap, 0.20f)
            EnhanceMode.BW2.id -> applyBradleySauvolaBW(bitmap, 0.28f)
            EnhanceMode.GRAYSCALE.id -> applyGrayscale(bitmap)
            EnhanceMode.BLUEPRINT.id -> applyBlueprint(bitmap)
            EnhanceMode.ECO_TEXT.id -> applyBradleySauvolaBW(bitmap, 0.14f)
            EnhanceMode.STAMP.id -> applyStamp(bitmap)
            EnhanceMode.WARM.id -> applyWarm(bitmap)
            EnhanceMode.ORIGINAL.id -> { /* No-op */ }
        }
    }

    fun applyMagicColor(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = ByteArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            gray[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }

        val step = 10
        val bw = (width + step - 1) / step
        val bh = (height + step - 1) / step
        val bg = IntArray(bw * bh)

        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                var maxVal = 0
                val startY = by * step
                val endY = min(startY + step, height)
                val startX = bx * step
                val endX = min(startX + step, width)
                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val lum = gray[y * width + x].toInt() and 0xff
                        if (lum > maxVal) maxVal = lum
                    }
                }
                bg[by * bw + bx] = max(maxVal, 128)
            }
        }

        for (y in 0 until height) {
            val by = min(y / step, bh - 1)
            for (x in 0 until width) {
                val bx = min(x / step, bw - 1)
                val bgVal = bg[by * bw + bx].toFloat()

                val p = pixels[y * width + x]
                var r = (p shr 16) and 0xff
                var g = (p shr 8) and 0xff
                var b = p and 0xff

                val factor = 255f / bgVal
                r = (r * factor).toInt()
                g = (g * factor).toInt()
                b = (b * factor).toInt()

                val lum = (r * 77 + g * 150 + b * 29) shr 8
                if (lum < 160) {
                    val darkFactor = (lum / 160f) * 0.85f
                    r = (r * darkFactor).toInt()
                    g = (g * darkFactor).toInt()
                    b = (b * darkFactor).toInt()
                }

                pixels[y * width + x] = (0xFF shl 24) or
                        (clamp255(r) shl 16) or
                        (clamp255(g) shl 8) or
                        clamp255(b)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyBradleySauvolaBW(bitmap: Bitmap, k: Float) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var sum = 0L
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val lum = (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8
                sum += lum
                integral[(y + 1) * (width + 1) + (x + 1)] = integral[y * (width + 1) + (x + 1)] + sum
            }
        }

        val s = max(width, height) / 16
        val s2 = s / 2
        val threshT = 1.0f - k

        for (y in 0 until height) {
            val y1 = max(0, y - s2)
            val y2 = min(height - 1, y + s2)
            for (x in 0 until width) {
                val x1 = max(0, x - s2)
                val x2 = min(width - 1, x + s2)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)] -
                          integral[y1 * (width + 1) + (x2 + 1)] -
                          integral[(y2 + 1) * (width + 1) + x1] +
                          integral[y1 * (width + 1) + x1]

                val p = pixels[y * width + x]
                val lum = (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8

                if (lum * count < sum * threshT) {
                    pixels[y * width + x] = 0xFF000000.toInt()
                } else {
                    pixels[y * width + x] = 0xFFFFFFFF.toInt()
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyGrayscale(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minL = 255
        var maxL = 0
        val lums = IntArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val l = (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8
            lums[i] = l
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }

        val range = max(1, maxL - minL).toFloat()
        for (i in 0 until total) {
            val stretched = clamp255(((lums[i] - minL) * 255f / range).toInt())
            pixels[i] = (0xFF shl 24) or (stretched shl 16) or (stretched shl 8) or stretched
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyLighten(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in 0 until total) {
            val p = pixels[i]
            var r = (p shr 16) and 0xff
            var g = (p shr 8) and 0xff
            var b = p and 0xff

            r = min(255, (r * 1.25f + 15).toInt())
            g = min(255, (g * 1.25f + 15).toInt())
            b = min(255, (b * 1.25f + 15).toInt())

            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyNoShadow(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = IntArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            lum[i] = (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8
        }

        val step = 16
        val bw = (width + step - 1) / step
        val bh = (height + step - 1) / step
        val bg = IntArray(bw * bh)

        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                val startY = by * step
                val endY = min(startY + step, height)
                val startX = bx * step
                val endX = min(startX + step, width)

                val blockLums = ArrayList<Int>((endY - startY) * (endX - startX))
                for (y in startY until endY) {
                    for (x in startX until endX) {
                        blockLums.add(lum[y * width + x])
                    }
                }
                if (blockLums.isNotEmpty()) {
                    blockLums.sort()
                    val p90 = blockLums.size * 90 / 100
                    bg[by * bw + bx] = max(blockLums[p90], 60)
                } else {
                    bg[by * bw + bx] = 128
                }
            }
        }

        for (y in 0 until height) {
            val fby = y.toFloat() / step
            val by0 = min(fby.toInt(), bh - 1)
            val by1 = min(by0 + 1, bh - 1)
            val fy = fby - by0

            for (x in 0 until width) {
                val fbx = x.toFloat() / step
                val bx0 = min(fbx.toInt(), bw - 1)
                val bx1 = min(bx0 + 1, bw - 1)
                val fx = fbx - bx0

                val bgEst = ((1f - fx) * (1f - fy) * bg[by0 * bw + bx0]
                        + fx * (1f - fy) * bg[by0 * bw + bx1]
                        + (1f - fx) * fy * bg[by1 * bw + bx0]
                        + fx * fy * bg[by1 * bw + bx1]).coerceAtLeast(60f)

                val p = pixels[y * width + x]
                var r = (p shr 16) and 0xff
                var g = (p shr 8) and 0xff
                var b = p and 0xff

                val factor = 245f / bgEst
                r = clamp255((r * factor).toInt())
                g = clamp255((g * factor).toInt())
                b = clamp255((b * factor).toInt())

                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyDeblur(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return
        val total = width * height
        val src = IntArray(total)
        val dst = IntArray(total)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)
        System.arraycopy(src, 0, dst, 0, total)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = src[y * width + x]
                val cr = (center shr 16) and 0xff
                val cg = (center shr 8) and 0xff
                val cb = center and 0xff

                val top = src[(y - 1) * width + x]
                val bot = src[(y + 1) * width + x]
                val left = src[y * width + (x - 1)]
                val right = src[y * width + (x + 1)]

                val lapR = 4 * cr - (((top shr 16) and 0xff) + ((bot shr 16) and 0xff) + ((left shr 16) and 0xff) + ((right shr 16) and 0xff))
                val lapG = 4 * cg - (((top shr 8) and 0xff) + ((bot shr 8) and 0xff) + ((left shr 8) and 0xff) + ((right shr 8) and 0xff))
                val lapB = 4 * cb - ((top and 0xff) + (bot and 0xff) + (left and 0xff) + (right and 0xff))

                val nr = clamp255((cr + 0.65f * lapR).toInt())
                val ng = clamp255((cg + 0.65f * lapG).toInt())
                val nb = clamp255((cb + 0.65f * lapB).toInt())

                dst[y * width + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        bitmap.setPixels(dst, 0, width, 0, 0, width, height)
    }

    fun applyColorDoc(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            // Boost saturation by 35%
            hsv[1] = min(1.0f, hsv[1] * 1.35f)
            // Brighten paper
            if (hsv[2] > 0.65f) {
                hsv[2] = min(1.0f, hsv[2] * 1.15f + 0.05f)
            }
            pixels[i] = android.graphics.Color.HSVToColor(hsv)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun adjustBrightnessContrastInPlace(bitmap: Bitmap, brightness: Int, contrast: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val factor = (259f * (contrast + 255f)) / (255f * (259f - contrast))

        for (i in 0 until total) {
            val p = pixels[i]
            var r = (p shr 16) and 0xff
            var g = (p shr 8) and 0xff
            var b = p and 0xff

            r = (factor * (r - 128) + 128 + brightness).toInt()
            g = (factor * (g - 128) + 128 + brightness).toInt()
            b = (factor * (b - 128) + 128 + brightness).toInt()

            pixels[i] = (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyBlueprint(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            val inv = 255 - lum
            val nr = if (inv < 120) (inv * 0.12f).toInt() else (200 + ((inv - 120) / 135f) * 55).toInt()
            val ng = if (inv < 120) (inv * 0.20f).toInt() else (220 + ((inv - 120) / 135f) * 35).toInt()
            val nb = if (inv < 120) (inv * 0.35f + 16).toInt() else 255
            pixels[i] = (0xFF shl 24) or (clamp255(nr) shl 16) or (clamp255(ng) shl 8) or clamp255(nb)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyStamp(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val isRedInk = (r > 110) && (r > g * 1.35f) && (r > b * 1.35f)
            val isBlueInk = (b > 100) && (b > r * 1.25f) && (b > g * 1.15f)
            if (isRedInk) {
                val nr = min(255, (r * 1.35f + 15).toInt())
                val ng = (g * 0.75f).toInt()
                val nb = (b * 0.75f).toInt()
                pixels[i] = (0xFF shl 24) or (clamp255(nr) shl 16) or (clamp255(ng) shl 8) or clamp255(nb)
            } else if (isBlueInk) {
                val nr = (r * 0.70f).toInt()
                val ng = (g * 0.85f).toInt()
                val nb = min(255, (b * 1.35f + 20).toInt())
                pixels[i] = (0xFF shl 24) or (clamp255(nr) shl 16) or (clamp255(ng) shl 8) or clamp255(nb)
            } else {
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                if (lum < 140) {
                    val dark = (lum * 0.65f).toInt()
                    pixels[i] = (0xFF shl 24) or (dark shl 16) or (dark shl 8) or dark
                } else {
                    pixels[i] = -1 // Pure white
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun applyWarm(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            if (lum > 140) {
                val wr = min(255, lum + 10)
                val wg = min(255, lum + 4)
                val wb = max(0, lum - 14)
                pixels[i] = (0xFF shl 24) or (clamp255(wr) shl 16) or (clamp255(wg) shl 8) or clamp255(wb)
            } else {
                val dark = max(20, (lum * 0.85f).toInt())
                pixels[i] = (0xFF shl 24) or ((dark + 4) shl 16) or (dark shl 8) or max(0, dark - 4)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun clamp255(valInt: Int): Int = when {
        valInt < 0 -> 0
        valInt > 255 -> 255
        else -> valInt
    }
}

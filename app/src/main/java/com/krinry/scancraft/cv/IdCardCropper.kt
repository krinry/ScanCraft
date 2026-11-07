package com.krinry.scancraft.cv

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Clean-room ISO/IEC 7810 ID-1 standard card cropping utility.
 * Aspect ratio: 85.60mm / 53.98mm = 1.586.
 */
object IdCardCropper {
    const val ID1_ASPECT_RATIO = 1.586f
    const val CARD_WIDTH_RATIO = 0.85f

    private fun createRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect(left, top, right, bottom).apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    /**
     * Calculates the bounding crop rectangle in bitmap pixel space corresponding
     * to the 85% width, centered 1.586 aspect ratio viewfinder guide on screen.
     */
    fun calculateCropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): Rect {
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            val fallbackW = (bitmapWidth * CARD_WIDTH_RATIO).toInt()
            val fallbackH = Math.round(fallbackW / ID1_ASPECT_RATIO).toInt().coerceAtLeast(1)
            val fallbackX = (bitmapWidth - fallbackW) / 2
            val fallbackY = (bitmapHeight - fallbackH) / 2
            return createRect(fallbackX, fallbackY, fallbackX + fallbackW, fallbackY + fallbackH)
        }

        // CameraX PreviewView FILL_CENTER uniform scale factor
        val scale = maxOf(viewWidth.toFloat() / bitmapWidth, viewHeight.toFloat() / bitmapHeight)
        val renderedWidth = bitmapWidth * scale
        val renderedHeight = bitmapHeight * scale
        val offsetX = (viewWidth - renderedWidth) / 2f
        val offsetY = (viewHeight - renderedHeight) / 2f

        // Viewfinder guide bounds on view
        val cardW = viewWidth * CARD_WIDTH_RATIO
        val cardH = cardW / ID1_ASPECT_RATIO
        val cardX = (viewWidth - cardW) / 2f
        val cardY = (viewHeight - cardH) / 2f

        // Map view coordinates back to bitmap coordinates
        val cropX = ((cardX - offsetX) / scale).toInt().coerceIn(0, bitmapWidth - 1)
        val cropY = ((cardY - offsetY) / scale).toInt().coerceIn(0, bitmapHeight - 1)
        val cropW = (cardW / scale).toInt().coerceAtMost(bitmapWidth - cropX).coerceAtLeast(1)
        val cropH = (cardH / scale).toInt().coerceAtMost(bitmapHeight - cropY).coerceAtLeast(1)

        return createRect(cropX, cropY, cropX + cropW, cropY + cropH)
    }

    /**
     * Crops the ID card region from the raw captured frame.
     */
    fun cropCard(
        rawBitmap: Bitmap,
        viewWidth: Int,
        viewHeight: Int
    ): Bitmap {
        val rect = calculateCropRect(rawBitmap.width, rawBitmap.height, viewWidth, viewHeight)
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        return Bitmap.createBitmap(rawBitmap, rect.left, rect.top, w, h)
    }
}

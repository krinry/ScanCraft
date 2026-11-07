package com.krinry.scancraft.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

object PerspectiveTransformer {

    fun warp(src: Bitmap, quad: DocumentQuadrilateral, targetWidth: Int, targetHeight: Int): Bitmap {
        val dstBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dstBitmap)

        val srcPoints = quad.toFloatArray()
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        // setPolyToPoly maps 4 corners of quad to 4 corners of target rectangle
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)

        return dstBitmap
    }
}

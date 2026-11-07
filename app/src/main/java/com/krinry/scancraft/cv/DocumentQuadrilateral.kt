package com.krinry.scancraft.cv

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.hypot

@Parcelize
data class PointF(
    val x: Float,
    val y: Float
) : Parcelable {
    fun distanceTo(other: PointF): Float = hypot(x - other.x, y - other.y)
}

@Parcelize
data class DocumentQuadrilateral(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) : Parcelable {

    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    /**
     * Compute polygon area using Shoelace formula.
     */
    fun area(): Float {
        return 0.5f * kotlin.math.abs(
            (topLeft.x * topRight.y - topRight.x * topLeft.y) +
            (topRight.x * bottomRight.y - bottomRight.x * topRight.y) +
            (bottomRight.x * bottomLeft.y - bottomLeft.x * bottomRight.y) +
            (bottomLeft.x * topLeft.y - topLeft.x * bottomLeft.y)
        )
    }

    /**
     * Check if the quadrilateral is strictly convex and non-self-intersecting.
     * Prevents inverted or self-crossing "bowtie" polygons from crashing perspective warp.
     */
    fun isValidConvex(minArea: Float = 400f): Boolean {
        val pts = listOf(topLeft, topRight, bottomRight, bottomLeft)
        var positive = false
        var negative = false
        for (i in 0 until 4) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % 4]
            val p2 = pts[(i + 2) % 4]
            val dx1 = p1.x - p0.x
            val dy1 = p1.y - p0.y
            val dx2 = p2.x - p1.x
            val dy2 = p2.y - p1.y
            val cross = dx1 * dy2 - dy1 * dx2
            if (cross > 1e-3f) positive = true
            if (cross < -1e-3f) negative = true
            if (positive && negative) return false
        }
        return (positive || negative) && area() >= minArea
    }

    companion object {
        fun fromFloatArray(arr: FloatArray): DocumentQuadrilateral {
            require(arr.size >= 8) { "Array must contain at least 8 elements (4 points)" }
            return DocumentQuadrilateral(
                topLeft = PointF(arr[0], arr[1]),
                topRight = PointF(arr[2], arr[3]),
                bottomRight = PointF(arr[4], arr[5]),
                bottomLeft = PointF(arr[6], arr[7])
            )
        }

        fun defaultForDimensions(width: Float, height: Float, marginFraction: Float = 0.08f): DocumentQuadrilateral {
            val mx = width * marginFraction
            val my = height * marginFraction
            return DocumentQuadrilateral(
                topLeft = PointF(mx, my),
                topRight = PointF(width - mx, my),
                bottomRight = PointF(width - mx, height - my),
                bottomLeft = PointF(mx, height - my)
            )
        }
    }

    fun isDefaultFallback(width: Float, height: Float, tolerance: Float = 24f): Boolean {
        val mx = width * 0.08f
        val my = height * 0.08f
        return kotlin.math.abs(topLeft.x - mx) <= tolerance &&
               kotlin.math.abs(topLeft.y - my) <= tolerance &&
               kotlin.math.abs(topRight.x - (width - mx)) <= tolerance &&
               kotlin.math.abs(topRight.y - my) <= tolerance &&
               kotlin.math.abs(bottomRight.x - (width - mx)) <= tolerance &&
               kotlin.math.abs(bottomRight.y - (height - my)) <= tolerance &&
               kotlin.math.abs(bottomLeft.x - mx) <= tolerance &&
               kotlin.math.abs(bottomLeft.y - (height - my)) <= tolerance
    }
}

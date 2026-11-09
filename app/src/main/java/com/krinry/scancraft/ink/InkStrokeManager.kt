package com.krinry.scancraft.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Brush representation for custom digital ink strokes.
 */
enum class InkBrushType {
    PEN,
    HIGHLIGHTER
}

data class InkBrush(
    val size: Float,
    val colorArgb: Int,
    val type: InkBrushType = InkBrushType.PEN,
    val opacity: Float = if (type == InkBrushType.HIGHLIGHTER) 0.35f else 1.0f
)

data class InkStroke(
    val brush: InkBrush,
    val points: List<Offset>,
    val smoothedPoints: List<Offset> = InkStrokeManager.smoothPoints(points)
)

/**
 * Lightweight, zero-dependency custom digital ink and vector stroke processor.
 * Replaces heavy external Ink libraries with pure Kotlin vector smoothing
 * and fast point-array flattening for native ThorVG rendering.
 */
object InkStrokeManager {

    fun createPenBrush(size: Float, color: Color): InkBrush {
        return createPenBrush(size, color.toArgb())
    }

    fun createPenBrush(size: Float, colorArgb: Int): InkBrush {
        return InkBrush(
            size = size,
            colorArgb = colorArgb,
            type = InkBrushType.PEN
        )
    }

    fun createHighlighterBrush(size: Float, color: Color): InkBrush {
        return createHighlighterBrush(size, color.toArgb())
    }

    fun createHighlighterBrush(size: Float, colorArgb: Int): InkBrush {
        return InkBrush(
            size = size,
            colorArgb = colorArgb,
            type = InkBrushType.HIGHLIGHTER
        )
    }

    /**
     * Builds a custom InkStroke from a sequence of touch offsets.
     */
    fun createStroke(
        points: List<Offset>,
        brush: InkBrush
    ): InkStroke? {
        if (points.size < 2) return null
        return InkStroke(
            brush = brush,
            points = points
        )
    }

    /**
     * Smooths an input path using Chaikin's corner cutting algorithm.
     * Produces smooth, natural handwriting curves without external dependencies.
     */
    fun smoothPoints(points: List<Offset>, iterations: Int = 1): List<Offset> {
        if (points.size <= 2 || iterations <= 0) return points

        var current = points
        repeat(iterations) {
            val smoothed = ArrayList<Offset>(current.size * 2)
            smoothed.add(current.first())
            for (i in 0 until current.size - 1) {
                val p0 = current[i]
                val p1 = current[i + 1]
                val q = Offset(0.75f * p0.x + 0.25f * p1.x, 0.75f * p0.y + 0.25f * p1.y)
                val r = Offset(0.25f * p0.x + 0.75f * p1.x, 0.25f * p0.y + 0.75f * p1.y)
                smoothed.add(q)
                smoothed.add(r)
            }
            smoothed.add(current.last())
            current = smoothed
        }
        return current
    }

    /**
     * Converts a sequence of points into a flat float array [x0, y0, x1, y1, ...]
     * for high-speed native ThorVG vector rendering.
     */
    fun toFlatPoints(points: List<Offset>): FloatArray {
        val result = FloatArray(points.size * 2)
        for (i in points.indices) {
            result[i * 2] = points[i].x
            result[i * 2 + 1] = points[i].y
        }
        return result
    }
}

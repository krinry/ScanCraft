package com.krinry.scancraft.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke

/**
 * High-precision touch and stylus digital ink manager powered by AndroidX Ink.
 * Captures touch streams, configures realistic stock brushes, and produces
 * smoothed vector coordinate arrays for native ThorVG rendering.
 */
object InkStrokeManager {

    fun createPenBrush(size: Float, color: Color): Brush {
        return createPenBrush(size, color.toArgb())
    }

    fun createPenBrush(size: Float, colorArgb: Int): Brush {
        return Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePenLatest,
            colorIntArgb = colorArgb,
            size = size,
            epsilon = 0.1f
        )
    }

    fun createHighlighterBrush(size: Float, color: Color): Brush {
        return createHighlighterBrush(size, color.toArgb())
    }

    fun createHighlighterBrush(size: Float, colorArgb: Int): Brush {
        return Brush.createWithColorIntArgb(
            family = StockBrushes.highlighterLatest,
            colorIntArgb = colorArgb,
            size = size,
            epsilon = 0.1f
        )
    }

    /**
     * Builds an AndroidX Ink Stroke from a sequence of touch offsets.
     */
    fun createStroke(
        points: List<Offset>,
        brush: Brush
    ): Stroke? {
        if (points.size < 2) return null
        val batch = MutableStrokeInputBatch()

        points.forEachIndexed { index, pt ->
            val elapsed = (index * 16).toLong()
            batch.addOrIgnore(
                type = InputToolType.TOUCH,
                x = pt.x,
                y = pt.y,
                elapsedTimeMillis = elapsed
            )
        }

        return try {
            Stroke(brush = brush, inputs = batch)
        } catch (_: Exception) {
            null
        }
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

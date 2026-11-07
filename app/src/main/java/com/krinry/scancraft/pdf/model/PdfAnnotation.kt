package com.krinry.scancraft.pdf.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

data class OffsetNormalized(val x: Float, val y: Float) {
    fun toScreenOffset(pageWidth: Float, pageHeight: Float): Offset =
        Offset(x * pageWidth, y * pageHeight)

    companion object {
        fun fromScreenOffset(offset: Offset, pageWidth: Float, pageHeight: Float): OffsetNormalized {
            val pw = pageWidth.coerceAtLeast(1f)
            val ph = pageHeight.coerceAtLeast(1f)
            return OffsetNormalized(
                x = (offset.x / pw).coerceIn(0f, 1f),
                y = (offset.y / ph).coerceIn(0f, 1f)
            )
        }
    }
}

data class RectNormalized(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun toScreenRect(pageWidth: Float, pageHeight: Float): Rect =
        Rect(left * pageWidth, top * pageHeight, right * pageWidth, bottom * pageHeight)

    fun contains(pt: OffsetNormalized): Boolean =
        pt.x in left..right && pt.y in top..bottom

    companion object {
        fun fromScreenPoints(p1: Offset, p2: Offset, pageWidth: Float, pageHeight: Float): RectNormalized {
            val pw = pageWidth.coerceAtLeast(1f)
            val ph = pageHeight.coerceAtLeast(1f)
            val minX = minOf(p1.x, p2.x) / pw
            val maxX = maxOf(p1.x, p2.x) / pw
            val minY = minOf(p1.y, p2.y) / ph
            val maxY = maxOf(p1.y, p2.y) / ph
            return RectNormalized(
                left = minX.coerceIn(0f, 1f),
                top = minY.coerceIn(0f, 1f),
                right = maxX.coerceIn(0f, 1f),
                bottom = maxY.coerceIn(0f, 1f)
            )
        }
    }
}

enum class AnnotationShapeType(val label: String) {
    RECTANGLE("Box"),
    FILLED_RECT("Redaction"),
    HIGHLIGHTER_BOX("Highlight Box"),
    CIRCLE("Circle"),
    ARROW("Arrow"),
    LINE("Line")
}

enum class ShapeFillType {
    NONE,
    SOLID,
    LINEAR_GRADIENT,
    RADIAL_GRADIENT
}

enum class ShapeDashStyle {
    SOLID,
    DASHED,
    DOTTED
}

sealed class PdfAnnotation {
    abstract val id: String
    abstract val zIndex: Int

    abstract fun getBoundingBox(): RectNormalized

    data class InkStroke(
        override val id: String = UUID.randomUUID().toString(),
        override val zIndex: Int = 0,
        val points: List<OffsetNormalized>,
        val colorArgb: Int,
        val strokeWidthNorm: Float, // Stroke width relative to page width
        val opacity: Float = 1.0f,
        val isHighlighter: Boolean = false,
        val isSmudge: Boolean = false
    ) : PdfAnnotation() {
        override fun getBoundingBox(): RectNormalized {
            if (points.isEmpty()) return RectNormalized(0f, 0f, 0f, 0f)
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val padding = strokeWidthNorm / 2f
            return RectNormalized(
                left = (minX - padding).coerceAtLeast(0f),
                top = (minY - padding).coerceAtLeast(0f),
                right = (maxX + padding).coerceAtMost(1f),
                bottom = (maxY + padding).coerceAtMost(1f)
            )
        }
    }

    data class ShapeOverlay(
        override val id: String = UUID.randomUUID().toString(),
        override val zIndex: Int = 0,
        val shapeType: AnnotationShapeType,
        val bounds: RectNormalized,
        val borderColorArgb: Int,
        val strokeWidthNorm: Float,
        val borderOpacity: Float = 1.0f,
        val fillType: ShapeFillType = ShapeFillType.NONE,
        val fillColor1Argb: Int = borderColorArgb,
        val fillColor2Argb: Int = borderColorArgb,
        val fillOpacity: Float = 1.0f,
        val dashStyle: ShapeDashStyle = ShapeDashStyle.SOLID,
        val cornerRadiusNorm: Float = 0.012f,
        val rotationDegrees: Float = 0f
    ) : PdfAnnotation() {
        override fun getBoundingBox(): RectNormalized = bounds
    }

    data class TextNote(
        override val id: String = UUID.randomUUID().toString(),
        override val zIndex: Int = 0,
        val text: String,
        val position: OffsetNormalized,
        val colorArgb: Int,
        val fontSizeNorm: Float = 0.024f, // 2.4% of page height
        val backgroundColorArgb: Int? = null,
        val isBold: Boolean = true
    ) : PdfAnnotation() {
        override fun getBoundingBox(): RectNormalized {
            // Estimated text bounds based on length and font size
            val estWidth = (text.length * fontSizeNorm * 0.55f).coerceIn(0.08f, 0.95f)
            val estHeight = fontSizeNorm * 1.5f
            return RectNormalized(
                left = position.x.coerceIn(0f, 1f - estWidth),
                top = position.y.coerceIn(0f, 1f - estHeight),
                right = (position.x + estWidth).coerceAtMost(1f),
                bottom = (position.y + estHeight).coerceAtMost(1f)
            )
        }
    }

    data class SignatureStamp(
        override val id: String = UUID.randomUUID().toString(),
        override val zIndex: Int = 0,
        val bounds: RectNormalized,
        val inkColorArgb: Int,
        val strokes: List<List<OffsetNormalized>>,
        val rotationDegrees: Float = 0f
    ) : PdfAnnotation() {
        override fun getBoundingBox(): RectNormalized = bounds
    }
}

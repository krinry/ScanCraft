package com.krinry.scancraft.cv

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * High-performance vector graphics and rasterization engine powered by native ThorVG.
 * Provides sub-millisecond anti-aliased vector rendering, smooth Bézier strokes,
 * geometric shapes with linear/radial gradients, dashed borders, opacity control,
 * and native smudge/blur redactions directly on Android Bitmap buffers.
 */
object ThorvgEngine {

    init {
        try {
            System.loadLibrary("scancraft")
            nativeInit()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    enum class ShapeType(val id: Int) {
        RECTANGLE(0),
        FILLED_BOX(1),
        CIRCLE(2),
        LINE(3),
        ARROW(4),
        HIGHLIGHTER_BOX(5)
    }

    enum class FillType(val id: Int) {
        NONE(0),
        SOLID(1),
        LINEAR_GRADIENT(2),
        RADIAL_GRADIENT(3)
    }

    enum class DashStyle(val id: Int) {
        SOLID(0),
        DASHED(1),
        DOTTED(2)
    }

    /**
     * Renders a smooth, non-pixelating vector path for freehand drawing or digital signatures.
     * Uses continuous tangent Bézier spline fitting in native ThorVG for ultra-smooth lines.
     */
    fun renderSmoothVectorStroke(
        bitmap: Bitmap,
        points: FloatArray,
        strokeWidth: Float,
        color: Color,
        opacity: Float = 1.0f,
        isCubicSmoothed: Boolean = true
    ): Boolean {
        if (points.size < 4) return false
        val effectiveColor = color.copy(alpha = (color.alpha * opacity).coerceIn(0f, 1f))
        val argb = effectiveColor.toArgb()
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        return nativeRenderSmoothVectorStroke(
            bitmap = bitmap,
            points = points,
            strokeWidth = strokeWidth,
            r = r,
            g = g,
            b = b,
            a = a,
            isCubicSmoothed = isCubicSmoothed
        )
    }

    /**
     * Renders an anti-aliased freehand stroke onto [bitmap].
     */
    fun renderStroke(
        bitmap: Bitmap,
        points: FloatArray,
        strokeWidth: Float,
        color: Color
    ): Boolean {
        return renderSmoothVectorStroke(
            bitmap = bitmap,
            points = points,
            strokeWidth = strokeWidth,
            color = color,
            isCubicSmoothed = true
        )
    }

    /**
     * Renders an advanced styled shape overlay with gradient fill (Linear/Radial),
     * border stroke, dash pattern, and opacity control.
     */
    fun renderStyledShape(
        bitmap: Bitmap,
        shapeType: ShapeType,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        borderColor: Color,
        borderOpacity: Float = 1.0f,
        fillType: FillType = FillType.NONE,
        fillColor1: Color = borderColor,
        fillColor2: Color = borderColor,
        fillOpacity: Float = 1.0f,
        dashStyle: DashStyle = DashStyle.SOLID,
        cornerRadius: Float = 6.0f
    ): Boolean {
        val sArgb = borderColor.copy(alpha = (borderColor.alpha * borderOpacity).coerceIn(0f, 1f)).toArgb()
        val sa = (sArgb ushr 24) and 0xFF
        val sr = (sArgb ushr 16) and 0xFF
        val sg = (sArgb ushr 8) and 0xFF
        val sb = sArgb and 0xFF

        val f1Argb = fillColor1.copy(alpha = (fillColor1.alpha * fillOpacity).coerceIn(0f, 1f)).toArgb()
        val f1a = (f1Argb ushr 24) and 0xFF
        val f1r = (f1Argb ushr 16) and 0xFF
        val f1g = (f1Argb ushr 8) and 0xFF
        val f1b = f1Argb and 0xFF

        val f2Argb = fillColor2.copy(alpha = (fillColor2.alpha * fillOpacity).coerceIn(0f, 1f)).toArgb()
        val f2a = (f2Argb ushr 24) and 0xFF
        val f2r = (f2Argb ushr 16) and 0xFF
        val f2g = (f2Argb ushr 8) and 0xFF
        val f2b = f2Argb and 0xFF

        return nativeRenderStyledShape(
            bitmap = bitmap,
            shapeType = shapeType.id,
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            strokeWidth = strokeWidth,
            strokeR = sr,
            strokeG = sg,
            strokeB = sb,
            strokeA = sa,
            fillType = fillType.id,
            fillR1 = f1r,
            fillG1 = f1g,
            fillB1 = f1b,
            fillA1 = f1a,
            fillR2 = f2r,
            fillG2 = f2g,
            fillB2 = f2b,
            fillA2 = f2a,
            dashStyle = dashStyle.id,
            cornerRadius = cornerRadius
        )
    }

    /**
     * Basic geometric shape rendering wrapper.
     */
    fun renderShape(
        bitmap: Bitmap,
        shapeType: ShapeType,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        color: Color,
        isFilled: Boolean = false
    ): Boolean {
        val argb = color.toArgb()
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        return nativeRenderShape(
            bitmap = bitmap,
            shapeType = shapeType.id,
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            strokeWidth = strokeWidth,
            r = r,
            g = g,
            b = b,
            a = a,
            isFilled = isFilled
        )
    }

    /**
     * Renders an SVG vector snippet directly onto [bitmap] at the given coordinates.
     */
    fun renderSvg(
        bitmap: Bitmap,
        svgString: String,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = 0f,
        height: Float = 0f,
        angle: Float = 0f,
        opacity: Float = 1.0f
    ): Boolean {
        return nativeRenderSvg(
            bitmap = bitmap,
            svgString = svgString,
            x = x,
            y = y,
            width = width,
            height = height,
            angle = angle,
            opacity = opacity
        )
    }

    /**
     * Applies a high-speed native Gaussian/smudge blur over a sensitive region of [bitmap].
     */
    fun applySmudgeBlur(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        radius: Int = 14
    ): Boolean {
        return nativeApplySmudgeBlur(
            bitmap = bitmap,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            radius = radius
        )
    }

    /**
     * Renders a vector watermark onto [bitmap] using ThorVG SVG capabilities.
     */
    fun renderWatermark(
        bitmap: Bitmap,
        text: String,
        angle: Float = -35f,
        opacity: Float = 0.28f,
        colorHex: String = "#888888"
    ): Boolean {
        if (text.isBlank()) return true
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val fontSize = (w * 0.055f).coerceIn(24f, 80f)

        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h">
                <text x="${w / 2f}" y="${h / 2f}" 
                      font-size="$fontSize" 
                      font-family="sans-serif" 
                      font-weight="bold" 
                      fill="$colorHex" 
                      text-anchor="middle" 
                      dominant-baseline="central"
                      transform="rotate($angle, ${w / 2f}, ${h / 2f})">
                    ${escapeXml(text)}
                </text>
            </svg>
        """.trimIndent()

        return renderSvg(
            bitmap = bitmap,
            svgString = svg,
            x = 0f,
            y = 0f,
            width = w,
            height = h,
            angle = 0f,
            opacity = opacity
        )
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // Native JNI bindings
    @JvmStatic
    private external fun nativeInit(): Boolean

    @JvmStatic
    private external fun nativeRenderStroke(
        bitmap: Bitmap,
        points: FloatArray,
        strokeWidth: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int
    ): Boolean

    @JvmStatic
    private external fun nativeRenderSmoothVectorStroke(
        bitmap: Bitmap,
        points: FloatArray,
        strokeWidth: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        isCubicSmoothed: Boolean
    ): Boolean

    @JvmStatic
    private external fun nativeRenderShape(
        bitmap: Bitmap,
        shapeType: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        isFilled: Boolean
    ): Boolean

    @JvmStatic
    private external fun nativeRenderStyledShape(
        bitmap: Bitmap,
        shapeType: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        strokeR: Int,
        strokeG: Int,
        strokeB: Int,
        strokeA: Int,
        fillType: Int,
        fillR1: Int,
        fillG1: Int,
        fillB1: Int,
        fillA1: Int,
        fillR2: Int,
        fillG2: Int,
        fillB2: Int,
        fillA2: Int,
        dashStyle: Int,
        cornerRadius: Float
    ): Boolean

    @JvmStatic
    private external fun nativeRenderSvg(
        bitmap: Bitmap,
        svgString: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        angle: Float,
        opacity: Float
    ): Boolean

    @JvmStatic
    private external fun nativeApplySmudgeBlur(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        radius: Int
    ): Boolean
}

package com.krinry.scancraft.pdf.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.krinry.scancraft.pdf.model.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class StudioTool {
    HAND,          // Navigate / scroll
    PENCIL,        // Smooth vector pen
    MARKER,        // Semi-transparent highlighter
    SMUDGE_BLUR,   // Privacy redaction blur
    SHAPES,        // Box, Circle, Arrow, Line, Redaction
    TEXT,          // Add draggable text label
    SIGNATURE,     // Stamp signature
    ERASER         // Erase by touching annotation
}

@Composable
fun PdfInteractiveCanvas(
    pageWidth: Float,
    pageHeight: Float,
    annotations: List<PdfAnnotation>,
    activeTool: StudioTool,
    isEditMode: Boolean,
    selectedShapeType: AnnotationShapeType,
    selectedColor: Color,
    strokeWidthNorm: Float,
    annotationOpacity: Float,
    fillType: ShapeFillType,
    dashStyle: ShapeDashStyle,
    gradientEndColor: Color,
    selectedAnnotationId: String?,
    onAnnotationSelected: (PdfAnnotation?) -> Unit,
    onAnnotationCreated: (PdfAnnotation) -> Unit,
    onAnnotationErased: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // In-progress drawing points (normalized)
    var activeStrokePoints by remember { mutableStateOf<List<OffsetNormalized>>(emptyList()) }
    var shapeStartNorm by remember { mutableStateOf<OffsetNormalized?>(null) }
    var shapeEndNorm by remember { mutableStateOf<OffsetNormalized?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(activeTool, isEditMode, pageWidth, pageHeight, annotations) {
                if (!isEditMode || activeTool == StudioTool.HAND) return@pointerInput

                detectTapGestures { tapOffset ->
                    val normPt = OffsetNormalized.fromScreenOffset(tapOffset, pageWidth, pageHeight)

                    if (activeTool == StudioTool.ERASER) {
                        // Find annotation under tap and erase it
                        val hit = annotations.reversed().firstOrNull { it.getBoundingBox().contains(normPt) }
                        if (hit != null) {
                            onAnnotationErased(hit.id)
                        }
                    } else {
                        // Check if tapping existing annotation to select it
                        val hit = annotations.reversed().firstOrNull { it.getBoundingBox().contains(normPt) }
                        onAnnotationSelected(hit)
                    }
                }
            }
            .pointerInput(activeTool, isEditMode, pageWidth, pageHeight) {
                if (!isEditMode || activeTool == StudioTool.HAND || activeTool == StudioTool.ERASER) return@pointerInput

                detectDragGestures(
                    onDragStart = { startOffset ->
                        val startNorm = OffsetNormalized.fromScreenOffset(startOffset, pageWidth, pageHeight)
                        if (activeTool == StudioTool.SHAPES) {
                            shapeStartNorm = startNorm
                            shapeEndNorm = startNorm
                        } else {
                            activeStrokePoints = listOf(startNorm)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val currNorm = OffsetNormalized.fromScreenOffset(change.position, pageWidth, pageHeight)
                        if (activeTool == StudioTool.SHAPES) {
                            shapeEndNorm = currNorm
                        } else {
                            activeStrokePoints = activeStrokePoints + currNorm
                        }
                    },
                    onDragEnd = {
                        if (activeTool == StudioTool.SHAPES) {
                            val s = shapeStartNorm
                            val e = shapeEndNorm
                            if (s != null && e != null) {
                                val bounds = RectNormalized(
                                    left = minOf(s.x, e.x),
                                    top = minOf(s.y, e.y),
                                    right = maxOf(s.x, e.x),
                                    bottom = maxOf(s.y, e.y)
                                )
                                if (bounds.width > 0.01f || bounds.height > 0.01f) {
                                    val isFilled = selectedShapeType == AnnotationShapeType.FILLED_RECT
                                    val isHighlighterBox = selectedShapeType == AnnotationShapeType.HIGHLIGHTER_BOX
                                    val newShape = PdfAnnotation.ShapeOverlay(
                                        shapeType = selectedShapeType,
                                        bounds = bounds,
                                        borderColorArgb = if (isFilled) Color.Black.toArgb() else selectedColor.toArgb(),
                                        strokeWidthNorm = strokeWidthNorm,
                                        borderOpacity = annotationOpacity,
                                        fillType = if (isFilled || isHighlighterBox) ShapeFillType.SOLID else fillType,
                                        fillColor1Argb = if (isFilled) Color.Black.toArgb() else selectedColor.toArgb(),
                                        fillColor2Argb = gradientEndColor.toArgb(),
                                        fillOpacity = if (isHighlighterBox) 0.35f * annotationOpacity else annotationOpacity,
                                        dashStyle = dashStyle
                                    )
                                    onAnnotationCreated(newShape)
                                }
                            }
                            shapeStartNorm = null
                            shapeEndNorm = null
                        } else if (activeStrokePoints.isNotEmpty()) {
                            val isHighlighter = activeTool == StudioTool.MARKER
                            val isSmudge = activeTool == StudioTool.SMUDGE_BLUR
                            val stroke = PdfAnnotation.InkStroke(
                                points = activeStrokePoints,
                                colorArgb = if (isSmudge) Color(0xFF263238).toArgb() else selectedColor.toArgb(),
                                strokeWidthNorm = strokeWidthNorm,
                                opacity = annotationOpacity,
                                isHighlighter = isHighlighter,
                                isSmudge = isSmudge
                            )
                            onAnnotationCreated(stroke)
                            activeStrokePoints = emptyList()
                        }
                    },
                    onDragCancel = {
                        shapeStartNorm = null
                        shapeEndNorm = null
                        activeStrokePoints = emptyList()
                    }
                )
            }
    ) {
        val pw = size.width
        val ph = size.height

        // 1. Render all committed annotations
        for (item in annotations) {
            drawAnnotation(item, pw, ph)
        }

        // 2. Render live stroke in progress
        if (activeStrokePoints.size > 1) {
            val strokePx = (strokeWidthNorm * pw).coerceAtLeast(1.5f)
            val isHighlighter = activeTool == StudioTool.MARKER
            val isSmudge = activeTool == StudioTool.SMUDGE_BLUR

            val strokeAlpha = if (isHighlighter) 0.40f * annotationOpacity else if (isSmudge) 0.70f else annotationOpacity
            val strokeColor = if (isSmudge) Color(0xFF37474F) else selectedColor

            val path = Path()
            val first = activeStrokePoints[0].toScreenOffset(pw, ph)
            path.moveTo(first.x, first.y)
            for (i in 1 until activeStrokePoints.size) {
                val prev = activeStrokePoints[i - 1].toScreenOffset(pw, ph)
                val curr = activeStrokePoints[i].toScreenOffset(pw, ph)
                path.quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
            }
            val last = activeStrokePoints.last().toScreenOffset(pw, ph)
            path.lineTo(last.x, last.y)

            drawPath(
                path = path,
                color = strokeColor.copy(alpha = strokeAlpha),
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 3. Render live shape in progress
        val s = shapeStartNorm
        val e = shapeEndNorm
        if (s != null && e != null) {
            val p1 = s.toScreenOffset(pw, ph)
            val p2 = e.toScreenOffset(pw, ph)
            val left = minOf(p1.x, p2.x)
            val top = minOf(p1.y, p2.y)
            val width = (maxOf(p1.x, p2.x) - left).coerceAtLeast(1f)
            val height = (maxOf(p1.y, p2.y) - top).coerceAtLeast(1f)
            val strokePx = (strokeWidthNorm * pw).coerceAtLeast(1.5f)

            val pathEffect = when (dashStyle) {
                ShapeDashStyle.SOLID -> null
                ShapeDashStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                ShapeDashStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
            }

            when (selectedShapeType) {
                AnnotationShapeType.RECTANGLE -> {
                    drawRoundRect(
                        color = selectedColor.copy(alpha = annotationOpacity),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(8f),
                        style = Stroke(width = strokePx, pathEffect = pathEffect)
                    )
                }
                AnnotationShapeType.FILLED_RECT -> {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.95f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(6f)
                    )
                }
                AnnotationShapeType.HIGHLIGHTER_BOX -> {
                    drawRoundRect(
                        color = selectedColor.copy(alpha = 0.35f * annotationOpacity),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(6f)
                    )
                }
                AnnotationShapeType.CIRCLE -> {
                    drawOval(
                        color = selectedColor.copy(alpha = annotationOpacity),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = strokePx, pathEffect = pathEffect)
                    )
                }
                AnnotationShapeType.LINE -> {
                    drawLine(
                        color = selectedColor.copy(alpha = annotationOpacity),
                        start = p1,
                        end = p2,
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                        pathEffect = pathEffect
                    )
                }
                AnnotationShapeType.ARROW -> {
                    drawLine(
                        color = selectedColor.copy(alpha = annotationOpacity),
                        start = p1,
                        end = p2,
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                        pathEffect = pathEffect
                    )
                    val angle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                    val arrowSize = (strokePx * 3.5f).coerceIn(16f, 40f)
                    val a1 = Offset((p2.x - arrowSize * cos(angle - PI / 6)).toFloat(), (p2.y - arrowSize * sin(angle - PI / 6)).toFloat())
                    val a2 = Offset((p2.x - arrowSize * cos(angle + PI / 6)).toFloat(), (p2.y - arrowSize * sin(angle + PI / 6)).toFloat())
                    drawLine(selectedColor.copy(alpha = annotationOpacity), p2, a1, strokeWidth = strokePx, cap = StrokeCap.Round)
                    drawLine(selectedColor.copy(alpha = annotationOpacity), p2, a2, strokeWidth = strokePx, cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(
    item: PdfAnnotation,
    pageWidth: Float,
    pageHeight: Float
) {
    when (item) {
        is PdfAnnotation.InkStroke -> {
            if (item.points.size > 1) {
                val strokePx = (item.strokeWidthNorm * pageWidth).coerceAtLeast(1f)
                val alpha = if (item.isHighlighter) 0.40f * item.opacity else if (item.isSmudge) 0.85f else item.opacity
                val color = Color(item.colorArgb).copy(alpha = alpha)

                val path = Path()
                val first = item.points[0].toScreenOffset(pageWidth, pageHeight)
                path.moveTo(first.x, first.y)
                for (i in 1 until item.points.size) {
                    val prev = item.points[i - 1].toScreenOffset(pageWidth, pageHeight)
                    val curr = item.points[i].toScreenOffset(pageWidth, pageHeight)
                    path.quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                }
                val last = item.points.last().toScreenOffset(pageWidth, pageHeight)
                path.lineTo(last.x, last.y)

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        is PdfAnnotation.ShapeOverlay -> {
            val r = item.bounds.toScreenRect(pageWidth, pageHeight)
            val strokePx = (item.strokeWidthNorm * pageWidth).coerceAtLeast(1f)
            val borderCol = Color(item.borderColorArgb).copy(alpha = item.borderOpacity)

            val pathEffect = when (item.dashStyle) {
                ShapeDashStyle.SOLID -> null
                ShapeDashStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                ShapeDashStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
            }

            val fillBrush = when (item.fillType) {
                ShapeFillType.NONE -> null
                ShapeFillType.SOLID -> Brush.linearGradient(
                    listOf(Color(item.fillColor1Argb).copy(alpha = item.fillOpacity), Color(item.fillColor1Argb).copy(alpha = item.fillOpacity))
                )
                ShapeFillType.LINEAR_GRADIENT -> Brush.linearGradient(
                    listOf(Color(item.fillColor1Argb).copy(alpha = item.fillOpacity), Color(item.fillColor2Argb).copy(alpha = item.fillOpacity)),
                    start = r.topLeft,
                    end = r.bottomRight
                )
                ShapeFillType.RADIAL_GRADIENT -> Brush.radialGradient(
                    listOf(Color(item.fillColor1Argb).copy(alpha = item.fillOpacity), Color(item.fillColor2Argb).copy(alpha = item.fillOpacity)),
                    center = r.center,
                    radius = maxOf(r.width, r.height) / 2f
                )
            }

            val crPx = item.cornerRadiusNorm * pageWidth

            when (item.shapeType) {
                AnnotationShapeType.RECTANGLE -> {
                    if (fillBrush != null) {
                        drawRoundRect(brush = fillBrush, topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(crPx))
                    }
                    drawRoundRect(color = borderCol, topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(crPx), style = Stroke(strokePx, pathEffect = pathEffect))
                }
                AnnotationShapeType.FILLED_RECT -> {
                    drawRoundRect(color = Color.Black.copy(alpha = 0.96f), topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(crPx))
                }
                AnnotationShapeType.HIGHLIGHTER_BOX -> {
                    drawRoundRect(color = borderCol.copy(alpha = 0.35f * item.borderOpacity), topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(crPx))
                }
                AnnotationShapeType.CIRCLE -> {
                    if (fillBrush != null) {
                        drawOval(brush = fillBrush, topLeft = r.topLeft, size = r.size)
                    }
                    drawOval(color = borderCol, topLeft = r.topLeft, size = r.size, style = Stroke(strokePx, pathEffect = pathEffect))
                }
                AnnotationShapeType.LINE -> {
                    drawLine(borderCol, r.topLeft, r.bottomRight, strokeWidth = strokePx, cap = StrokeCap.Round, pathEffect = pathEffect)
                }
                AnnotationShapeType.ARROW -> {
                    val p1 = r.topLeft
                    val p2 = r.bottomRight
                    drawLine(borderCol, p1, p2, strokeWidth = strokePx, cap = StrokeCap.Round, pathEffect = pathEffect)
                    val angle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                    val arrowSize = (strokePx * 3.5f).coerceIn(16f, 40f)
                    val a1 = Offset((p2.x - arrowSize * cos(angle - PI / 6)).toFloat(), (p2.y - arrowSize * sin(angle - PI / 6)).toFloat())
                    val a2 = Offset((p2.x - arrowSize * cos(angle + PI / 6)).toFloat(), (p2.y - arrowSize * sin(angle + PI / 6)).toFloat())
                    drawLine(borderCol, p2, a1, strokeWidth = strokePx, cap = StrokeCap.Round)
                    drawLine(borderCol, p2, a2, strokeWidth = strokePx, cap = StrokeCap.Round)
                }
            }
        }

        is PdfAnnotation.TextNote -> {
            val pt = item.position.toScreenOffset(pageWidth, pageHeight)
            val fontPx = item.fontSizeNorm * pageHeight
            val paint = Paint().apply {
                isAntiAlias = true
                color = item.colorArgb
                textSize = fontPx
                isFakeBoldText = item.isBold
            }
            if (item.backgroundColorArgb != null) {
                val bgPaint = Paint().apply {
                    color = item.backgroundColorArgb
                    style = Paint.Style.FILL
                }
                val textW = paint.measureText(item.text)
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    pt.x - 8f, pt.y - fontPx - 4f, pt.x + textW + 8f, pt.y + 8f,
                    8f, 8f, bgPaint
                )
            }
            drawContext.canvas.nativeCanvas.drawText(item.text, pt.x, pt.y, paint)
        }

        is PdfAnnotation.SignatureStamp -> {
            val inkColor = Color(item.inkColorArgb)
            val strokePx = (0.0035f * pageWidth).coerceAtLeast(2f)

            for (stroke in item.strokes) {
                if (stroke.size > 1) {
                    val path = Path()
                    val first = stroke[0].toScreenOffset(pageWidth, pageHeight)
                    path.moveTo(first.x, first.y)
                    for (i in 1 until stroke.size) {
                        val prev = stroke[i - 1].toScreenOffset(pageWidth, pageHeight)
                        val curr = stroke[i].toScreenOffset(pageWidth, pageHeight)
                        path.quadraticBezierTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                    }
                    val last = stroke.last().toScreenOffset(pageWidth, pageHeight)
                    path.lineTo(last.x, last.y)
                    drawPath(path, color = inkColor, style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

package com.krinry.scancraft.pdf.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.krinry.scancraft.cv.ThorvgEngine
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.pdf.PdfPageSize
import com.krinry.scancraft.pdf.PdfQuality
import com.krinry.scancraft.pdf.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

enum class PdfExportMode {
    STANDARD_NATIVE_ANNOTATIONS,
    FLATTENED_TAMPER_RESISTANT
}

data class StudioExportConfig(
    val exportMode: PdfExportMode = PdfExportMode.FLATTENED_TAMPER_RESISTANT,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: PdfQuality = PdfQuality.MEDIUM,
    val watermarkText: String? = null,
    val password: String? = null
)

/**
 * High-performance PDF studio exporter powered by Android's native hardware-accelerated PdfDocument
 * and ThorVG native vector engine. Completely free of heavy third-party PDFBox dependencies.
 */
object PdfStudioExporter {

    suspend fun exportPdf(
        context: Context,
        pages: List<Page>,
        pageAnnotationsMap: Map<Long, List<PdfAnnotation>>,
        config: StudioExportConfig,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages available to export"))
        }

        try {
            outputFile.parentFile?.mkdirs()

            val pdfDoc = PdfDocument()
            var addedCount = 0

            for (page in pages) {
                val file = listOf(File(page.processedImagePath), File(page.originalImagePath))
                    .firstOrNull { it.exists() && it.length() > 0 } ?: continue

                val baseBmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val annotations = pageAnnotationsMap[page.id] ?: PdfAnnotationSerializer.deserialize(page.annotationsJson)

                // High-resolution bitmap buffer with baked vector annotations
                val renderBmp = if (annotations.isNotEmpty()) {
                    bakeAnnotationsOntoBitmap(baseBmp, annotations)
                } else {
                    baseBmp
                }

                // Compress according to chosen quality
                val qualityInt = config.quality.jpegQuality.coerceIn(20, 100)
                val stream = ByteArrayOutputStream()
                renderBmp.compress(Bitmap.CompressFormat.JPEG, qualityInt, stream)
                val compBytes = stream.toByteArray()
                val compressedBmp = BitmapFactory.decodeByteArray(compBytes, 0, compBytes.size)

                val imgW = compressedBmp.width
                val imgH = compressedBmp.height

                val dim = config.pageSize.dimension
                val targetW = dim?.widthPoints ?: imgW
                val targetH = dim?.heightPoints ?: imgH

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, ++addedCount).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas: Canvas = pdfPage.canvas

                val margin = if (config.pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
                val availableW = targetW - (margin * 2)
                val availableH = targetH - (margin * 2)

                val scale = min(availableW / imgW.toFloat(), availableH / imgH.toFloat())
                val drawW = imgW * scale
                val drawH = imgH * scale
                val drawX = margin + (availableW - drawW) / 2f
                val drawY = margin + (availableH - drawH) / 2f

                val dstRect = RectF(drawX, drawY, drawX + drawW, drawY + drawH)
                canvas.drawBitmap(compressedBmp, null, dstRect, null)

                // Optional Watermark
                val wm = config.watermarkText ?: page.watermarkText
                if (!wm.isNullOrBlank()) {
                    val paint = Paint().apply {
                        color = Color.argb(60, 160, 160, 160)
                        textSize = 36f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.save()
                    canvas.rotate(-45f, targetW / 2f, targetH / 2f)
                    canvas.drawText(wm, targetW / 2f, targetH / 2f, paint)
                    canvas.restore()
                }

                pdfDoc.finishPage(pdfPage)

                if (renderBmp != baseBmp && !renderBmp.isRecycled) renderBmp.recycle()
                if (!baseBmp.isRecycled) baseBmp.recycle()
                if (!compressedBmp.isRecycled) compressedBmp.recycle()
            }

            if (addedCount == 0) {
                pdfDoc.close()
                throw IllegalStateException("All document pages were empty or missing")
            }

            outputFile.outputStream().use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Bakes vector strokes, shapes, and signatures onto the high-res bitmap buffer
     * using ThorVG C++ vector engine for maximum precision and speed.
     */
    private fun bakeAnnotationsOntoBitmap(
        baseBmp: Bitmap,
        annotations: List<PdfAnnotation>
    ): Bitmap {
        val mutableBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true)
        val bw = mutableBmp.width.toFloat()
        val bh = mutableBmp.height.toFloat()

        for (item in annotations) {
            when (item) {
                is PdfAnnotation.InkStroke -> {
                    val strokePx = (item.strokeWidthNorm * bw).coerceAtLeast(1.5f)
                    if (item.isSmudge) {
                        if (item.points.isNotEmpty()) {
                            val minX = (item.points.minOf { it.x } * bw - strokePx).toInt().coerceAtLeast(0)
                            val minY = (item.points.minOf { it.y } * bh - strokePx).toInt().coerceAtLeast(0)
                            val maxX = (item.points.maxOf { it.x } * bw + strokePx).toInt().coerceAtMost(mutableBmp.width)
                            val maxY = (item.points.maxOf { it.y } * bh + strokePx).toInt().coerceAtMost(mutableBmp.height)
                            ThorvgEngine.applySmudgeBlur(
                                bitmap = mutableBmp,
                                left = minX,
                                top = minY,
                                right = maxX,
                                bottom = maxY,
                                radius = (strokePx * 0.7f).toInt().coerceIn(6, 32)
                            )
                        }
                    } else {
                        val flatPoints = FloatArray(item.points.size * 2)
                        for (i in item.points.indices) {
                            flatPoints[i * 2] = item.points[i].x * bw
                            flatPoints[i * 2 + 1] = item.points[i].y * bh
                        }
                        val strokeAlpha = if (item.isHighlighter) 0.45f * item.opacity else item.opacity
                        val color = androidx.compose.ui.graphics.Color(item.colorArgb).copy(alpha = strokeAlpha)
                        ThorvgEngine.renderSmoothVectorStroke(
                            bitmap = mutableBmp,
                            points = flatPoints,
                            strokeWidth = strokePx,
                            color = color,
                            opacity = item.opacity,
                            isCubicSmoothed = true
                        )
                    }
                }

                is PdfAnnotation.ShapeOverlay -> {
                    val thShapeType = when (item.shapeType) {
                        AnnotationShapeType.RECTANGLE -> ThorvgEngine.ShapeType.RECTANGLE
                        AnnotationShapeType.FILLED_RECT -> ThorvgEngine.ShapeType.FILLED_BOX
                        AnnotationShapeType.HIGHLIGHTER_BOX -> ThorvgEngine.ShapeType.HIGHLIGHTER_BOX
                        AnnotationShapeType.CIRCLE -> ThorvgEngine.ShapeType.CIRCLE
                        AnnotationShapeType.LINE -> ThorvgEngine.ShapeType.LINE
                        AnnotationShapeType.ARROW -> ThorvgEngine.ShapeType.ARROW
                    }

                    val thFillType = when (item.fillType) {
                        ShapeFillType.NONE -> ThorvgEngine.FillType.NONE
                        ShapeFillType.SOLID -> ThorvgEngine.FillType.SOLID
                        ShapeFillType.LINEAR_GRADIENT -> ThorvgEngine.FillType.LINEAR_GRADIENT
                        ShapeFillType.RADIAL_GRADIENT -> ThorvgEngine.FillType.RADIAL_GRADIENT
                    }

                    val thDashStyle = when (item.dashStyle) {
                        ShapeDashStyle.SOLID -> ThorvgEngine.DashStyle.SOLID
                        ShapeDashStyle.DASHED -> ThorvgEngine.DashStyle.DASHED
                        ShapeDashStyle.DOTTED -> ThorvgEngine.DashStyle.DOTTED
                    }

                    ThorvgEngine.renderStyledShape(
                        bitmap = mutableBmp,
                        shapeType = thShapeType,
                        x1 = item.bounds.left * bw,
                        y1 = item.bounds.top * bh,
                        x2 = item.bounds.right * bw,
                        y2 = item.bounds.bottom * bh,
                        strokeWidth = (item.strokeWidthNorm * bw).coerceAtLeast(1.5f),
                        borderColor = androidx.compose.ui.graphics.Color(item.borderColorArgb),
                        borderOpacity = item.borderOpacity,
                        fillType = thFillType,
                        fillColor1 = androidx.compose.ui.graphics.Color(item.fillColor1Argb),
                        fillColor2 = androidx.compose.ui.graphics.Color(item.fillColor2Argb),
                        fillOpacity = item.fillOpacity,
                        dashStyle = thDashStyle,
                        cornerRadius = item.cornerRadiusNorm * bw
                    )
                }

                is PdfAnnotation.TextNote -> {
                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = item.colorArgb
                        textSize = item.fontSizeNorm * bh
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val canvas = Canvas(mutableBmp)
                    canvas.drawText(item.text, item.position.x * bw, item.position.y * bh, paint)
                }

                is PdfAnnotation.SignatureStamp -> {
                    for (stroke in item.strokes) {
                        if (stroke.size > 1) {
                            val flatPoints = FloatArray(stroke.size * 2)
                            for (i in stroke.indices) {
                                flatPoints[i * 2] = stroke[i].x * bw
                                flatPoints[i * 2 + 1] = stroke[i].y * bh
                            }
                            ThorvgEngine.renderSmoothVectorStroke(
                                bitmap = mutableBmp,
                                points = flatPoints,
                                strokeWidth = (0.0035f * bw).coerceAtLeast(2f),
                                color = androidx.compose.ui.graphics.Color(item.inkColorArgb),
                                opacity = 1.0f,
                                isCubicSmoothed = true
                            )
                        }
                    }
                }
            }
        }
        return mutableBmp
    }
}

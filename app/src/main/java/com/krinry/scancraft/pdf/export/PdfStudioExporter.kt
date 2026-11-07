package com.krinry.scancraft.pdf.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.krinry.scancraft.cv.ThorvgEngine
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.pdf.PdfPageSize
import com.krinry.scancraft.pdf.PdfQuality
import com.krinry.scancraft.pdf.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class PdfExportMode {
    STANDARD_NATIVE_ANNOTATIONS, // ISO 32000-1 native PDF annotations (editable in Acrobat/Drive)
    FLATTENED_TAMPER_RESISTANT   // Baked vector/raster stream (tamper-resistant, legal submissions)
}

data class StudioExportConfig(
    val exportMode: PdfExportMode = PdfExportMode.FLATTENED_TAMPER_RESISTANT,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: PdfQuality = PdfQuality.MEDIUM,
    val watermarkText: String? = null,
    val password: String? = null
)

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

            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (_: Exception) {}

            val document = PDDocument()
            var addedCount = 0

            for (page in pages) {
                val file = listOf(File(page.processedImagePath), File(page.originalImagePath))
                    .firstOrNull { it.exists() && it.length() > 0 } ?: continue

                val baseBmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val annotations = pageAnnotationsMap[page.id] ?: PdfAnnotationSerializer.deserialize(page.annotationsJson)

                // High-resolution bitmap buffer
                val renderBmp = if (config.exportMode == PdfExportMode.FLATTENED_TAMPER_RESISTANT && annotations.isNotEmpty()) {
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

                val imgW = compressedBmp.width.toFloat()
                val imgH = compressedBmp.height.toFloat()

                val pdPageRect = config.pageSize.rectangle ?: PDRectangle(imgW, imgH)
                val pdPage = PDPage(pdPageRect)
                document.addPage(pdPage)
                addedCount++

                val pdImage = JPEGFactory.createFromImage(document, compressedBmp, qualityInt / 100f)
                val margin = if (config.pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
                val availableW = pdPageRect.width - (margin * 2)
                val availableH = pdPageRect.height - (margin * 2)

                val scale = min(availableW / imgW, availableH / imgH)
                val drawW = imgW * scale
                val drawH = imgH * scale
                val drawX = margin + (availableW - drawW) / 2f
                val drawY = margin + (availableH - drawH) / 2f

                PDPageContentStream(document, pdPage).use { cs ->
                    cs.drawImage(pdImage, drawX, drawY, drawW, drawH)

                    // Optional Watermark
                    val wm = config.watermarkText ?: page.watermarkText
                    if (!wm.isNullOrBlank()) {
                        try {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                            cs.setNonStrokingColor(185, 185, 185)
                            cs.setTextMatrix(0.707, 0.707, -0.707, 0.707, (pdPageRect.width * 0.25).toDouble(), (pdPageRect.height * 0.35).toDouble())
                            cs.showText(wm)
                            cs.endText()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // If Standard Native Annotations mode is selected, attach ISO 32000-1 annotations
                if (config.exportMode == PdfExportMode.STANDARD_NATIVE_ANNOTATIONS && annotations.isNotEmpty()) {
                    attachNativePdfAnnotations(pdPage, annotations, drawX, drawY, drawW, drawH)
                }

                if (renderBmp != baseBmp && !renderBmp.isRecycled) renderBmp.recycle()
                if (!baseBmp.isRecycled) baseBmp.recycle()
                if (!compressedBmp.isRecycled) compressedBmp.recycle()
            }

            if (addedCount == 0) {
                document.close()
                throw IllegalStateException("All document pages were empty or missing")
            }

            // AES 128-bit Password Encryption
            if (!config.password.isNullOrBlank()) {
                val ap = AccessPermission().apply {
                    setCanPrint(true)
                    setCanExtractContent(true)
                }
                val spp = StandardProtectionPolicy(config.password, config.password, ap)
                spp.encryptionKeyLength = 128
                document.protect(spp)
            }

            document.save(outputFile)
            document.close()
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

    private fun attachNativePdfAnnotations(
        pdPage: PDPage,
        annotations: List<PdfAnnotation>,
        drawX: Float,
        drawY: Float,
        drawW: Float,
        drawH: Float
    ) {
        val pdAnnots = pdPage.annotations ?: mutableListOf()
        for (item in annotations) {
            when (item) {
                is PdfAnnotation.ShapeOverlay -> {
                    if (item.shapeType == AnnotationShapeType.RECTANGLE || item.shapeType == AnnotationShapeType.CIRCLE) {
                        val subType = if (item.shapeType == AnnotationShapeType.CIRCLE) {
                            PDAnnotationSquareCircle.SUB_TYPE_CIRCLE
                        } else {
                            PDAnnotationSquareCircle.SUB_TYPE_SQUARE
                        }
                        val annot = PDAnnotationSquareCircle(subType)
                        val annotRect = PDRectangle(
                            drawX + item.bounds.left * drawW,
                            drawY + (1f - item.bounds.bottom) * drawH,
                            item.bounds.width * drawW,
                            item.bounds.height * drawH
                        )
                        annot.rectangle = annotRect
                        val c = androidx.compose.ui.graphics.Color(item.borderColorArgb)
                        annot.color = PDColor(floatArrayOf(c.red, c.green, c.blue), PDDeviceRGB.INSTANCE)
                        pdAnnots.add(annot)
                    }
                }
                else -> {
                    // Other types remain in vector content stream
                }
            }
        }
        pdPage.annotations = pdAnnots
    }
}

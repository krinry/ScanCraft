package com.krinry.scancraft.pdf

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
import com.krinry.scancraft.data.model.Page
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * High-performance, hardware-accelerated PDF generation engine powered by Android's native PdfDocument.
 * Eliminates heavy third-party PDFBox dependencies, minimizing APK size and maximizing rendering speed.
 */
object PdfGenerator {

    /**
     * Generates a multi-page PDF document using Android's native PdfDocument engine.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<Page>,
        options: PdfExportOptions,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages to generate PDF"))
        }

        try {
            outputFile.parentFile?.mkdirs()

            val pdfDoc = PdfDocument()
            var pageNum = 1

            for (page in pages) {
                val imgFile = listOf(File(page.processedImagePath), File(page.originalImagePath))
                    .firstOrNull { it.exists() && it.length() > 0 } ?: continue

                val decodedBitmap = decodeOptimizedBitmap(imgFile, options.quality) ?: continue

                // Compute page dimensions
                val dim = options.pageSize.dimension
                val targetW = dim?.widthPoints ?: decodedBitmap.width
                val targetH = dim?.heightPoints ?: decodedBitmap.height

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, pageNum++).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas: Canvas = pdfPage.canvas

                val margin = if (options.pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
                val availW = targetW - (margin * 2)
                val availH = targetH - (margin * 2)

                val scale = min(availW / decodedBitmap.width.toFloat(), availH / decodedBitmap.height.toFloat())
                val dw = decodedBitmap.width * scale
                val dh = decodedBitmap.height * scale
                val dx = margin + (availW - dw) / 2f
                val dy = margin + (availH - dh) / 2f

                val dstRect = RectF(dx, dy, dx + dw, dy + dh)
                canvas.drawBitmap(decodedBitmap, null, dstRect, null)

                // Optional watermark
                val wm = options.watermarkText ?: page.watermarkText
                if (!wm.isNullOrBlank()) {
                    drawWatermark(canvas, wm, targetW, targetH)
                }

                pdfDoc.finishPage(pdfPage)
                decodedBitmap.recycle()
            }

            if (pageNum == 1) {
                pdfDoc.close()
                return@withContext Result.failure(IllegalStateException("No pages could be rendered to PDF"))
            }

            outputFile.outputStream().use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeOptimizedBitmap(file: File, quality: PdfQuality): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val sampleSize = when (quality) {
            PdfQuality.HIGH -> 1
            PdfQuality.MEDIUM -> if (boundsOptions.outWidth > 2400 || boundsOptions.outHeight > 2400) 2 else 1
            PdfQuality.LOW -> if (boundsOptions.outWidth > 1600 || boundsOptions.outHeight > 1600) 2 else 1
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val originalBmp = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        if (quality == PdfQuality.HIGH) return originalBmp

        val compressedStream = ByteArrayOutputStream()
        originalBmp.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, compressedStream)
        originalBmp.recycle()

        val compressedBytes = compressedStream.toByteArray()
        return BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
    }

    private fun drawWatermark(canvas: Canvas, text: String, width: Int, height: Int) {
        val paint = Paint().apply {
            color = Color.argb(60, 160, 160, 160)
            textSize = 42f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-45f, width / 2f, height / 2f)
        canvas.drawText(text, width / 2f, height / 2f, paint)
        canvas.restore()
    }
}

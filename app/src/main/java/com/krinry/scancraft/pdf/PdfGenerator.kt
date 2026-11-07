package com.krinry.scancraft.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.krinry.scancraft.data.model.Page
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

object PdfGenerator {

    /**
     * Generates a multi-page PDF document.
     * Uses PDFBox with AES 128-bit encryption support, and automatically falls back to
     * Android's native hardware-accelerated PdfDocument engine if needed.
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
            // Ensure parent directory exists
            outputFile.parentFile?.mkdirs()

            // Try PDFBox engine
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (_: Exception) {}

            val document = PDDocument()
            var addedPages = 0

            for (page in pages) {
                val imgFile = listOf(File(page.processedImagePath), File(page.originalImagePath))
                    .firstOrNull { it.exists() && it.length() > 0 } ?: continue

                val originalBitmap = BitmapFactory.decodeFile(imgFile.absolutePath) ?: continue

                // Compression based on quality setting
                val quality = options.quality.jpegQuality.coerceIn(20, 100)
                val stream = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val compressedBytes = stream.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                val imgW = compressedBitmap.width.toFloat()
                val imgH = compressedBitmap.height.toFloat()

                val pdPageRect = options.pageSize.rectangle ?: PDRectangle(imgW, imgH)
                val pdPage = PDPage(pdPageRect)
                document.addPage(pdPage)
                addedPages++

                val pdImage = JPEGFactory.createFromImage(document, compressedBitmap, quality / 100f)

                val margin = if (options.pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
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
                    val watermark = options.watermarkText ?: page.watermarkText
                    if (!watermark.isNullOrBlank()) {
                        try {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                            cs.setNonStrokingColor(180, 180, 180)
                            cs.setTextMatrix(
                                0.707, 0.707, -0.707, 0.707,
                                (pdPageRect.width * 0.25).toDouble(),
                                (pdPageRect.height * 0.35).toDouble()
                            )
                            cs.showText(watermark)
                            cs.endText()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            if (addedPages == 0) {
                document.close()
                throw IllegalStateException("All page image files were missing or unreadable")
            }

            // Optional Password Protection (AES 128-bit)
            if (!options.password.isNullOrBlank()) {
                val ap = AccessPermission().apply {
                    setCanPrint(true)
                    setCanExtractContent(true)
                }
                val spp = StandardProtectionPolicy(options.password, options.password, ap)
                spp.encryptionKeyLength = 128
                document.protect(spp)
            }

            document.save(outputFile)
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail-closed: If password encryption was requested and failed, never fall back to unencrypted native PDF
            if (!options.password.isNullOrBlank()) {
                Result.failure(IllegalStateException("Failed to generate password-protected PDF: ${e.message}", e))
            } else {
                // Robust Native Fallback using Android OS PdfDocument for unencrypted exports
                try {
                    generateNativePdf(pages, options, outputFile)
                    Result.success(outputFile)
                } catch (fallbackError: Exception) {
                    Result.failure(fallbackError)
                }
            }
        }
    }

    private fun generateNativePdf(
        pages: List<Page>,
        options: PdfExportOptions,
        outputFile: File
    ) {
        val pdfDoc = PdfDocument()
        var pageNum = 1

        for (page in pages) {
            val imgFile = listOf(File(page.processedImagePath), File(page.originalImagePath))
                .firstOrNull { it.exists() && it.length() > 0 } ?: continue
            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath) ?: continue

            // A4 dimensions in PostScript points: 595 x 842 pt
            val pageW = if (options.pageSize == PdfPageSize.LETTER) 612 else 595
            val pageH = if (options.pageSize == PdfPageSize.LETTER) 792 else 842

            val targetW = if (options.pageSize == PdfPageSize.FIT_IMAGE) bmp.width else pageW
            val targetH = if (options.pageSize == PdfPageSize.FIT_IMAGE) bmp.height else pageH

            val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, pageNum++).create()
            val pdfPage = pdfDoc.startPage(pageInfo)
            val canvas: Canvas = pdfPage.canvas

            val margin = if (options.pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
            val availW = targetW - (margin * 2)
            val availH = targetH - (margin * 2)

            val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = margin + (availW - dw) / 2f
            val dy = margin + (availH - dh) / 2f

            val dstRect = RectF(dx, dy, dx + dw, dy + dh)
            canvas.drawBitmap(bmp, null, dstRect, null)

            // Optional watermark
            val wm = options.watermarkText ?: page.watermarkText
            if (!wm.isNullOrBlank()) {
                val paint = Paint().apply {
                    color = Color.argb(60, 160, 160, 160)
                    textSize = 42f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.save()
                canvas.rotate(-45f, targetW / 2f, targetH / 2f)
                canvas.drawText(wm, targetW / 2f, targetH / 2f, paint)
                canvas.restore()
            }

            pdfDoc.finishPage(pdfPage)
            bmp.recycle()
        }

        if (pageNum == 1) {
            pdfDoc.close()
            throw IllegalStateException("No pages could be rendered to PDF")
        }

        outputFile.outputStream().use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }
}

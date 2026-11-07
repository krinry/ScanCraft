package com.krinry.scancraft.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.krinry.scancraft.cv.NativeScannerEngine
import com.krinry.scancraft.cv.OcrEngine
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.camera.BatchCaptureItem
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * High-performance deferred background processing pipeline for Batch Burst captures.
 * Uses Semaphore(2) on Dispatchers.Default to prevent OutOfMemoryError (OOM) by strictly
 * limiting concurrent high-resolution bitmap transformations.
 */
class BatchWarpProcessor(
    private val context: Context,
    private val repository: ScannerRepository
) {

    suspend fun processBatch(
        targetDocId: Long,
        items: List<BatchCaptureItem>,
        selectedEnhanceMode: EnhanceMode = EnhanceMode.MAGIC_COLOR,
        pageFilters: Map<Long, EnhanceMode> = emptyMap(),
        brightness: Int = 0,
        contrast: Int = 0,
        pageAdjustments: Map<Long, Pair<Int, Int>> = emptyMap(),
        customTitle: String? = null,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.Default) {
        val total = items.size
        if (total == 0) return@withContext targetDocId

        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(2)

        val docId = if (targetDocId <= 0L) {
            repository.createNewDocument(title = customTitle)
        } else {
            if (customTitle != null) {
                repository.renameDocument(targetDocId, customTitle)
            }
            targetDocId
        }

        coroutineScope {
            items.forEach { item ->
                launch {
                    semaphore.withPermit {
                        val effectiveMode = pageFilters[item.id] ?: selectedEnhanceMode
                        val (itemBrightness, itemContrast) = pageAdjustments[item.id] ?: (brightness to contrast)
                        processSingleItem(
                            docId = docId,
                            item = item,
                            enhanceMode = effectiveMode,
                            brightness = itemBrightness,
                            contrast = itemContrast
                        )
                        val done = completedCount.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            onProgress(done, total)
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            onProgress(total, total)
        }

        docId
    }

    private suspend fun processSingleItem(
        docId: Long,
        item: BatchCaptureItem,
        enhanceMode: EnhanceMode,
        brightness: Int = 0,
        contrast: Int = 0
    ) = withContext(Dispatchers.Default) {
        val file = File(item.rawFilePath)
        if (!file.exists()) return@withContext

        // 1. Decode raw JPEG safely
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return@withContext

        var sampleSize = 1
        while (rawW / (sampleSize * 2) >= 2400 && rawH / (sampleSize * 2) >= 2400) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var rawBmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@withContext

        // 2. Apply manual rotation if requested
        if (item.rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
            if (rotated != rawBmp) {
                rawBmp.recycle()
                rawBmp = rotated
            }
        }

        // 3. Document Quad detection (deferred auto-crop if user did not manually crop)
        val quad = if (item.cropQuad != null) {
            val fullRotW = if (item.rotationDegrees % 180 == 0) rawW.toFloat() else rawH.toFloat()
            val fullRotH = if (item.rotationDegrees % 180 == 0) rawH.toFloat() else rawW.toFloat()
            val sx = rawBmp.width.toFloat() / fullRotW
            val sy = rawBmp.height.toFloat() / fullRotH
            if (kotlin.math.abs(sx - 1f) > 0.001f || kotlin.math.abs(sy - 1f) > 0.001f) {
                com.krinry.scancraft.cv.DocumentQuadrilateral(
                    topLeft = com.krinry.scancraft.cv.PointF(item.cropQuad!!.topLeft.x * sx, item.cropQuad!!.topLeft.y * sy),
                    topRight = com.krinry.scancraft.cv.PointF(item.cropQuad!!.topRight.x * sx, item.cropQuad!!.topRight.y * sy),
                    bottomRight = com.krinry.scancraft.cv.PointF(item.cropQuad!!.bottomRight.x * sx, item.cropQuad!!.bottomRight.y * sy),
                    bottomLeft = com.krinry.scancraft.cv.PointF(item.cropQuad!!.bottomLeft.x * sx, item.cropQuad!!.bottomLeft.y * sy)
                )
            } else {
                item.cropQuad!!
            }
        } else {
            NativeScannerEngine.detectDocumentBounds(rawBmp)
        }

        // 4. Perspective Warp
        val topW = quad.topLeft.distanceTo(quad.topRight)
        val botW = quad.bottomLeft.distanceTo(quad.bottomRight)
        val leftH = quad.topLeft.distanceTo(quad.bottomLeft)
        val rightH = quad.topRight.distanceTo(quad.bottomRight)
        val targetW = max(topW, botW).toInt().coerceAtLeast(300)
        val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)

        val warpedBmp = NativeScannerEngine.warpDocument(rawBmp, quad, targetW, targetH)

        // 5. Apply Enhancement Filter (e.g. Magic Color) and Tone Fine-Tuning
        NativeScannerEngine.applyEnhancement(warpedBmp, enhanceMode.id)
        if (brightness != 0 || contrast != 0) {
            NativeScannerEngine.adjustBrightnessContrast(warpedBmp, brightness, contrast)
        }

        // 6. Save Page into Room & filesDir/scans/
        val borderStr = quad.toFloatArray().joinToString(",")
        val savedPage = repository.savePage(
            documentId = docId,
            processedBitmap = warpedBmp,
            originalBitmap = rawBmp,
            cropBorderJson = borderStr,
            enhanceModeId = enhanceMode.id
        )

        // 7. Background Offline OCR
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = OcrEngine.recognizeText(warpedBmp)
                if (text.isNotBlank()) {
                    repository.updatePage(savedPage.copy(ocrText = text))
                }
            } catch (e: Exception) {
                // Ignore OCR background failures
            }
        }

        // 8. Explicit bitmap recycling to eliminate OOM risks
        warpedBmp.recycle()
        rawBmp.recycle()

        // 9. Clean up temporary cache burst file
        try {
            file.delete()
        } catch (e: Exception) { /* Ignore */ }
    }
}

package com.krinry.scancraft.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.camera.BatchWarpProcessor
import com.krinry.scancraft.camera.CaptureMode
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.PointF
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.data.repository.SettingsManager
import android.media.MediaActionSound
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

data class BatchCaptureItem(
    val id: Long = System.nanoTime(),
    val rawFilePath: String,
    val thumbnailBitmap: Bitmap,
    var rotationDegrees: Int = 0,
    var cropQuad: DocumentQuadrilateral? = null
)

enum class QualityMode(val label: String, val description: String) {
    SD("SD", "Standard 1080p, faster capture"),
    HD("HD", "High Definition balanced"),
    UHD("UHD", "Maximum sensor resolution")
}

enum class StabilityState(val message: String) {
    SEARCHING("ALIGN DOCUMENT"),
    MOVE_CLOSER("MOVE CLOSER"),
    STABILIZING("HOLD STEADY"),
    STEADY_LOCKED("AUTO CAPTURING...")
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentMode = MutableStateFlow(CaptureMode.DOCUMENT)
    val currentMode: StateFlow<CaptureMode> = _currentMode

    private val _detectedQuad = MutableStateFlow<DocumentQuadrilateral?>(null)
    val detectedQuad: StateFlow<DocumentQuadrilateral?> = _detectedQuad

    private val _previewDimens = MutableStateFlow(Pair(0, 0))
    val previewDimens: StateFlow<Pair<Int, Int>> = _previewDimens

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode

    private val _qualityMode = MutableStateFlow(QualityMode.HD)
    val qualityMode: StateFlow<QualityMode> = _qualityMode

    private val _selectedFilter = MutableStateFlow(1) // 1 = Magic Color (default)
    val selectedFilter: StateFlow<Int> = _selectedFilter

    private val settingsManager = SettingsManager.getInstance(application)

    private val _cropAfterScan = MutableStateFlow(false)
    val cropAfterScan: StateFlow<Boolean> = _cropAfterScan

    private val _autoCropEnabled = MutableStateFlow(settingsManager.autoCropEnabled.value)
    val autoCropEnabled: StateFlow<Boolean> = _autoCropEnabled

    private val _batchCount = MutableStateFlow(0)
    val batchCount: StateFlow<Int> = _batchCount

    private val _batchQueue = MutableStateFlow<List<BatchCaptureItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchCaptureItem>> = _batchQueue.asStateFlow()

    private val _stabilityState = MutableStateFlow(StabilityState.SEARCHING)
    val stabilityState: StateFlow<StabilityState> = _stabilityState

    private val _autoCaptureMode = MutableStateFlow(false)
    val autoCaptureMode: StateFlow<Boolean> = _autoCaptureMode

    private val _triggerAutoCapture = MutableStateFlow<Long?>(null)
    val triggerAutoCapture: StateFlow<Long?> = _triggerAutoCapture

    private var steadyFrameCount = 0
    private var lastStableTimestamp = 0L
    private var consecutiveMissingFrames = 0
    private val frameCache = java.util.ArrayDeque<DocumentQuadrilateral>(5)

    fun toggleAutoCapture() {
        _autoCaptureMode.value = !_autoCaptureMode.value
    }

    fun clearAutoCaptureTrigger() {
        _triggerAutoCapture.value = null
    }

    fun setMode(mode: CaptureMode) {
        _currentMode.value = mode
        _detectedQuad.value = null
        _stabilityState.value = StabilityState.SEARCHING
        steadyFrameCount = 0
        frameCache.clear()
    }

    fun onDocumentDetected(quad: DocumentQuadrilateral, width: Int, height: Int) {
        val isReal = !quad.isDefaultFallback(width.toFloat(), height.toFloat())
        if (!isReal) {
            steadyFrameCount = 0
            frameCache.clear()
            _stabilityState.value = StabilityState.SEARCHING
            consecutiveMissingFrames++
            if (consecutiveMissingFrames >= 4) {
                _detectedQuad.value = null
            }
            return
        }
        consecutiveMissingFrames = 0

        // Document size proximity check
        val minX = minOf(quad.topLeft.x, quad.bottomLeft.x)
        val maxX = maxOf(quad.topRight.x, quad.bottomRight.x)
        val minY = minOf(quad.topLeft.y, quad.topRight.y)
        val maxY = maxOf(quad.bottomLeft.y, quad.bottomRight.y)
        val docW = maxX - minX
        val docH = maxY - minY

        val prev = _detectedQuad.value
        val smoothed = if (prev == null || _previewDimens.value != Pair(width, height)) {
            steadyFrameCount = 0
            frameCache.clear()
            _stabilityState.value = StabilityState.SEARCHING
            quad
        } else {
            val alpha = 0.32f // Responsive low-pass smoothing for natural corner tracking
            fun lerp(p1: PointF, p2: PointF): PointF =
                PointF(p1.x + alpha * (p2.x - p1.x), p1.y + alpha * (p2.y - p1.y))

            val smoothedQuad = DocumentQuadrilateral(
                topLeft = lerp(prev.topLeft, quad.topLeft),
                topRight = lerp(prev.topRight, quad.topRight),
                bottomRight = lerp(prev.bottomRight, quad.bottomRight),
                bottomLeft = lerp(prev.bottomLeft, quad.bottomLeft)
            )

            // Cache sliding window of 5 frames
            frameCache.addLast(smoothedQuad)
            if (frameCache.size > 5) {
                frameCache.removeFirst()
            }

            if (docW < width * 0.35f || docH < height * 0.35f) {
                // Page occupies less than 35% of frame -> recommend moving closer
                _stabilityState.value = StabilityState.MOVE_CLOSER
                steadyFrameCount = 0
            } else if (frameCache.size >= 4) {
                // Calculate max corner variance across the 5 cached frames
                val avgTLx = frameCache.map { it.topLeft.x }.average().toFloat()
                val avgTLy = frameCache.map { it.topLeft.y }.average().toFloat()
                val avgTRx = frameCache.map { it.topRight.x }.average().toFloat()
                val avgTRy = frameCache.map { it.topRight.y }.average().toFloat()
                val avgBRx = frameCache.map { it.bottomRight.x }.average().toFloat()
                val avgBRy = frameCache.map { it.bottomRight.y }.average().toFloat()
                val avgBLx = frameCache.map { it.bottomLeft.x }.average().toFloat()
                val avgBLy = frameCache.map { it.bottomLeft.y }.average().toFloat()

                var maxVariance = 0f
                for (f in frameCache) {
                    val d1 = hypot(f.topLeft.x - avgTLx, f.topLeft.y - avgTLy)
                    val d2 = hypot(f.topRight.x - avgTRx, f.topRight.y - avgTRy)
                    val d3 = hypot(f.bottomRight.x - avgBRx, f.bottomRight.y - avgBRy)
                    val d4 = hypot(f.bottomLeft.x - avgBLx, f.bottomLeft.y - avgBLy)
                    maxVariance = maxOf(maxVariance, d1, d2, d3, d4)
                }

                if (maxVariance < 14f) {
                    steadyFrameCount++
                    if (steadyFrameCount >= 5) {
                        if (_stabilityState.value != StabilityState.STEADY_LOCKED) {
                            _stabilityState.value = StabilityState.STEADY_LOCKED
                            lastStableTimestamp = System.currentTimeMillis()
                        } else if (_autoCaptureMode.value && System.currentTimeMillis() - lastStableTimestamp >= 700L) {
                            // Document has been steady for > 700ms -> Trigger auto shutter!
                            _triggerAutoCapture.value = System.currentTimeMillis()
                            steadyFrameCount = 0
                            lastStableTimestamp = System.currentTimeMillis() + 2500L // Cool-down
                        }
                    } else {
                        _stabilityState.value = StabilityState.STABILIZING
                    }
                } else {
                    steadyFrameCount = 0
                    _stabilityState.value = StabilityState.SEARCHING
                }
            } else {
                steadyFrameCount = 0
                _stabilityState.value = StabilityState.SEARCHING
            }

            smoothedQuad
        }
        _detectedQuad.value = smoothed
        _previewDimens.value = Pair(width, height)
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun cycleQuality(): QualityMode {
        val next = when (_qualityMode.value) {
            QualityMode.SD -> QualityMode.HD
            QualityMode.HD -> QualityMode.UHD
            QualityMode.UHD -> QualityMode.SD
        }
        _qualityMode.value = next
        return next
    }

    fun setQualityMode(mode: QualityMode) {
        _qualityMode.value = mode
    }

    fun setSelectedFilter(filterId: Int) {
        _selectedFilter.value = filterId
    }

    fun setCropAfterScan(enabled: Boolean) {
        _cropAfterScan.value = enabled
    }

    fun setAutoCropEnabled(enabled: Boolean) {
        _autoCropEnabled.value = enabled
        settingsManager.setAutoCropEnabled(enabled)
    }

    fun addBatchItem(item: BatchCaptureItem) {
        val updated = _batchQueue.value + item
        _batchQueue.value = updated
        _batchCount.value = updated.size
    }

    fun captureBatchPhoto(
        imageCapture: ImageCapture,
        onSuccess: (BatchCaptureItem) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val context = getApplication<Application>()
        if (settingsManager.shutterSoundEnabled.value) {
            try {
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            } catch (ignored: Exception) {}
        }
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val rotation = image.imageInfo.rotationDegrees

                            // Decode lightweight thumbnail for live filmstrip queue
                            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                            var sampleSize = 1
                            while (boundsOpts.outWidth / (sampleSize * 2) >= 320 && boundsOpts.outHeight / (sampleSize * 2) >= 320) {
                                sampleSize *= 2
                            }
                            val thumbOpts = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            var thumbBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, thumbOpts)
                            if (rotation != 0 && thumbBmp != null) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                val rotatedThumb = Bitmap.createBitmap(thumbBmp, 0, 0, thumbBmp.width, thumbBmp.height, matrix, true)
                                if (rotatedThumb != thumbBmp) {
                                    thumbBmp.recycle()
                                    thumbBmp = rotatedThumb
                                }
                            }

                            // Write raw JPEG to cacheDir/temp_scans/burst_...jpg
                            val cacheDir = File(context.cacheDir, "temp_scans").apply { if (!exists()) mkdirs() }
                            val burstFile = File(cacheDir, "burst_${System.currentTimeMillis()}_${_batchQueue.value.size + 1}.jpg")

                            if (rotation == 0) {
                                FileOutputStream(burstFile).use { it.write(bytes) }
                            } else {
                                val fullBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (fullBmp != null) {
                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                    val rotatedFull = Bitmap.createBitmap(fullBmp, 0, 0, fullBmp.width, fullBmp.height, matrix, true)
                                    FileOutputStream(burstFile).use { out ->
                                        rotatedFull.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    if (rotatedFull != fullBmp) fullBmp.recycle()
                                    rotatedFull.recycle()
                                } else {
                                    FileOutputStream(burstFile).use { it.write(bytes) }
                                }
                            }

                            image.close()

                            val newItem = BatchCaptureItem(
                                id = System.nanoTime(),
                                rawFilePath = burstFile.absolutePath,
                                thumbnailBitmap = thumbBmp ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                                rotationDegrees = 0,
                                cropQuad = null
                            )

                            withContext(Dispatchers.Main) {
                                val updated = _batchQueue.value + newItem
                                _batchQueue.value = updated
                                _batchCount.value = updated.size
                                onSuccess(newItem)
                            }
                        } catch (e: Exception) {
                            image.close()
                            withContext(Dispatchers.Main) {
                                onError(e)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun removeBatchItem(id: Long) {
        val current = _batchQueue.value
        val itemToRemove = current.firstOrNull { it.id == id }
        if (itemToRemove != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    File(itemToRemove.rawFilePath).delete()
                } catch (e: Exception) { /* Ignore */ }
            }
            val updated = current.filter { it.id != id }
            _batchQueue.value = updated
            _batchCount.value = updated.size
        }
    }

    fun rotateBatchItem(id: Long, clockwise: Boolean = true) {
        val delta = if (clockwise) 90 else -90
        _batchQueue.value = _batchQueue.value.map { item ->
            if (item.id == id) {
                val newAngle = (item.rotationDegrees + delta + 360) % 360
                val matrix = Matrix().apply { postRotate(delta.toFloat()) }
                val rotatedThumb = Bitmap.createBitmap(
                    item.thumbnailBitmap,
                    0, 0,
                    item.thumbnailBitmap.width,
                    item.thumbnailBitmap.height,
                    matrix,
                    true
                )
                item.copy(
                    rotationDegrees = newAngle,
                    thumbnailBitmap = rotatedThumb
                )
            } else {
                item
            }
        }
    }

    fun updateBatchItemCrop(id: Long, quad: DocumentQuadrilateral) {
        _batchQueue.value = _batchQueue.value.map { item ->
            if (item.id == id) {
                item.copy(cropQuad = quad)
            } else {
                item
            }
        }
    }

    fun clearBatch() {
        val current = _batchQueue.value
        viewModelScope.launch(Dispatchers.IO) {
            for (item in current) {
                try {
                    File(item.rawFilePath).delete()
                } catch (e: Exception) { /* Ignore */ }
            }
        }
        _batchQueue.value = emptyList()
        _batchCount.value = 0
    }

    fun capturePhoto(
        imageCapture: ImageCapture,
        onPhotoCaptured: (savedPath: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val context = getApplication<Application>()
        if (settingsManager.shutterSoundEnabled.value) {
            try {
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            } catch (ignored: Exception) {}
        }
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rotationDegrees = image.imageInfo.rotationDegrees

                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (rotationDegrees != 0 && bitmap != null) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }

                    // Save raw photo to cache file
                    val cacheDir = File(context.cacheDir, "temp_scans").apply { if (!exists()) mkdirs() }
                    val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                    if (bitmap != null) {
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    } else {
                        FileOutputStream(tempFile).use { out ->
                            out.write(bytes)
                        }
                    }

                    image.close()

                    if (_currentMode.value == CaptureMode.DOCUMENT) {
                        _batchCount.value += 1
                    }

                    onPhotoCaptured(tempFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun addDocumentItemFromFile(
        filePath: String,
        onSuccess: (BatchCaptureItem) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(filePath, boundsOpts)
                var sampleSize = 1
                while (boundsOpts.outWidth / (sampleSize * 2) >= 320 && boundsOpts.outHeight / (sampleSize * 2) >= 320) {
                    sampleSize *= 2
                }
                val thumbOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val thumbBmp = BitmapFactory.decodeFile(filePath, thumbOpts)

                val newItem = BatchCaptureItem(
                    id = System.nanoTime(),
                    rawFilePath = filePath,
                    thumbnailBitmap = thumbBmp ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                    rotationDegrees = 0,
                    cropQuad = null
                )

                withContext(Dispatchers.Main) {
                    val updated = _batchQueue.value + newItem
                    _batchQueue.value = updated
                    _batchCount.value = updated.size
                    onSuccess(newItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun saveBatchAsDraft(docId: Long = -1L): Long = withContext(Dispatchers.IO) {
        val items = _batchQueue.value
        if (items.isEmpty()) return@withContext -1L
        val context = getApplication<Application>()
        val app = context as ScanCraftApp
        val repo = ScannerRepository(
            context = context,
            documentDao = app.database.documentDao(),
            pageDao = app.database.pageDao(),
            folderDao = app.database.folderDao()
        )
        val processor = BatchWarpProcessor(context, repo)
        val draftTitle = "Draft " + SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        val savedId = processor.processBatch(
            targetDocId = if (docId > 0) docId else -1L,
            items = items,
            selectedEnhanceMode = EnhanceMode.fromId(_selectedFilter.value),
            pageFilters = emptyMap(),
            brightness = 0,
            contrast = 0,
            pageAdjustments = emptyMap(),
            customTitle = draftTitle,
            onProgress = { _: Int, _: Int -> }
        )
        // Mark tags with "draft"
        if (savedId > 0) {
            try {
                val doc = repo.getDocumentById(savedId)
                if (doc != null) {
                    repo.updateDocument(doc.copy(tags = "draft"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _batchQueue.value = emptyList()
        _batchCount.value = 0
        savedId
    }
}

package com.krinry.scancraft.ui.filter

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.NativeScannerEngine
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.data.repository.SettingsManager
import com.krinry.scancraft.cv.OcrEngine
import java.io.File
import kotlin.math.max

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScanCraftApp).database
    private val repository = ScannerRepository(application, db.documentDao(), db.pageDao(), db.folderDao())

    private var rawBitmap: Bitmap? = null
    private var warpedBaseBitmap: Bitmap? = null
    private var previewBaseBitmap: Bitmap? = null
    private var pipelineJob: Job? = null

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap

    private val _selectedMode = MutableStateFlow(EnhanceMode.MAGIC_COLOR)
    val selectedMode: StateFlow<EnhanceMode> = _selectedMode

    private val _brightness = MutableStateFlow(0)
    val brightness: StateFlow<Int> = _brightness

    private val _contrast = MutableStateFlow(0)
    val contrast: StateFlow<Int> = _contrast

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    fun init(imagePath: String, borderStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(imagePath)
            if (!file.exists()) return@launch

            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@launch
            rawBitmap = bmp

            val parts = borderStr.split(",").mapNotNull { it.toFloatOrNull() }
            val warped = if (parts.size >= 8) {
                val quad = DocumentQuadrilateral.fromFloatArray(parts.toFloatArray())
                val topW = quad.topLeft.distanceTo(quad.topRight)
                val botW = quad.bottomLeft.distanceTo(quad.bottomRight)
                val leftH = quad.topLeft.distanceTo(quad.bottomLeft)
                val rightH = quad.topRight.distanceTo(quad.bottomRight)

                val targetW = max(topW, botW).toInt().coerceAtLeast(300)
                val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)
                NativeScannerEngine.warpDocument(bmp, quad, targetW, targetH)
            } else {
                bmp
            }

            warpedBaseBitmap = warped

            // Generate downscaled base bitmap for high-performance interactive preview
            val maxPreviewDim = 1080
            val maxWarpDim = max(warped.width, warped.height)
            if (maxWarpDim > maxPreviewDim) {
                val scale = maxPreviewDim.toFloat() / maxWarpDim
                val pw = (warped.width * scale).toInt()
                val ph = (warped.height * scale).toInt()
                previewBaseBitmap = Bitmap.createScaledBitmap(warped, pw, ph, true)
            } else {
                previewBaseBitmap = warped
            }

            applyCurrentPipeline()
        }
    }

    fun setFilterMode(mode: EnhanceMode) {
        _selectedMode.value = mode
        applyCurrentPipeline()
    }

    fun setBrightness(b: Int) {
        _brightness.value = b
        applyCurrentPipeline()
    }

    fun setContrast(c: Int) {
        _contrast.value = c
        applyCurrentPipeline()
    }

    private fun applyCurrentPipeline() {
        val base = previewBaseBitmap ?: return
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch(Dispatchers.Default) {
            val copy = base.copy(Bitmap.Config.ARGB_8888, true)
            NativeScannerEngine.applyEnhancement(copy, _selectedMode.value.id)
            if (_brightness.value != 0 || _contrast.value != 0) {
                NativeScannerEngine.adjustBrightnessContrast(copy, _brightness.value, _contrast.value)
            }
            _previewBitmap.value = copy
        }
    }

    fun saveDocument(docId: Long, borderStr: String, onComplete: (targetDocId: Long) -> Unit) {
        val fullBase = warpedBaseBitmap ?: return
        _isSaving.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // Apply enhancements to the high-resolution bitmap before saving
            val finalHighResBmp = fullBase.copy(Bitmap.Config.ARGB_8888, true)
            NativeScannerEngine.applyEnhancement(finalHighResBmp, _selectedMode.value.id)
            if (_brightness.value != 0 || _contrast.value != 0) {
                NativeScannerEngine.adjustBrightnessContrast(finalHighResBmp, _brightness.value, _contrast.value)
            }

            val targetDocId = if (docId <= 0L) {
                repository.createNewDocument()
            } else {
                docId
            }

            val savedPage = repository.savePage(
                documentId = targetDocId,
                processedBitmap = finalHighResBmp,
                originalBitmap = rawBitmap,
                cropBorderJson = borderStr,
                enhanceModeId = _selectedMode.value.id
            )

            // Trigger background OCR if enabled in settings
            val settings = SettingsManager.getInstance(getApplication())
            if (settings.autoOcrEnabled.value) {
                launch {
                    try {
                        val ocrResult = OcrEngine.recognizeText(
                            bitmap = finalHighResBmp,
                            language = settings.ocrLanguage.value
                        )
                        if (ocrResult.isNotBlank()) {
                            repository.updatePage(savedPage.copy(ocrText = ocrResult))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _isSaving.value = false
                onComplete(targetDocId)
            }
        }
    }
}

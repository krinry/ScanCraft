package com.krinry.scancraft.ui.camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.camera.CaptureMode
import com.krinry.scancraft.camera.DocumentAnalyzer
import com.krinry.scancraft.cv.BookSpineDetector
import com.krinry.scancraft.cv.IdCardCropper
import com.krinry.scancraft.cv.OcrEngine
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.data.repository.SettingsManager
import com.krinry.scancraft.ui.crop.CropScreen
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    docId: Long,
    initialMode: String = "Document",
    onPhotoCaptured: (imagePath: String, docId: Long) -> Unit,
    onClose: () -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    // Gallery Image Picker ("Import")
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                try {
                    val targetFile = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (viewModel.currentMode.value == CaptureMode.DOCUMENT) {
                        viewModel.addDocumentItemFromFile(targetFile.absolutePath)
                    } else {
                        onPhotoCaptured(targetFile.absolutePath, docId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    // PDF / Files Picker ("Import Files")
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (!uris.isNullOrEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    var importedCount = 0
                    for (uri in uris) {
                        val mimeType = context.contentResolver.getType(uri) ?: ""
                        if (mimeType == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                            try {
                                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                                if (pfd != null) {
                                    val pdfRenderer = PdfRenderer(pfd)
                                    val maxPages = 100 // Cap maximum imported pages to prevent memory exhaustion
                                    val count = minOf(pdfRenderer.pageCount, maxPages)
                                    for (i in 0 until count) {
                                        val page = pdfRenderer.openPage(i)
                                        val w = page.width * 2
                                        val h = page.height * 2
                                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()

                                        val pageFile = File(context.cacheDir, "pdf_import_${System.currentTimeMillis()}_$i.jpg")
                                        pageFile.outputStream().use { out ->
                                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }
                                        bmp.recycle()
                                        viewModel.addDocumentItemFromFile(pageFile.absolutePath)
                                        importedCount++
                                    }
                                    pdfRenderer.close()
                                    pfd.close()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            try {
                                val targetFile = File(context.cacheDir, "file_import_${System.currentTimeMillis()}.jpg")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    targetFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                viewModel.addDocumentItemFromFile(targetFile.absolutePath)
                                importedCount++
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (importedCount > 0) {
                            Toast.makeText(context, "Imported $importedCount page(s) into document", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        val matchMode = CaptureMode.entries.firstOrNull {
            it.title.equals(initialMode, ignoreCase = true) ||
                    (it == CaptureMode.DOCUMENT && (initialMode.equals("Docs", ignoreCase = true) || initialMode.equals("Document", ignoreCase = true)))
        }
        if (matchMode != null) {
            viewModel.setMode(matchMode)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to scan documents.", color = Color.White)
        }
        return
    }

    val currentMode by viewModel.currentMode.collectAsState()
    val detectedQuad by viewModel.detectedQuad.collectAsState()
    val previewDimens by viewModel.previewDimens.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val qualityMode by viewModel.qualityMode.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val cropAfterScan by viewModel.cropAfterScan.collectAsState()
    val autoCropEnabled by viewModel.autoCropEnabled.collectAsState()
    val batchCount by viewModel.batchCount.collectAsState()
    val batchQueue by viewModel.batchQueue.collectAsState()
    val autoCaptureMode by viewModel.autoCaptureMode.collectAsState()
    val stabilityState by viewModel.stabilityState.collectAsState()
    val triggerAutoCapture by viewModel.triggerAutoCapture.collectAsState()

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val settingsManager = remember { SettingsManager.getInstance(context) }
    var showGrid by remember { mutableStateOf(settingsManager.gridEnabled.value) }
    var showSideSheet by remember { mutableStateOf(false) }
    var showFilterSlider by remember { mutableStateOf(false) }
    var showDraftDialog by remember { mutableStateOf(false) }
    var showDiscardIdDialog by remember { mutableStateOf(false) }
    var showBatchReview by remember { mutableStateOf(false) }
    var pendingCropImagePath by remember { mutableStateOf<String?>(null) }

    var ocrResultText by remember { mutableStateOf<String?>(null) }
    var isOcrProcessing by remember { mutableStateOf(false) }

    var idCardFrontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewLayoutSize by remember { mutableStateOf(IntSize.Zero) }

    // Real-time Horizon Leveler using accelerometer tilt physics
    val leveler = remember { com.krinry.scancraft.camera.CameraLeveler(context) }
    val levelerState by leveler.levelerState.collectAsState()

    // Flashlight / Torch dynamic control & cleanup
    LaunchedEffect(flashMode, camera) {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                val isTorch = flashMode == ImageCapture.FLASH_MODE_ON
                cam.cameraControl.enableTorch(isTorch)
                imageCapture?.flashMode = flashMode
            }
        }
    }

    DisposableEffect(Unit) {
        leveler.start()
        onDispose {
            leveler.stop()
            camera?.cameraControl?.enableTorch(false)
        }
    }

    // Accidental Back Prevention Handler
    val handleBack = {
        if (currentMode == CaptureMode.ID_CARD && idCardFrontBitmap != null) {
            showDiscardIdDialog = true
        } else if (currentMode == CaptureMode.DOCUMENT && batchQueue.isNotEmpty()) {
            showDraftDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    // Save Scans as Draft Dialog
    if (showDraftDialog) {
        AlertDialog(
            onDismissRequest = { showDraftDialog = false },
            title = { Text("Save Scans as Draft?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "You have ${batchQueue.size} scanned page(s). Save them as a draft document so they appear in Home?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDraftDialog = false
                        coroutineScope.launch {
                            viewModel.saveBatchAsDraft(docId)
                            Toast.makeText(context, "Saved to Drafts!", Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Save Draft", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDraftDialog = false
                            viewModel.clearBatch()
                            onClose()
                        }
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showDraftDialog = false }) {
                        Text("Keep Scanning", color = Color.White)
                    }
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Discard ID Card Dialog
    if (showDiscardIdDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardIdDialog = false },
            title = { Text("Discard Front Side?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Discard front side and exit?", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardIdDialog = false
                        idCardFrontBitmap?.recycle()
                        idCardFrontBitmap = null
                        onClose()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardIdDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // OCR Result Dialog (To Text Mode)
    if (ocrResultText != null) {
        AlertDialog(
            onDismissRequest = { ocrResultText = null },
            title = { Text("Extracted Text (OCR)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (ocrResultText!!.isBlank()) "No text detected in this photo." else ocrResultText!!,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Scanned Text", ocrResultText))
                        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                        ocrResultText = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Copy Text", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrResultText = null }) {
                    Text("Dismiss", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
        else -> Icons.Default.FlashOff
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewLayoutSize = it }
    ) {
        // 3:4 Viewfinder Frame (Clean-room 1:1 camera framing, zero cropping)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .aspectRatio(3f / 4f)
                .clipToBounds()
        ) {
            // CameraX Live Preview
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(flashMode)
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build()
                        imageCapture = capture

                        val analysis = ImageAnalysis.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()

                        analysis.setAnalyzer(
                            Executors.newSingleThreadExecutor(),
                            DocumentAnalyzer { quad, w, h ->
                                if (autoCropEnabled) {
                                    viewModel.onDocumentDetected(quad, w, h)
                                }
                            }
                        )

                        try {
                            cameraProvider.unbindAll()
                            val boundCam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                                analysis
                            )
                            camera = boundCam
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Viewfinder overlay with dynamic guides (ID Card, Book spine/badges, QR, Quad, Leveler)
            ViewfinderOverlay(
                mode = currentMode,
                quad = if (autoCropEnabled) detectedQuad else null,
                previewWidth = previewDimens.first,
                previewHeight = previewDimens.second,
                showGrid = showGrid,
                idCardStep = if (idCardFrontBitmap == null) 1 else 2,
                stabilityState = stabilityState,
                autoCaptureMode = autoCaptureMode,
                levelerState = levelerState
            )
        }

        // Top Control Bar (5 items matching uploaded screenshot layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Flash Toggle
            IconButton(onClick = { viewModel.toggleFlash() }) {
                Icon(
                    flashIcon,
                    contentDescription = "Flash",
                    modifier = Modifier.size(24.dp),
                    tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) ScannerGreen else Color.White
                )
            }

            // 2. Resolution / Quality Badge (Opens Side Sheet)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, ScannerGreen),
                modifier = Modifier.clickable { showSideSheet = true }
            ) {
                Text(
                    text = qualityMode.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // 3. Filter Toggle (Toggles in-place filter slider bar right on screen)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showFilterSlider) ScannerGreen.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { showFilterSlider = !showFilterSlider }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.ColorLens,
                    contentDescription = "Filter",
                    tint = if (showFilterSlider || selectedFilter != 0) ScannerGreen else Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = EnhanceMode.fromId(selectedFilter).shortCode,
                    color = if (showFilterSlider || selectedFilter != 0) ScannerGreen else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 4. Grid Toggle
            IconButton(onClick = {
                val next = !showGrid
                showGrid = next
                settingsManager.setGridEnabled(next)
            }) {
                Icon(
                    if (showGrid) Icons.Filled.GridOn else Icons.Outlined.GridOff,
                    contentDescription = "Grid",
                    tint = if (showGrid) ScannerGreen else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 5. Auto / Manual Shutter Toggle Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (autoCaptureMode) ScannerGreen.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (autoCaptureMode) ScannerGreen else Color.Gray),
                modifier = Modifier.clickable {
                    viewModel.toggleAutoCapture()
                    Toast.makeText(context, if (!autoCaptureMode) "Auto-Shutter ON" else "Auto-Shutter OFF", Toast.LENGTH_SHORT).show()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (autoCaptureMode) Icons.Default.CameraAlt else Icons.Outlined.CameraAlt,
                        contentDescription = "Auto Shutter",
                        tint = if (autoCaptureMode) ScannerGreen else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (autoCaptureMode) "AUTO" else "MANUAL",
                        color = if (autoCaptureMode) ScannerGreen else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 6. Settings Gear (Opens M3 Side Sheet)
            IconButton(onClick = { showSideSheet = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Controls: Filmstrip + In-place Filter Slider + Mode Carousel + Centered Shutter & Import Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(bottom = 16.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Document Mode Filmstrip Queue
            if (currentMode == CaptureMode.DOCUMENT && batchQueue.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${batchQueue.size} pages captured",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap to review",
                            color = ScannerGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showBatchReview = true }
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(batchQueue, key = { _, item -> item.id }) { index, item ->
                            Box(
                                modifier = Modifier
                                    .size(width = 62.dp, height = 82.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .clickable { showBatchReview = true }
                            ) {
                                Image(
                                    bitmap = item.thumbnailBitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(3.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .clickable { viewModel.removeBatchItem(item.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove page",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // In-Place Horizontal Filter Slider (No Bottom Sheet! In-place like Batch Process Screen)
            AnimatedVisibility(
                visible = showFilterSlider,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(Color(0xFF141414), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ColorLens,
                                contentDescription = null,
                                tint = ScannerGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Filter: ${EnhanceMode.fromId(selectedFilter).displayName}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = { showFilterSlider = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(EnhanceMode.entries) { mode ->
                            val isSelected = mode.id == selectedFilter
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.setSelectedFilter(mode.id)
                                    }
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 54.dp, height = 64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF222222))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.75.dp,
                                            color = if (isSelected) ScannerGreen else Color(0xFF383838),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) ScannerGreen else Color(0xFF333333)
                                    ) {
                                        Text(
                                            text = mode.shortCode,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mode.displayName,
                                    color = if (isSelected) ScannerGreen else Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Mode Carousel: Book | To Text | Docs | ID Card | QR Code
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(CaptureMode.entries) { mode ->
                    val isSelected = mode == currentMode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .clickable { viewModel.setMode(mode) }
                    ) {
                        Text(
                            text = mode.displayTitle.uppercase(),
                            color = if (isSelected) ScannerGreen else Color.LightGray,
                            fontSize = if (isSelected) 14.sp else 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ScannerGreen)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            }

            // Single vs Batch Mode Switcher
            if (currentMode == CaptureMode.DOCUMENT) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Single Mode Option
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (cropAfterScan) ScannerGreen else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .clickable { viewModel.setCropAfterScan(true) }
                        ) {
                            Text(
                                text = "Single",
                                color = if (cropAfterScan) Color.White else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (cropAfterScan) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }

                        // Batch Mode Option
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (!cropAfterScan) ScannerGreen else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .clickable { viewModel.setCropAfterScan(false) }
                        ) {
                            Text(
                                text = "Batch",
                                color = if (!cropAfterScan) Color.White else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (!cropAfterScan) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Shutter Row: Perfectly Centered Shutter + Left (Close) + Right (Compact Import & Forward Arrow)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                // Left slot: Close X Button
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Auto-Capture Shutter Event Trigger
                LaunchedEffect(triggerAutoCapture) {
                    if (triggerAutoCapture != null) {
                        viewModel.clearAutoCaptureTrigger()
                        imageCapture?.let { capture ->
                            if (currentMode == CaptureMode.DOCUMENT) {
                                if (cropAfterScan) {
                                    viewModel.capturePhoto(
                                        imageCapture = capture,
                                        onPhotoCaptured = { savedPath ->
                                            pendingCropImagePath = savedPath
                                        },
                                        onError = { Toast.makeText(context, "Auto-capture failed", Toast.LENGTH_SHORT).show() }
                                    )
                                } else {
                                    viewModel.captureBatchPhoto(
                                        imageCapture = capture,
                                        onSuccess = { Toast.makeText(context, "Auto-captured document page", Toast.LENGTH_SHORT).show() },
                                        onError = { Toast.makeText(context, "Auto-capture failed", Toast.LENGTH_SHORT).show() }
                                    )
                                }
                            }
                        }
                    }
                }

                // Center slot: Shutter Button (EXACT CENTER OF SCREEN)
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .border(3.5.dp, Color.White, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(ScannerGreen)
                            .clickable {
                                imageCapture?.let { capture ->
                                    when (currentMode) {
                                        CaptureMode.DOCUMENT -> {
                                            if (cropAfterScan) {
                                                viewModel.capturePhoto(
                                                    imageCapture = capture,
                                                    onPhotoCaptured = { savedPath ->
                                                        pendingCropImagePath = savedPath
                                                    },
                                                    onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                                )
                                            } else {
                                                viewModel.captureBatchPhoto(
                                                    imageCapture = capture,
                                                    onSuccess = { /* Document item added */ },
                                                    onError = { Toast.makeText(context, "Document capture failed", Toast.LENGTH_SHORT).show() }
                                                )
                                            }
                                        }
                                        CaptureMode.TO_TEXT -> {
                                            isOcrProcessing = true
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    coroutineScope.launch(Dispatchers.Default) {
                                                        val bmp = BitmapFactory.decodeFile(savedPath)
                                                        if (bmp != null) {
                                                            val text = OcrEngine.recognizeText(bmp)
                                                            if (!bmp.isRecycled) bmp.recycle()
                                                            try {
                                                                File(savedPath).delete()
                                                            } catch (e: Exception) { /* Ignore */ }
                                                            withContext(Dispatchers.Main) {
                                                                isOcrProcessing = false
                                                                ocrResultText = text
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                isOcrProcessing = false
                                                            }
                                                        }
                                                    }
                                                },
                                                onError = {
                                                    isOcrProcessing = false
                                                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                        CaptureMode.ID_CARD -> {
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    coroutineScope.launch(Dispatchers.Default) {
                                                        val rawBitmap = BitmapFactory.decodeFile(savedPath)
                                                        if (rawBitmap != null) {
                                                            val cropW = if (viewLayoutSize.width > 0) viewLayoutSize.width else rawBitmap.width
                                                            val cropH = if (viewLayoutSize.height > 0) viewLayoutSize.height else rawBitmap.height
                                                            val croppedCard = IdCardCropper.cropCard(rawBitmap, cropW, cropH)
                                                            if (rawBitmap != croppedCard && !rawBitmap.isRecycled) {
                                                                rawBitmap.recycle()
                                                            }
                                                            try {
                                                                File(savedPath).delete()
                                                            } catch (e: Exception) { /* Ignore */ }

                                                            withContext(Dispatchers.Main) {
                                                                if (idCardFrontBitmap == null) {
                                                                    idCardFrontBitmap = croppedCard
                                                                    Toast.makeText(context, "Front side saved! Now capture Back side.", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    val front = idCardFrontBitmap!!
                                                                    val back = croppedCard
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        val app = context.applicationContext as ScanCraftApp
                                                                        val repo = ScannerRepository(
                                                                            context = context,
                                                                            documentDao = app.database.documentDao(),
                                                                            pageDao = app.database.pageDao(),
                                                                            folderDao = app.database.folderDao()
                                                                        )
                                                                        val targetDocId = if (docId > 0) docId else null
                                                                        repo.stitchIdCardPages(front, back, targetDocId)
                                                                        if (!front.isRecycled) front.recycle()
                                                                        if (!back.isRecycled) back.recycle()
                                                                        withContext(Dispatchers.Main) {
                                                                            idCardFrontBitmap = null
                                                                            Toast.makeText(context, "ID Card 2-sided scan created!", Toast.LENGTH_LONG).show()
                                                                            onClose()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                        CaptureMode.BOOK -> {
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val bmp = BitmapFactory.decodeFile(savedPath)
                                                        if (bmp != null) {
                                                            val spineX = BookSpineDetector.detectSpine(bmp)
                                                            val app = context.applicationContext as ScanCraftApp
                                                            val repo = ScannerRepository(
                                                                context = context,
                                                                documentDao = app.database.documentDao(),
                                                                pageDao = app.database.pageDao(),
                                                                folderDao = app.database.folderDao()
                                                            )
                                                            val targetDocId = if (docId > 0) docId else null
                                                            repo.splitAndSaveBookPages(bmp, spineX, targetDocId)
                                                            if (!bmp.isRecycled) bmp.recycle()
                                                            try {
                                                                File(savedPath).delete()
                                                            } catch (e: Exception) { /* Ignore */ }
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Book pages split & saved!", Toast.LENGTH_SHORT).show()
                                                                onClose()
                                                            }
                                                        }
                                                    }
                                                },
                                                onError = { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                        else -> {
                                            viewModel.capturePhoto(
                                                imageCapture = capture,
                                                onPhotoCaptured = { savedPath ->
                                                    onPhotoCaptured(savedPath, docId)
                                                },
                                                onError = { /* Log error */ }
                                            )
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOcrProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp))
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Shutter badge for Document mode
                    if (currentMode == CaptureMode.DOCUMENT && batchCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp),
                            shape = CircleShape,
                            color = ScannerGreen,
                            border = BorderStroke(1.5.dp, Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = batchCount.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Right slot: Forward Arrow (when scans taken) + Smaller Import Buttons
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (currentMode == CaptureMode.DOCUMENT && batchCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact Import (Gallery)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { galleryLauncher.launch("image/*") }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = "Import",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Import", color = Color.White, fontSize = 8.sp)
                            }

                            // Forward Arrow Button with Badge to review scans!
                            Surface(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .clickable { showBatchReview = true },
                                color = ScannerGreen,
                                shadowElevation = 6.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Proceed to Review",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "$batchCount",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Smaller, compact "Import" & "Import Files"
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Smaller Import (Photo Gallery)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { galleryLauncher.launch("image/*") }
                                    .padding(horizontal = 4.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = "Import",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Import", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }

                            // Smaller Import Files (PDF & Documents)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { filePickerLauncher.launch(arrayOf("application/pdf", "image/*")) }
                                    .padding(horizontal = 4.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PictureAsPdf,
                                    contentDescription = "Import Files",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Files", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // Material 3 Side Sheet for Settings & Quality (Slides from right edge instead of bottom sheet)
        AnimatedVisibility(
            visible = showSideSheet,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            ) {
                // Translucent Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showSideSheet = false }
                )

                // Side Sheet Container
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 280.dp, max = 340.dp)
                        .align(Alignment.CenterEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume touches inside side sheet */ },
                    color = Color(0xFF181818),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Side Sheet Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan Settings",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            IconButton(
                                onClick = { showSideSheet = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF282828))

                        // 1. Image Quality Slider Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Capture Quality",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ScannerGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, ScannerGreen)
                                ) {
                                    Text(
                                        text = qualityMode.label,
                                        color = ScannerGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = qualityMode.description,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Slider(
                                value = when (qualityMode) {
                                    QualityMode.SD -> 1f
                                    QualityMode.HD -> 2f
                                    QualityMode.UHD -> 3f
                                },
                                onValueChange = { v ->
                                    val mode = when (v.roundToInt()) {
                                        1 -> QualityMode.SD
                                        2 -> QualityMode.HD
                                        else -> QualityMode.UHD
                                    }
                                    viewModel.setQualityMode(mode)
                                },
                                valueRange = 1f..3f,
                                steps = 1,
                                colors = SliderDefaults.colors(
                                    thumbColor = ScannerGreen,
                                    activeTrackColor = ScannerGreen,
                                    inactiveTrackColor = Color(0xFF383838),
                                    activeTickColor = Color.White,
                                    inactiveTickColor = Color.Gray
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "SD\n1080p",
                                    color = if (qualityMode == QualityMode.SD) ScannerGreen else Color.Gray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "HD\nBalanced",
                                    color = if (qualityMode == QualityMode.HD) ScannerGreen else Color.Gray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "UHD\nMax Sensor",
                                    color = if (qualityMode == QualityMode.UHD) ScannerGreen else Color.Gray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF282828))

                        // 2. Crop After Scan Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Crop after each scan",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Open manual crop editor after every photo",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = cropAfterScan,
                                onCheckedChange = { viewModel.setCropAfterScan(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ScannerGreen
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFF282828))

                        // 3. Auto Crop Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto crop images",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically detect document corners and perspective warp",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = autoCropEnabled,
                                onCheckedChange = { viewModel.setAutoCropEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ScannerGreen
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFF282828))

                        // 4. Privacy & Hardware Information
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF222222),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "100% Offline & Private",
                                    color = ScannerGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Scans and image processing never leave your device. Zero telemetry, zero cloud tracking.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full-page Batch Review Screen Overlay
        if (showBatchReview && batchQueue.isNotEmpty()) {
            BatchReviewScreen(
                docId = docId,
                batchQueue = batchQueue,
                onRotateItem = { id, clockwise -> viewModel.rotateBatchItem(id, clockwise) },
                onRemoveItem = { id -> viewModel.removeBatchItem(id) },
                onUpdateCropQuad = { id, quad -> viewModel.updateBatchItemCrop(id, quad) },
                onClose = { showBatchReview = false },
                onBatchSaved = { _ ->
                    viewModel.clearBatch()
                    showBatchReview = false
                    onClose()
                }
            )
        }

        // Crop Screen when "Crop images after each scan" is enabled or image captured
        if (pendingCropImagePath != null) {
            val cropItems = remember(batchQueue, pendingCropImagePath) {
                val list = batchQueue.map {
                    com.krinry.scancraft.ui.crop.CropItem(
                        id = it.id,
                        imagePath = it.rawFilePath,
                        initialQuad = it.cropQuad,
                        rotationDegrees = it.rotationDegrees
                    )
                }.toMutableList()
                val path = pendingCropImagePath!!
                if (list.none { it.imagePath == path }) {
                    list.add(com.krinry.scancraft.ui.crop.CropItem(id = 0L, imagePath = path))
                }
                list
            }
            val initialIdx = (cropItems.size - 1).coerceAtLeast(0)
            CropScreen(
                items = cropItems,
                initialIndex = initialIdx,
                imagePath = pendingCropImagePath!!,
                docId = docId,
                onBatchCropConfirmed = { quadsMap ->
                    val path = pendingCropImagePath
                    quadsMap.forEach { (id, quad) ->
                        if (id == 0L && path != null) {
                            viewModel.addDocumentItemFromFile(path) { newItem ->
                                viewModel.updateBatchItemCrop(newItem.id, quad)
                            }
                        } else {
                            viewModel.updateBatchItemCrop(id, quad)
                        }
                    }
                    pendingCropImagePath = null
                },
                onCropConfirmed = { quad ->
                    viewModel.addDocumentItemFromFile(pendingCropImagePath!!) { newItem ->
                        viewModel.updateBatchItemCrop(newItem.id, quad)
                    }
                    pendingCropImagePath = null
                },
                onClose = {
                    pendingCropImagePath = null
                }
            )
        }
    }
}

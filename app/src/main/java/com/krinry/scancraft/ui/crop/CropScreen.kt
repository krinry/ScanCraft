package com.krinry.scancraft.ui.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.EdgeDetector
import com.krinry.scancraft.cv.NativeScannerEngine
import com.krinry.scancraft.cv.PointF
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File
import kotlin.math.max

/**
 * Universal Production-grade Document Crop Screen adhering strictly to AGENTS.md clean-room standards.
 *
 * Single Source of Truth for cropping across the entire application:
 * 1. Post-scan / camera capture flow (routes to FilterScreen).
 * 2. Existing Page crop flow from PageViewerScreen (saves perspective warp directly to Room DB & files).
 * 3. Standalone quad selection with callback.
 *
 * Features:
 * - 8-Handle Crop Box with dark translucent overlay mask.
 * - Directional Midpoint Drag preserving perspective angles and skew.
 * - Dynamic HUD Loupe Evasion (Top-Left by default, flips to Top-Right near contact).
 * - Magnetic Snapping to image borders, 90° orthogonal alignments, and physical document edges.
 * - 100% Free corner drag with 1:1 finger tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imagePath: String = "",
    docId: Long = -1L,
    page: Page? = null,
    initialQuad: DocumentQuadrilateral? = null,
    rotationDegrees: Int? = null,
    onCropConfirmed: ((DocumentQuadrilateral) -> Unit)? = null,
    onNavigateToFilter: ((imagePath: String, docId: Long, borderJson: String) -> Unit)? = null,
    onPageUpdated: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Determine target image file
    val effectiveImagePath = remember(page, imagePath) {
        if (page != null) {
            val origFile = File(page.originalImagePath)
            if (origFile.exists() && origFile.length() > 0) {
                origFile.absolutePath
            } else {
                page.processedImagePath
            }
        } else {
            imagePath
        }
    }

    val effectiveDocId = page?.documentId ?: docId
    val explicitRotation = remember(page, rotationDegrees) {
        if (page != null) {
            (page.rotationDegrees + (rotationDegrees ?: 0)) % 360
        } else {
            rotationDegrees
        }
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quad by remember { mutableStateOf<DocumentQuadrilateral?>(null) }
    var activeTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var activeHandlePoint by remember { mutableStateOf<Offset?>(null) }
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }
    var fullImageWidth by remember { mutableFloatStateOf(0f) }
    var fullImageHeight by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val isQuadValid = quad?.isValidConvex() ?: true

    // Parse initial quad from Page if available and none explicitly passed
    val parsedInitialQuad = remember(page, initialQuad) {
        if (initialQuad != null) {
            initialQuad
        } else if (page != null && page.cropBorderJson.isNotBlank()) {
            val parts = page.cropBorderJson.split(",").mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 8) {
                DocumentQuadrilateral.fromFloatArray(parts.toFloatArray())
            } else null
        } else null
    }

    fun updateTransform(zoomChange: Float, panChange: Offset) {
        val newZoom = (zoomScale * zoomChange).coerceIn(1f, 4.5f)
        zoomScale = newZoom
        if (newZoom <= 1.01f) {
            panOffset = Offset.Zero
        } else {
            val maxPanX = (viewWidth * (newZoom - 1f)) / 2f
            val maxPanY = (viewHeight * (newZoom - 1f)) / 2f
            panOffset = Offset(
                (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
            )
        }
    }

    fun calculateImageFit(viewW: Float, viewH: Float, bmpW: Int, bmpH: Int): RectF {
        if (viewW <= 0f || viewH <= 0f || bmpW <= 0 || bmpH <= 0) return RectF(0f, 0f, viewW, viewH)
        val imageAspect = bmpW.toFloat() / bmpH.toFloat()
        val viewAspect = viewW / viewH
        return if (viewAspect > imageAspect) {
            val renderW = viewH * imageAspect
            val offX = (viewW - renderW) / 2f
            RectF(offX, 0f, offX + renderW, viewH)
        } else {
            val renderH = viewW / imageAspect
            val offY = (viewH - renderH) / 2f
            RectF(0f, offY, viewW, offY + renderH)
        }
    }

    fun mapDetectedQuadToScreen(detected: DocumentQuadrilateral, fitRect: RectF, bmpW: Int, bmpH: Int): DocumentQuadrilateral {
        val sx = fitRect.width() / bmpW.toFloat()
        val sy = fitRect.height() / bmpH.toFloat()
        return DocumentQuadrilateral(
            topLeft = PointF(
                (fitRect.left + detected.topLeft.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.topLeft.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            topRight = PointF(
                (fitRect.left + detected.topRight.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.topRight.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            bottomRight = PointF(
                (fitRect.left + detected.bottomRight.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.bottomRight.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            bottomLeft = PointF(
                (fitRect.left + detected.bottomLeft.x * sx).coerceIn(fitRect.left, fitRect.right),
                (fitRect.top + detected.bottomLeft.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            )
        )
    }

    fun mapInitialQuadToScreen(initial: DocumentQuadrilateral, fitRect: RectF, origW: Float, origH: Float): DocumentQuadrilateral {
        val isNormalized = initial.topLeft.x <= 1.05f && initial.topRight.x <= 1.05f && initial.bottomRight.x <= 1.05f && initial.bottomLeft.x <= 1.05f
        val sx = if (isNormalized) fitRect.width() else (fitRect.width() / if (origW > 0f) origW else fitRect.width())
        val sy = if (isNormalized) fitRect.height() else (fitRect.height() / if (origH > 0f) origH else fitRect.height())
        val baseLeft = fitRect.left
        val baseTop = fitRect.top
        return DocumentQuadrilateral(
            topLeft = PointF(
                (baseLeft + initial.topLeft.x * sx).coerceIn(fitRect.left, fitRect.right),
                (baseTop + initial.topLeft.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            topRight = PointF(
                (baseLeft + initial.topRight.x * sx).coerceIn(fitRect.left, fitRect.right),
                (baseTop + initial.topRight.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            bottomRight = PointF(
                (baseLeft + initial.bottomRight.x * sx).coerceIn(fitRect.left, fitRect.right),
                (baseTop + initial.bottomRight.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            ),
            bottomLeft = PointF(
                (baseLeft + initial.bottomLeft.x * sx).coerceIn(fitRect.left, fitRect.right),
                (baseTop + initial.bottomLeft.y * sy).coerceIn(fitRect.top, fitRect.bottom)
            )
        )
    }

    fun autoDetectCorners(bmp: Bitmap, fitRect: RectF) {
        coroutineScope.launch(Dispatchers.Default) {
            val detected = EdgeDetector.detectInBitmap(bmp)
            val screenQuad = mapDetectedQuadToScreen(detected, fitRect, bmp.width, bmp.height)
            withContext(Dispatchers.Main) {
                quad = screenQuad
            }
        }
    }

    // Decode sample bitmap for high performance rendering
    LaunchedEffect(effectiveImagePath, explicitRotation) {
        withContext(Dispatchers.IO) {
            val file = File(effectiveImagePath)
            if (file.exists()) {
                var exifDegrees = 0
                try {
                    val exif = android.media.ExifInterface(file.absolutePath)
                    val orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                    exifDegrees = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) { }

                // If explicit rotation is provided by caller (batch item or page), use it authoritatively without double-adding EXIF
                val totalRotation = explicitRotation ?: exifDegrees

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                val rawW = if (totalRotation % 180 == 0) opts.outWidth.toFloat() else opts.outHeight.toFloat()
                val rawH = if (totalRotation % 180 == 0) opts.outHeight.toFloat() else opts.outWidth.toFloat()
                fullImageWidth = rawW
                fullImageHeight = rawH

                var sampleSize = 1
                while (opts.outWidth / (sampleSize * 2) >= 1200 && opts.outHeight / (sampleSize * 2) >= 1200) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (decoded != null && totalRotation != 0) {
                    val m = Matrix().apply { postRotate(totalRotation.toFloat()) }
                    bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                } else {
                    bitmap = decoded
                }
            }
        }
    }

    fun proceedToNext() {
        val q = quad ?: return
        if (!q.isValidConvex()) {
            Toast.makeText(context, "Invalid boundary geometry", Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = bitmap ?: return
        val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
        val renderW = fitRect.width()
        val renderH = fitRect.height()
        if (renderW <= 0f || renderH <= 0f) return

        val origW = if (fullImageWidth > 0f) fullImageWidth else bmp.width.toFloat()
        val origH = if (fullImageHeight > 0f) fullImageHeight else bmp.height.toFloat()

        fun mapPoint(pt: PointF): PointF {
            val nx = ((pt.x - fitRect.left) / renderW).coerceIn(0f, 1f)
            val ny = ((pt.y - fitRect.top) / renderH).coerceIn(0f, 1f)
            return PointF(nx * origW, ny * origH)
        }

        val realQuad = DocumentQuadrilateral(
            topLeft = mapPoint(q.topLeft),
            topRight = mapPoint(q.topRight),
            bottomRight = mapPoint(q.bottomRight),
            bottomLeft = mapPoint(q.bottomLeft)
        )

        val borderStr = "${realQuad.topLeft.x},${realQuad.topLeft.y}," +
                "${realQuad.topRight.x},${realQuad.topRight.y}," +
                "${realQuad.bottomRight.x},${realQuad.bottomRight.y}," +
                "${realQuad.bottomLeft.x},${realQuad.bottomLeft.y}"

        if (page != null) {
            // Existing page workflow: warp, apply existing enhancements, update Room DB & file
            isSaving = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val rawFile = File(effectiveImagePath)
                    val origBmp = BitmapFactory.decodeFile(rawFile.absolutePath) ?: bmp
                    val topW = realQuad.topLeft.distanceTo(realQuad.topRight)
                    val botW = realQuad.bottomLeft.distanceTo(realQuad.bottomRight)
                    val leftH = realQuad.topLeft.distanceTo(realQuad.bottomLeft)
                    val rightH = realQuad.topRight.distanceTo(realQuad.bottomRight)
                    val targetW = max(topW, botW).toInt().coerceAtLeast(300)
                    val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)

                    val warped = NativeScannerEngine.warpDocument(origBmp, realQuad, targetW, targetH)
                    if (page.enhanceMode != 0) {
                        NativeScannerEngine.applyEnhancement(warped, page.enhanceMode)
                    }
                    if (page.brightness != 0 || page.contrast != 0) {
                        NativeScannerEngine.adjustBrightnessContrast(warped, page.brightness, page.contrast)
                    }

                    val app = context.applicationContext as ScanCraftApp
                    val repo = ScannerRepository(
                        context = context,
                        documentDao = app.database.documentDao(),
                        pageDao = app.database.pageDao(),
                        folderDao = app.database.folderDao()
                    )

                    repo.updatePageBitmap(
                        pageId = page.id,
                        newBitmap = warped,
                        cropBorderJson = borderStr
                    )

                    withContext(Dispatchers.Main) {
                        isSaving = false
                        Toast.makeText(context, "Page cropped successfully!", Toast.LENGTH_SHORT).show()
                        onPageUpdated?.invoke()
                        onClose()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        Toast.makeText(context, "Failed to crop page: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (onCropConfirmed != null) {
            onCropConfirmed(realQuad)
            onClose()
        } else if (onNavigateToFilter != null) {
            onNavigateToFilter(effectiveImagePath, effectiveDocId, borderStr)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (page != null) "Crop Page" else "Crop Document",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = { proceedToNext() },
                        enabled = isQuadValid && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScannerGreen,
                            disabledContainerColor = Color.DarkGray
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (page != null) "Save" else "Next",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (page != null) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF141414)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left 90° Rotate
                    CropBarButton(
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        label = "Left",
                        onClick = {
                            bitmap?.let { b ->
                                val matrix = Matrix().apply { postRotate(-90f) }
                                val rotated = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                                bitmap = rotated
                                val temp = fullImageWidth
                                fullImageWidth = fullImageHeight
                                fullImageHeight = temp
                                val r = calculateImageFit(viewWidth, viewHeight, rotated.width, rotated.height)
                                autoDetectCorners(rotated, r)
                            }
                        }
                    )

                    // Right 90° Rotate
                    CropBarButton(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Right",
                        onClick = {
                            bitmap?.let { b ->
                                val matrix = Matrix().apply { postRotate(90f) }
                                val rotated = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                                bitmap = rotated
                                val temp = fullImageWidth
                                fullImageWidth = fullImageHeight
                                fullImageHeight = temp
                                val r = calculateImageFit(viewWidth, viewHeight, rotated.width, rotated.height)
                                autoDetectCorners(rotated, r)
                            }
                        }
                    )

                    // Crop All (Expand to full page boundaries)
                    CropBarButton(
                        icon = Icons.Outlined.SelectAll,
                        label = "All",
                        onClick = {
                            bitmap?.let { b ->
                                val r = calculateImageFit(viewWidth, viewHeight, b.width, b.height)
                                quad = DocumentQuadrilateral(
                                    topLeft = PointF(r.left, r.top),
                                    topRight = PointF(r.right, r.top),
                                    bottomRight = PointF(r.right, r.bottom),
                                    bottomLeft = PointF(r.left, r.bottom)
                                )
                            }
                        }
                    )

                    // Auto Detect Corners
                    CropBarButton(
                        icon = Icons.Outlined.AutoFixHigh,
                        label = "Auto",
                        onClick = {
                            bitmap?.let { b ->
                                val r = calculateImageFit(viewWidth, viewHeight, b.width, b.height)
                                autoDetectCorners(b, r)
                            }
                        }
                    )

                    // Process Button
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (isQuadValid && !isSaving) ScannerGreen else Color.DarkGray)
                            .clickable(enabled = isQuadValid && !isSaving) { proceedToNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (page != null) Icons.Default.Check else Icons.Default.Check,
                                contentDescription = "Confirm Crop",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            viewWidth = coordinates.size.width.toFloat()
                            viewHeight = coordinates.size.height.toFloat()
                            if (quad == null) {
                                val r = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                                if (parsedInitialQuad != null) {
                                    quad = mapInitialQuadToScreen(parsedInitialQuad, r, fullImageWidth, fullImageHeight)
                                } else {
                                    autoDetectCorners(bmp, r)
                                }
                            }
                        }
                ) {
                    // Zoomable container with graphicsLayer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Document Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)

                        quad?.let { currentQuad ->
                            DraggablePolygonOverlay(
                                quad = currentQuad,
                                boundsRect = fitRect,
                                onQuadChanged = { quad = it },
                                onHandleActive = { hPt, tPt ->
                                    activeHandlePoint = hPt
                                    activeTouchPoint = tPt
                                },
                                zoomScale = zoomScale,
                                onTransform = { zRatio, pDelta ->
                                    updateTransform(zRatio, pDelta)
                                }
                            )
                        }
                    }

                    // Floating Reset Zoom Pill (appears when user pinches to zoom in)
                    if (zoomScale > 1.05f) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, ScannerGreen),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clickable {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${(zoomScale * 10).toInt() / 10f}x",
                                    color = ScannerGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reset",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Floating HUD Magnifier Loupe
                    val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                    quad?.let { _ ->
                        AnimatedVisibility(
                            visible = activeHandlePoint != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            MagnifierLoupe(
                                bitmap = bmp,
                                touchPoint = activeTouchPoint,
                                handlePoint = activeHandlePoint,
                                imageScale = fitRect.width() / bmp.width.toFloat(),
                                fitRect = fitRect
                            )
                        }
                    }
                }
            } ?: CircularProgressIndicator(color = ScannerGreen)
        }
    }
}

@Composable
fun CropBarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

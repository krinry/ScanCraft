package com.krinry.scancraft.ui.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File
import kotlin.math.max

/**
 * Representation of an individual item in a multi-image crop session.
 */
data class CropItem(
    val id: Long = 0L,
    val imagePath: String,
    val initialQuad: DocumentQuadrilateral? = null,
    val rotationDegrees: Int = 0,
    val page: Page? = null
)

/**
 * Result data holder for batch cropping: quad and effective rotation degrees.
 */
data class BatchCropResult(
    val quad: DocumentQuadrilateral,
    val rotationDegrees: Int
)

/**
 * Maps a [Page] entity into a unified [CropItem].
 */
fun Page.toCropItem(explicitRotation: Int? = null): CropItem {
    val origFile = File(originalImagePath)
    val hasOriginal = origFile.exists() && origFile.length() > 0
    val path = if (hasOriginal) origFile.absolutePath else processedImagePath
    // If using fallback processed image, previous page rotation is already baked into pixels
    val baseRot = if (hasOriginal) rotationDegrees else 0
    val rot = (baseRot + (explicitRotation ?: 0)) % 360
    val pQuad = cropBorderJson
        .takeIf { it.isNotBlank() }
        ?.split(",")
        ?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.size >= 8 }
        ?.let { DocumentQuadrilateral.fromFloatArray(it.toFloatArray()) }
    return CropItem(
        id = id,
        imagePath = path,
        initialQuad = pQuad,
        rotationDegrees = rot,
        page = this
    )
}

/**
 * Universal Production-grade Document Crop Screen adhering strictly to AGENTS.md clean-room standards.
 *
 * Single Source of Truth for cropping across the entire application:
 * 1. Post-scan / camera capture flow (supports swiping across multiple scanned images in batch).
 * 2. Existing Page crop flow from PageViewerScreen (saves perspective warp directly to Room DB & files).
 * 3. Standalone quad selection with callback.
 *
 * Features:
 * - Full multi-item horizontal swipe navigation: swiping changes image to crop subsequent/previous images.
 * - Header pager indicator: `<  1 / 5  >` for clear tap and swipe guidance.
 * - 8-Handle Crop Box with dark translucent overlay mask.
 * - Directional Midpoint Drag preserving perspective angles and skew.
 * - Dynamic HUD Loupe Evasion (Top-Left by default, flips to Top-Right near contact).
 * - Full Dark & Light theme integration for bars, buttons, and dialogs.
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
    items: List<CropItem> = emptyList(),
    initialIndex: Int = 0,
    onBatchCropConfirmed: ((Map<Long, BatchCropResult>) -> Unit)? = null,
    onCropConfirmed: ((DocumentQuadrilateral) -> Unit)? = null,
    onNavigateToFilter: ((imagePath: String, docId: Long, borderJson: String) -> Unit)? = null,
    onPageUpdated: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = ScannerTheme.colors

    // Resolve list of crop items
    val resolvedItems = remember(items, page, imagePath, initialQuad, rotationDegrees) {
        if (items.isNotEmpty()) {
            items
        } else if (page != null) {
            val baseItem = page.toCropItem(rotationDegrees)
            listOf(if (initialQuad != null) baseItem.copy(initialQuad = initialQuad) else baseItem)
        } else if (imagePath.isNotBlank()) {
            val initialRot = rotationDegrees ?: run {
                try {
                    val exif = ExifInterface(imagePath)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) { 0 }
            }
            listOf(CropItem(id = 0L, imagePath = imagePath, initialQuad = initialQuad, rotationDegrees = initialRot))
        } else {
            emptyList()
        }
    }

    var currentIndex by remember(resolvedItems) {
        mutableIntStateOf(initialIndex.coerceIn(0, (resolvedItems.size - 1).coerceAtLeast(0)))
    }

    val currentItem = resolvedItems.getOrNull(currentIndex)
    val effectiveDocId = currentItem?.page?.documentId ?: docId

    // In-memory caches for multi-page quads & image properties
    val savedQuads = remember { mutableStateMapOf<Int, DocumentQuadrilateral>() }
    val itemRotations = remember { mutableStateMapOf<Int, Int>() }

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
        val startIndex = currentIndex
        coroutineScope.launch(Dispatchers.Default) {
            val detected = EdgeDetector.detectInBitmap(bmp)
            val screenQuad = mapDetectedQuadToScreen(detected, fitRect, bmp.width, bmp.height)
            withContext(Dispatchers.Main) {
                if (currentIndex == startIndex && bitmap === bmp) {
                    quad = screenQuad
                }
            }
        }
    }

    fun mapScreenQuadToOriginal(q: DocumentQuadrilateral, bmp: Bitmap): DocumentQuadrilateral? {
        val fitRect = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
        val renderW = fitRect.width()
        val renderH = fitRect.height()
        if (renderW <= 0f || renderH <= 0f) return null

        val origW = if (fullImageWidth > 0f) fullImageWidth else bmp.width.toFloat()
        val origH = if (fullImageHeight > 0f) fullImageHeight else bmp.height.toFloat()

        fun mapPoint(pt: PointF): PointF {
            val nx = ((pt.x - fitRect.left) / renderW).coerceIn(0f, 1f)
            val ny = ((pt.y - fitRect.top) / renderH).coerceIn(0f, 1f)
            return PointF(nx * origW, ny * origH)
        }

        return DocumentQuadrilateral(
            topLeft = mapPoint(q.topLeft),
            topRight = mapPoint(q.topRight),
            bottomRight = mapPoint(q.bottomRight),
            bottomLeft = mapPoint(q.bottomLeft)
        )
    }

    fun saveCurrentQuad() {
        val currentQ = quad ?: return
        val currentBmp = bitmap ?: return
        val realQ = mapScreenQuadToOriginal(currentQ, currentBmp)
        if (realQ != null) {
            savedQuads[currentIndex] = realQ
        }
    }

    fun switchToPage(newIndex: Int) {
        if (newIndex in resolvedItems.indices && newIndex != currentIndex) {
            if (quad?.isValidConvex() == false) {
                Toast.makeText(context, "Invalid boundary geometry", Toast.LENGTH_SHORT).show()
                return
            }
            saveCurrentQuad()
            zoomScale = 1f
            panOffset = Offset.Zero
            activeTouchPoint = null
            activeHandlePoint = null
            quad = null
            bitmap = null
            currentIndex = newIndex
        }
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

    // Decode sample bitmap for current page
    LaunchedEffect(currentIndex, currentItem?.imagePath, itemRotations[currentIndex]) {
        val itm = currentItem ?: return@LaunchedEffect
        val filePath = itm.imagePath
        val explicitRot = itemRotations[currentIndex] ?: itm.rotationDegrees

        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (file.exists()) {
                val totalRotation = explicitRot

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
                val finalBmp = if (decoded != null && totalRotation != 0) {
                    val m = Matrix().apply { postRotate(totalRotation.toFloat()) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                } else {
                    decoded
                }

                withContext(Dispatchers.Main) {
                    bitmap = finalBmp
                    // Initialize quad for current item
                    finalBmp?.let { bmp ->
                        val r = calculateImageFit(viewWidth, viewHeight, bmp.width, bmp.height)
                        val prevSaved = savedQuads[currentIndex]
                        val isRotatedByUser = itemRotations[currentIndex] != null && itemRotations[currentIndex] != itm.rotationDegrees
                        if (prevSaved != null) {
                            quad = mapInitialQuadToScreen(prevSaved, r, fullImageWidth, fullImageHeight)
                        } else if (itm.initialQuad != null && !isRotatedByUser) {
                            quad = mapInitialQuadToScreen(itm.initialQuad, r, fullImageWidth, fullImageHeight)
                        } else {
                            autoDetectCorners(bmp, r)
                        }
                    }
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
        val realQuad = mapScreenQuadToOriginal(q, bmp) ?: return
        savedQuads[currentIndex] = realQuad

        val borderStr = "${realQuad.topLeft.x},${realQuad.topLeft.y}," +
                "${realQuad.topRight.x},${realQuad.topRight.y}," +
                "${realQuad.bottomRight.x},${realQuad.bottomRight.y}," +
                "${realQuad.bottomLeft.x},${realQuad.bottomLeft.y}"

        if (onBatchCropConfirmed != null) {
            // Batch workflow: confirm all quads mapped to their item ID with their effective rotation
            val resultMap = mutableMapOf<Long, BatchCropResult>()
            resolvedItems.forEachIndexed { idx, itm ->
                val itemQuad = if (idx == currentIndex) realQuad else (savedQuads[idx] ?: itm.initialQuad)
                if (itemQuad != null) {
                    val rot = itemRotations[idx] ?: itm.rotationDegrees
                    resultMap[itm.id] = BatchCropResult(itemQuad, rot)
                }
            }
            onBatchCropConfirmed(resultMap)
        } else if (currentItem?.page != null) {
            // Existing single/multi page workflow: warp, apply existing enhancements, update Room DB & file
            val targetPage = currentItem.page
            val isFallback = targetPage.originalImagePath.isBlank() ||
                !File(targetPage.originalImagePath).exists() ||
                currentItem.imagePath == targetPage.processedImagePath
            val effRot = itemRotations[currentIndex] ?: currentItem.rotationDegrees
            val rotToApply = effRot
            isSaving = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val rawFile = File(currentItem.imagePath)
                    var origBmp = BitmapFactory.decodeFile(rawFile.absolutePath) ?: bmp
                    if (rotToApply != 0) {
                        val matrix = Matrix().apply { postRotate(rotToApply.toFloat()) }
                        val rotated = Bitmap.createBitmap(origBmp, 0, 0, origBmp.width, origBmp.height, matrix, true)
                        if (rotated != origBmp) {
                            origBmp.recycle()
                            origBmp = rotated
                        }
                    }
                    val topW = realQuad.topLeft.distanceTo(realQuad.topRight)
                    val botW = realQuad.bottomLeft.distanceTo(realQuad.bottomRight)
                    val leftH = realQuad.topLeft.distanceTo(realQuad.bottomLeft)
                    val rightH = realQuad.topRight.distanceTo(realQuad.bottomRight)
                    val targetW = max(topW, botW).toInt().coerceAtLeast(300)
                    val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)

                    val warped = NativeScannerEngine.warpDocument(origBmp, realQuad, targetW, targetH)
                    if (targetPage.enhanceMode != 0) {
                        NativeScannerEngine.applyEnhancement(warped, targetPage.enhanceMode)
                    }
                    if (targetPage.brightness != 0 || targetPage.contrast != 0) {
                        NativeScannerEngine.adjustBrightnessContrast(warped, targetPage.brightness, targetPage.contrast)
                    }

                    val app = context.applicationContext as ScanCraftApp
                    val repo = ScannerRepository(
                        context = context,
                        documentDao = app.database.documentDao(),
                        pageDao = app.database.pageDao(),
                        folderDao = app.database.folderDao()
                    )

                    repo.updatePageBitmap(
                        pageId = targetPage.id,
                        newBitmap = warped,
                        cropBorderJson = borderStr,
                        rotationDegrees = if (isFallback) {
                            (targetPage.rotationDegrees + effRot) % 360
                        } else {
                            effRot
                        }
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
            onNavigateToFilter(currentItem?.imagePath ?: imagePath, effectiveDocId, borderStr)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (resolvedItems.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { switchToPage(currentIndex - 1) },
                                enabled = currentIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Page",
                                    tint = if (currentIndex > 0) colors.textPrimary else colors.textMuted.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Crop (${currentIndex + 1} / ${resolvedItems.size})",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { switchToPage(currentIndex + 1) },
                                enabled = currentIndex < resolvedItems.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Page",
                                    tint = if (currentIndex < resolvedItems.size - 1) colors.textPrimary else colors.textMuted.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (currentItem?.page != null) "Crop Page" else "Crop Document",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = { proceedToNext() },
                        enabled = isQuadValid && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScannerGreen,
                            disabledContainerColor = colors.surfaceSecondary
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (currentItem?.page != null || onBatchCropConfirmed != null) "Save" else "Next",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (currentItem?.page != null || onBatchCropConfirmed != null) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surface,
                border = BorderStroke(0.5.dp, colors.cardBorder),
                tonalElevation = 6.dp
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
                            val baseRot = itemRotations[currentIndex] ?: currentItem?.rotationDegrees ?: 0
                            savedQuads.remove(currentIndex)
                            quad = null
                            itemRotations[currentIndex] = (baseRot - 90 + 360) % 360
                        }
                    )

                    // Right 90° Rotate
                    CropBarButton(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Right",
                        onClick = {
                            val baseRot = itemRotations[currentIndex] ?: currentItem?.rotationDegrees ?: 0
                            savedQuads.remove(currentIndex)
                            quad = null
                            itemRotations[currentIndex] = (baseRot + 90) % 360
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
                            .background(if (isQuadValid && !isSaving) ScannerGreen else colors.surfaceSecondary)
                            .clickable(enabled = isQuadValid && !isSaving) { proceedToNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
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
        val workspaceBg = colors.background
        var dragAccumulator by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(workspaceBg)
                .pointerInput(currentIndex, resolvedItems.size, zoomScale) {
                    if (resolvedItems.size > 1 && zoomScale <= 1.05f) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                            onDragEnd = {
                                val threshold = 40.dp.toPx()
                                if (dragAccumulator < -threshold && currentIndex < resolvedItems.size - 1) {
                                    switchToPage(currentIndex + 1)
                                } else if (dragAccumulator > threshold && currentIndex > 0) {
                                    switchToPage(currentIndex - 1)
                                }
                                dragAccumulator = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (activeHandlePoint == null) {
                                    dragAccumulator += dragAmount
                                }
                            }
                        )
                    }
                },
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
                                val itm = currentItem
                                val prevSaved = savedQuads[currentIndex]
                                val isRotatedByUser = itemRotations[currentIndex] != null && itemRotations[currentIndex] != itm?.rotationDegrees
                                if (prevSaved != null) {
                                    quad = mapInitialQuadToScreen(prevSaved, r, fullImageWidth, fullImageHeight)
                                } else if (itm?.initialQuad != null && !isRotatedByUser) {
                                    quad = mapInitialQuadToScreen(itm.initialQuad, r, fullImageWidth, fullImageHeight)
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
                                },
                                onSwipe = if (resolvedItems.size > 1) {
                                    { isNext ->
                                        if (isNext && currentIndex < resolvedItems.size - 1) {
                                            switchToPage(currentIndex + 1)
                                            true
                                        } else if (!isNext && currentIndex > 0) {
                                            switchToPage(currentIndex - 1)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                } else null
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
    val colors = ScannerTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

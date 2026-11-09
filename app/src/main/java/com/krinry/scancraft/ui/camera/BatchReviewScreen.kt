package com.krinry.scancraft.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.camera.BatchWarpProcessor
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.NativeScannerEngine
import com.krinry.scancraft.cv.PointF
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BatchReviewScreen(
    docId: Long,
    batchQueue: List<BatchCaptureItem>,
    onRotateItem: (id: Long, clockwise: Boolean) -> Unit,
    onRemoveItem: (id: Long) -> Unit,
    onUpdateCropQuad: (id: Long, quad: DocumentQuadrilateral, rotationDegrees: Int?) -> Unit,
    onClose: () -> Unit,
    onBatchSaved: (savedDocId: Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = ScannerTheme.colors
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { batchQueue.size })

    // If batch queue becomes empty (e.g. user deleted all items), return to camera
    LaunchedEffect(batchQueue.size) {
        if (batchQueue.isEmpty()) {
            onClose()
        }
    }

    val defaultTitle = remember {
        "New Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date())
    }
    var documentTitle by remember { mutableStateOf(defaultTitle) }
    var showEditTitleDialog by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0 to batchQueue.size) }

    var selectedFilter by remember { mutableStateOf(EnhanceMode.NO_SHADOW) }
    var applyToAllPages by remember { mutableStateOf(true) }

    // Per-page filter override if "applyToAllPages" is false
    var pageFilters by remember { mutableStateOf(mapOf<Long, EnhanceMode>()) }

    // Global and per-page tone adjustments (Brightness, Contrast)
    var globalBrightness by remember { mutableStateOf(0) }
    var globalContrast by remember { mutableStateOf(0) }
    var pageAdjustments by remember { mutableStateOf(mapOf<Long, Pair<Int, Int>>()) }

    var showAdjustSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var cropEditingItem by remember { mutableStateOf<BatchCaptureItem?>(null) }

    // In-memory caches for high-performance interactive preview
    var warpedBaseCache by remember { mutableStateOf(mapOf<Long, Bitmap>()) }
    var previewDisplayCache by remember { mutableStateOf(mapOf<Long, Bitmap>()) }
    var filterThumbnailsCache by remember { mutableStateOf(mapOf<EnhanceMode, Bitmap>()) }

    val safeCurrentPage = pagerState.currentPage.coerceIn(0, (batchQueue.size - 1).coerceAtLeast(0))
    val currentItem = batchQueue.getOrNull(safeCurrentPage)

    val currentEffectiveFilter = if (applyToAllPages) {
        selectedFilter
    } else {
        currentItem?.let { pageFilters[it.id] } ?: selectedFilter
    }

    val currentTone = if (applyToAllPages) {
        globalBrightness to globalContrast
    } else {
        currentItem?.let { pageAdjustments[it.id] } ?: (globalBrightness to globalContrast)
    }

    val filterModes = remember {
        listOf(
            EnhanceMode.NO_SHADOW to "Beta",
            EnhanceMode.ORIGINAL to null,
            EnhanceMode.LIGHTEN to null,
            EnhanceMode.MAGIC_COLOR to null,
            EnhanceMode.GRAYSCALE to null,
            EnhanceMode.BW to null
        )
    }

    // 1. Generate/update warped base bitmap when currentItem changes or crop/rotation changes
    LaunchedEffect(currentItem?.id, currentItem?.cropQuad, currentItem?.rotationDegrees) {
        val item = currentItem ?: return@LaunchedEffect
        val cachedBase = warpedBaseCache[item.id]
        if (cachedBase == null || cachedBase.isRecycled) {
            val warped = loadWarpedBaseBitmap(item) { detectedQuad ->
                onUpdateCropQuad(item.id, detectedQuad, item.rotationDegrees)
            }
            warpedBaseCache = warpedBaseCache + (item.id to warped)

            // Generate filter strip miniatures from this base
            val miniThumbs = generateFilterThumbnails(warped, filterModes.map { it.first })
            filterThumbnailsCache = miniThumbs
        } else {
            // Already cached, refresh miniatures if needed
            if (filterThumbnailsCache.isEmpty()) {
                val miniThumbs = generateFilterThumbnails(cachedBase, filterModes.map { it.first })
                filterThumbnailsCache = miniThumbs
            }
        }
    }

    // 2. Generate/update final display bitmap with filter & tone fine-tuning applied
    LaunchedEffect(
        currentItem?.id,
        currentEffectiveFilter,
        currentTone.first,
        currentTone.second,
        warpedBaseCache[currentItem?.id]
    ) {
        val item = currentItem ?: return@LaunchedEffect
        val base = warpedBaseCache[item.id] ?: item.thumbnailBitmap
        val filtered = applyFilterAndTonesToBitmap(
            base = base,
            filter = currentEffectiveFilter,
            brightness = currentTone.first,
            contrast = currentTone.second
        )
        previewDisplayCache = previewDisplayCache + (item.id to filtered)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEditTitleDialog = true }
                    ) {
                        Text(
                            text = documentTitle,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit Title",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                actions = {
                    // Quick Delete Icon in Top Bar
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = currentItem != null && !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete Page",
                            tint = Color(0xFFFF6B6B)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Done Button
                    Button(
                        onClick = {
                            if (!isProcessing && batchQueue.isNotEmpty()) {
                                isProcessing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val app = context.applicationContext as ScanCraftApp
                                    val repo = ScannerRepository(
                                        context = context,
                                        documentDao = app.database.documentDao(),
                                        pageDao = app.database.pageDao(),
                                        folderDao = app.database.folderDao()
                                    )
                                    val processor = BatchWarpProcessor(context, repo)
                                    val savedId = processor.processBatch(
                                        targetDocId = docId,
                                        items = batchQueue,
                                        selectedEnhanceMode = selectedFilter,
                                        pageFilters = if (applyToAllPages) emptyMap() else pageFilters,
                                        brightness = globalBrightness,
                                        contrast = globalContrast,
                                        pageAdjustments = if (applyToAllPages) emptyMap() else pageAdjustments,
                                        customTitle = documentTitle,
                                        onProgress = { done, total ->
                                            processingProgress = done to total
                                        }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        onBatchSaved(savedId)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
            ) {
                // 1. "Apply to all pages" Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apply to all pages",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(
                        selected = applyToAllPages,
                        onClick = { applyToAllPages = !applyToAllPages },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ScannerGreen,
                            unselectedColor = Color.Gray
                        )
                    )
                }

                // 2. Horizontal Filter Strip with Live Filtered Miniatures & Double-Click/Tap Adjust
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(filterModes) { (mode, badge) ->
                        val isSelected = currentEffectiveFilter == mode

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    if (isSelected) {
                                        // Double-click or re-click on selected filter opens tone fine-tuning sheet
                                        showAdjustSheet = true
                                    } else {
                                        if (applyToAllPages) {
                                            selectedFilter = mode
                                        } else {
                                            currentItem?.let { item ->
                                                pageFilters = pageFilters + (item.id to mode)
                                            }
                                        }
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceSecondary)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) ScannerGreen else colors.cardBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            ) {
                                // Miniature Page Preview with this filter effect applied
                                val miniBmp = filterThumbnailsCache[mode] ?: currentItem?.thumbnailBitmap
                                if (miniBmp != null) {
                                    Image(
                                        bitmap = miniBmp.asImageBitmap(),
                                        contentDescription = mode.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Beta Badge on Top Right
                                if (badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = 4.dp),
                                        color = Color(0xFFFF5722),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = badge,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // If selected, show small tune icon indicator indicating adjustable
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.75f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(3.dp)
                                            .size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Adjust",
                                            tint = ScannerGreen,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp)
                                        )
                                    }
                                }

                                // Dark overlay for filter label at bottom of thumbnail
                                Surface(
                                    color = Color.Black.copy(alpha = 0.70f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        color = if (isSelected) ScannerGreen else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)

                // 3. Bottom Actions Toolbar (Retake, Add, Left, Right, Crop)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatchActionButton(
                        icon = Icons.Outlined.Cameraswitch,
                        label = "Retake",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentItem?.let { item ->
                                onRemoveItem(item.id)
                                onClose()
                            }
                        }
                    )

                    BatchActionButton(
                        icon = Icons.Outlined.AddBox,
                        label = "Add",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onClose()
                        }
                    )

                    BatchActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        label = "Left",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentItem?.let { item ->
                                warpedBaseCache = warpedBaseCache - item.id
                                previewDisplayCache = previewDisplayCache - item.id
                                onRotateItem(item.id, false)
                            }
                        }
                    )

                    BatchActionButton(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Right",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentItem?.let { item ->
                                warpedBaseCache = warpedBaseCache - item.id
                                previewDisplayCache = previewDisplayCache - item.id
                                onRotateItem(item.id, true)
                            }
                        }
                    )

                    BatchActionButton(
                        icon = Icons.Outlined.Crop,
                        label = "Crop",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            cropEditingItem = currentItem
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Document Page Viewport with Original Image Aspect Ratio & Live Cropped/Filtered Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp
                ) { pageIndex ->
                    val item = batchQueue.getOrNull(pageIndex)
                    if (item != null) {
                        val displayBmp = previewDisplayCache[item.id]
                            ?: warpedBaseCache[item.id]
                            ?: item.thumbnailBitmap

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmpW = displayBmp.width.toFloat()
                            val bmpH = displayBmp.height.toFloat()
                            val aspect = if (bmpH > 0f) bmpW / bmpH else 0.707f

                            val maxW = maxWidth
                            val maxH = maxHeight

                            // Dynamically scale card dimensions to preserve exact original / warped document aspect ratio
                            val (cardW, cardH) = if (maxW.value / maxH.value > aspect) {
                                val h = maxH
                                val w = h * aspect
                                w to h
                            } else {
                                val w = maxW
                                val h = w / aspect
                                w to h
                            }

                            Surface(
                                modifier = Modifier
                                    .size(cardW, cardH)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = Color.White,
                                shadowElevation = 8.dp
                            ) {
                                Image(
                                    bitmap = displayBmp.asImageBitmap(),
                                    contentDescription = "Page ${pageIndex + 1}",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Page Indicator with Prev / Next Arrow Steppers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Previous Page",
                        tint = if (pagerState.currentPage > 0) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surfaceSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "${safeCurrentPage + 1}/${batchQueue.size}",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (pagerState.currentPage < batchQueue.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = pagerState.currentPage < batchQueue.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Next Page",
                        tint = if (pagerState.currentPage < batchQueue.size - 1) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Tone Fine-Tuning Modal Bottom Sheet (Double-Click / Re-Click on filter)
    if (showAdjustSheet) {
        var tempBrightness by remember(currentTone) { mutableStateOf(currentTone.first) }
        var tempContrast by remember(currentTone) { mutableStateOf(currentTone.second) }

        ModalBottomSheet(
            onDismissRequest = { showAdjustSheet = false },
            containerColor = colors.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.cardBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fine-Tune Tone",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = currentEffectiveFilter.displayName,
                            color = ScannerGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(
                        onClick = {
                            tempBrightness = 0
                            tempContrast = 0
                            if (applyToAllPages) {
                                globalBrightness = 0
                                globalContrast = 0
                            } else {
                                currentItem?.let { item ->
                                    pageAdjustments = pageAdjustments + (item.id to (0 to 0))
                                }
                            }
                        }
                    ) {
                        Text("Reset", color = Color(0xFFFF9800), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Brightness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Brightness", color = colors.textSecondary, fontSize = 14.sp)
                    Text(
                        text = if (tempBrightness > 0) "+$tempBrightness" else "$tempBrightness",
                        color = if (tempBrightness != 0) ScannerGreen else colors.textMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = tempBrightness.toFloat(),
                    onValueChange = { newVal ->
                        tempBrightness = newVal.toInt()
                        if (applyToAllPages) {
                            globalBrightness = tempBrightness
                        } else {
                            currentItem?.let { item ->
                                pageAdjustments = pageAdjustments + (item.id to (tempBrightness to tempContrast))
                            }
                        }
                    },
                    valueRange = -80f..80f,
                    colors = SliderDefaults.colors(
                        thumbColor = ScannerGreen,
                        activeTrackColor = ScannerGreen,
                        inactiveTrackColor = colors.cardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Contrast Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Contrast", color = colors.textSecondary, fontSize = 14.sp)
                    Text(
                        text = if (tempContrast > 0) "+$tempContrast" else "$tempContrast",
                        color = if (tempContrast != 0) ScannerGreen else colors.textMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = tempContrast.toFloat(),
                    onValueChange = { newVal ->
                        tempContrast = newVal.toInt()
                        if (applyToAllPages) {
                            globalContrast = tempContrast
                        } else {
                            currentItem?.let { item ->
                                pageAdjustments = pageAdjustments + (item.id to (tempBrightness to tempContrast))
                            }
                        }
                    },
                    valueRange = -80f..80f,
                    colors = SliderDefaults.colors(
                        thumbColor = ScannerGreen,
                        activeTrackColor = ScannerGreen,
                        inactiveTrackColor = colors.cardBorder
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showAdjustSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Page?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete page ${safeCurrentPage + 1}?",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = currentItem.id
                        val isLast = batchQueue.size <= 1
                        showDeleteConfirmDialog = false
                        warpedBaseCache = warpedBaseCache - idToDelete
                        previewDisplayCache = previewDisplayCache - idToDelete
                        onRemoveItem(idToDelete)
                        if (isLast) {
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Edit Document Title Dialog
    if (showEditTitleDialog) {
        var tempTitle by remember { mutableStateOf(documentTitle) }
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Edit Document Name", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = colors.cardBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        documentTitle = tempTitle.trim().ifEmpty { defaultTitle }
                        showEditTitleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Modal Crop Screen for Batch Items
    cropEditingItem?.let { item ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            val cropItems = remember(batchQueue) {
                batchQueue.map {
                    com.krinry.scancraft.ui.crop.CropItem(
                        id = it.id,
                        imagePath = it.rawFilePath,
                        initialQuad = it.cropQuad,
                        rotationDegrees = it.rotationDegrees
                    )
                }
            }
            val initialIdx = batchQueue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            com.krinry.scancraft.ui.crop.CropScreen(
                items = cropItems,
                initialIndex = initialIdx,
                docId = docId,
                onBatchCropConfirmed = { resultsMap ->
                    resultsMap.forEach { (itemId, res) ->
                        onUpdateCropQuad(itemId, res.quad, res.rotationDegrees)
                        warpedBaseCache = warpedBaseCache - itemId
                        previewDisplayCache = previewDisplayCache - itemId
                    }
                    cropEditingItem = null
                },
                onCropConfirmed = { newQuad ->
                    onUpdateCropQuad(item.id, newQuad, item.rotationDegrees)
                    warpedBaseCache = warpedBaseCache - item.id
                    previewDisplayCache = previewDisplayCache - item.id
                    cropEditingItem = null
                },
                onClose = { cropEditingItem = null }
            )
        }
    }

    // Progress Dialog during batch processing
    if (isProcessing) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.cardBorder),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Processing Document...",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val progressFrac = if (processingProgress.second > 0) {
                        processingProgress.first.toFloat() / processingProgress.second.toFloat()
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progressFrac },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = ScannerGreen,
                        trackColor = colors.cardBorder
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Page ${processingProgress.first} of ${processingProgress.second}",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BatchActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Loads and warps the document perspective on Dispatchers.IO for interactive preview.
 * Returns the perspective-corrected, un-filtered document bitmap.
 */
private suspend fun loadWarpedBaseBitmap(
    item: BatchCaptureItem,
    onAutoDetected: (DocumentQuadrilateral) -> Unit
): Bitmap = withContext(Dispatchers.IO) {
    val file = File(item.rawFilePath)
    if (!file.exists()) return@withContext item.thumbnailBitmap

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val origW = bounds.outWidth
    val origH = bounds.outHeight
    if (origW <= 0 || origH <= 0) return@withContext item.thumbnailBitmap

    var sampleSize = 1
    val targetDim = 1200
    while (origW / (sampleSize * 2) >= targetDim && origH / (sampleSize * 2) >= targetDim) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var rawBmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext item.thumbnailBitmap

    if (item.rotationDegrees != 0) {
        val m = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true)
        if (rotated != rawBmp) {
            rawBmp.recycle()
            rawBmp = rotated
        }
    }

    val fullRotW = if (item.rotationDegrees % 180 == 0) origW.toFloat() else origH.toFloat()
    val fullRotH = if (item.rotationDegrees % 180 == 0) origH.toFloat() else origW.toFloat()

    val quad = if (item.cropQuad != null) {
        val sx = rawBmp.width.toFloat() / fullRotW
        val sy = rawBmp.height.toFloat() / fullRotH
        DocumentQuadrilateral(
            topLeft = PointF(item.cropQuad!!.topLeft.x * sx, item.cropQuad!!.topLeft.y * sy),
            topRight = PointF(item.cropQuad!!.topRight.x * sx, item.cropQuad!!.topRight.y * sy),
            bottomRight = PointF(item.cropQuad!!.bottomRight.x * sx, item.cropQuad!!.bottomRight.y * sy),
            bottomLeft = PointF(item.cropQuad!!.bottomLeft.x * sx, item.cropQuad!!.bottomLeft.y * sy)
        )
    } else {
        val detected = NativeScannerEngine.detectDocumentBounds(rawBmp)
        val invSx = fullRotW / rawBmp.width.toFloat()
        val invSy = fullRotH / rawBmp.height.toFloat()
        val origQuad = DocumentQuadrilateral(
            topLeft = PointF(detected.topLeft.x * invSx, detected.topLeft.y * invSy),
            topRight = PointF(detected.topRight.x * invSx, detected.topRight.y * invSy),
            bottomRight = PointF(detected.bottomRight.x * invSx, detected.bottomRight.y * invSy),
            bottomLeft = PointF(detected.bottomLeft.x * invSx, detected.bottomLeft.y * invSy)
        )
        onAutoDetected(origQuad)
        detected
    }

    val topW = quad.topLeft.distanceTo(quad.topRight)
    val botW = quad.bottomLeft.distanceTo(quad.bottomRight)
    val leftH = quad.topLeft.distanceTo(quad.bottomLeft)
    val rightH = quad.topRight.distanceTo(quad.bottomRight)
    val targetW = max(topW, botW).toInt().coerceAtLeast(300)
    val targetH = max(leftH, rightH).toInt().coerceAtLeast(400)

    val warped = NativeScannerEngine.warpDocument(rawBmp, quad, targetW, targetH)
    if (warped != rawBmp) {
        rawBmp.recycle()
    }
    warped
}

/**
 * Applies the selected enhancement filter and brightness/contrast adjustments to a bitmap copy.
 */
private suspend fun applyFilterAndTonesToBitmap(
    base: Bitmap,
    filter: EnhanceMode,
    brightness: Int,
    contrast: Int
): Bitmap = withContext(Dispatchers.Default) {
    val copy = base.copy(Bitmap.Config.ARGB_8888, true)
    NativeScannerEngine.applyEnhancement(copy, filter.id)
    if (brightness != 0 || contrast != 0) {
        NativeScannerEngine.adjustBrightnessContrast(copy, brightness, contrast)
    }
    copy
}

/**
 * Generates miniature filter previews for the bottom filter strip.
 */
private suspend fun generateFilterThumbnails(
    base: Bitmap,
    modes: List<EnhanceMode>
): Map<EnhanceMode, Bitmap> = withContext(Dispatchers.Default) {
    val thumbW = 90
    val thumbH = 120
    val scaled = Bitmap.createScaledBitmap(base, thumbW, thumbH, true)
    val map = mutableMapOf<EnhanceMode, Bitmap>()
    for (mode in modes) {
        val copy = scaled.copy(Bitmap.Config.ARGB_8888, true)
        NativeScannerEngine.applyEnhancement(copy, mode.id)
        map[mode] = copy
    }
    if (scaled != base) {
        scaled.recycle()
    }
    map
}

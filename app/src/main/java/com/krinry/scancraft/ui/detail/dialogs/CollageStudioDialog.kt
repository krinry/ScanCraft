package com.krinry.scancraft.ui.detail.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.collection.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.cv.CollageGenerator
import com.krinry.scancraft.cv.CollageLayout
import com.krinry.scancraft.cv.CollageWatermarkConfig
import com.krinry.scancraft.cv.NativeScannerEngine
import com.krinry.scancraft.cv.SlotTransform
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.CollagePageSpec
import com.krinry.scancraft.ui.crop.CropScreen
import com.krinry.scancraft.ui.theme.ScannerGreen
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synchronous in-memory bitmap cache to completely eliminate image flickering during touch gestures.
 */
object CollageBitmapCache {
    private val memoryCache = LruCache<String, Bitmap>(64)

    fun get(path: String): Bitmap? {
        val bmp = memoryCache.get(path)
        if (bmp != null && !bmp.isRecycled) return bmp
        return null
    }

    fun put(path: String, bitmap: Bitmap) {
        memoryCache.put(path, bitmap)
    }

    fun getOrDecode(path: String, reqW: Int, reqH: Int): Bitmap? {
        val cached = get(path)
        if (cached != null) return cached
        if (File(path).exists()) {
            val decoded = CollageGenerator.decodeSampledBitmap(path, reqW, reqH)
            if (decoded != null) {
                memoryCache.put(path, decoded)
                return decoded
            }
        }
        return null
    }

    fun clear() {
        memoryCache.evictAll()
    }
}

data class CollageTemplateOption(
    val id: String,
    val title: String,
    val layout: CollageLayout,
    val description: String
)

val COLLAGE_TEMPLATES = listOf(
    CollageTemplateOption("4_grid", "4-Grid", CollageLayout.FOUR_GRID, "4-in-1 Grid"),
    CollageTemplateOption("6_grid", "6-Grid", CollageLayout.SIX_GRID, "6-in-1 Grid"),
    CollageTemplateOption("2x1", "2x1 Vertical", CollageLayout.TWO_VERTICAL, "2 Vertical"),
    CollageTemplateOption("1x2", "1x2 Side-by-Side", CollageLayout.TWO_HORIZONTAL, "2 Side-by-Side"),
    CollageTemplateOption("3x1", "3x1 Vertical", CollageLayout.THREE_VERTICAL, "3 Vertical"),
    CollageTemplateOption("3_top_two", "3-in-1 Split", CollageLayout.THREE_TOP_TWO_BOTTOM, "1 Top, 2 Bottom"),
    CollageTemplateOption("driver_license", "Driver License", CollageLayout.DRIVER_LICENSE, "ID Card 2-in-1"),
    CollageTemplateOption("passport", "Passport", CollageLayout.PASSPORT, "Passport & Details"),
    CollageTemplateOption("question_book", "Question Book", CollageLayout.QUESTION_BOOK, "Question Paper")
)

/**
 * State of a single collage sheet / page in multi-page mode.
 */
class CollageSheetState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val slotAssignments: SnapshotStateMap<Int, Page> = mutableStateMapOf(),
    val slotTransforms: SnapshotStateMap<Int, SlotTransform> = mutableStateMapOf(),
    val slotCroppedPaths: SnapshotStateMap<Int, String> = mutableStateMapOf()
)

/**
 * Isolated snapping guides state for canvas draw-phase rendering without recompositions.
 */
class SnapGuidesState {
    var snapVertical by mutableStateOf(false)
    var snapHorizontal by mutableStateOf(false)
}

/**
 * Multi-page drag and drop coordinator allowing images to be moved or swapped across pages.
 */
class CrossPageDragController {
    var isDragging by mutableStateOf(false)
    var sourceSheetIndex by mutableIntStateOf(-1)
    var sourceSlotIndex by mutableIntStateOf(-1)
    var draggedPage by mutableStateOf<Page?>(null)
    var draggedCustomPath by mutableStateOf<String?>(null)
    var draggedBitmap by mutableStateOf<Bitmap?>(null)
    var dragItemSize by mutableStateOf(IntSize.Zero)

    var currentRootPos by mutableStateOf(Offset.Zero)
    var startRootPos by mutableStateOf(Offset.Zero)

    var hoverSheetIndex by mutableStateOf<Int?>(null)
    var hoverSlotIndex by mutableStateOf<Int?>(null)

    val sheetBoundsInRoot = mutableMapOf<Int, Rect>()

    fun startDrag(
        sheetIndex: Int,
        slotIndex: Int,
        page: Page?,
        customPath: String?,
        bitmap: Bitmap?,
        itemSize: IntSize,
        rootPos: Offset
    ) {
        sourceSheetIndex = sheetIndex
        sourceSlotIndex = slotIndex
        draggedPage = page
        draggedCustomPath = customPath
        draggedBitmap = bitmap
        dragItemSize = itemSize
        startRootPos = rootPos
        currentRootPos = rootPos
        isDragging = true
        hoverSheetIndex = sheetIndex
        hoverSlotIndex = slotIndex
    }

    fun updateDragPosition(pos: Offset, layout: CollageLayout) {
        currentRootPos = pos
        var foundSheet: Int? = null
        var foundSlot: Int? = null

        // 1. Direct hit test within sheet bounds
        for ((sIdx, rect) in sheetBoundsInRoot) {
            if (rect.contains(pos)) {
                foundSheet = sIdx
                val localX = pos.x - rect.left
                val localY = pos.y - rect.top
                val slots = CollageGenerator.calculateSlotRects(layout, rect.width.toInt(), rect.height.toInt())
                for (i in slots.indices) {
                    val r = slots[i]
                    if (localX >= r.left && localX <= r.right && localY >= r.top && localY <= r.bottom) {
                        foundSlot = i
                        break
                    }
                }
                if (foundSlot == null && slots.isNotEmpty()) {
                    foundSlot = slots.indices.minByOrNull { i ->
                        val r = slots[i]
                        val cx = (r.left + r.right) / 2f
                        val cy = (r.top + r.bottom) / 2f
                        kotlin.math.hypot(localX - cx, localY - cy)
                    }
                }
                break
            }
        }

        // 2. Tolerance for vertical gaps between sheets
        if (foundSheet == null && sheetBoundsInRoot.isNotEmpty()) {
            val closestSheet = sheetBoundsInRoot.minByOrNull { (_, rect) ->
                val dy = when {
                    pos.y < rect.top -> rect.top - pos.y
                    pos.y > rect.bottom -> pos.y - rect.bottom
                    else -> 0f
                }
                val dx = when {
                    pos.x < rect.left -> rect.left - pos.x
                    pos.x > rect.right -> pos.x - rect.right
                    else -> 0f
                }
                kotlin.math.hypot(dx, dy)
            }
            if (closestSheet != null) {
                val rect = closestSheet.value
                val distY = when {
                    pos.y < rect.top -> rect.top - pos.y
                    pos.y > rect.bottom -> pos.y - rect.bottom
                    else -> 0f
                }
                if (distY < 80f) {
                    foundSheet = closestSheet.key
                    val localX = (pos.x - rect.left).coerceIn(0f, rect.width)
                    val localY = (pos.y - rect.top).coerceIn(0f, rect.height)
                    val slots = CollageGenerator.calculateSlotRects(layout, rect.width.toInt(), rect.height.toInt())
                    foundSlot = slots.indices.minByOrNull { i ->
                        val r = slots[i]
                        val cx = (r.left + r.right) / 2f
                        val cy = (r.top + r.bottom) / 2f
                        kotlin.math.hypot(localX - cx, localY - cy)
                    }
                }
            }
        }

        hoverSheetIndex = foundSheet
        hoverSlotIndex = foundSlot
    }

    fun commitDrop(
        sheets: MutableList<CollageSheetState>,
        onSelect: (sheetIdx: Int, slotIdx: Int) -> Unit,
        showToast: (String) -> Unit
    ) {
        if (!isDragging) return
        val srcSheetIdx = sourceSheetIndex
        val srcSlotIdx = sourceSlotIndex
        val tgtSheetIdx = hoverSheetIndex
        val tgtSlotIdx = hoverSlotIndex

        if (tgtSheetIdx != null && tgtSlotIdx != null && (tgtSheetIdx != srcSheetIdx || tgtSlotIdx != srcSlotIdx)) {
            val srcSheet = sheets.getOrNull(srcSheetIdx)
            val tgtSheet = sheets.getOrNull(tgtSheetIdx)

            if (srcSheet != null && tgtSheet != null) {
                val srcPage = srcSheet.slotAssignments[srcSlotIdx]
                val srcCrop = srcSheet.slotCroppedPaths[srcSlotIdx]
                val tgtPage = tgtSheet.slotAssignments[tgtSlotIdx]
                val tgtCrop = tgtSheet.slotCroppedPaths[tgtSlotIdx]

                // Assign src to target
                if (srcPage != null) {
                    tgtSheet.slotAssignments[tgtSlotIdx] = srcPage
                } else {
                    tgtSheet.slotAssignments.remove(tgtSlotIdx)
                }
                if (srcCrop != null) {
                    tgtSheet.slotCroppedPaths[tgtSlotIdx] = srcCrop
                } else {
                    tgtSheet.slotCroppedPaths.remove(tgtSlotIdx)
                }
                tgtSheet.slotTransforms[tgtSlotIdx] = SlotTransform()

                // Assign target to src (swap) or clear
                if (tgtPage != null || tgtCrop != null) {
                    if (tgtPage != null) {
                        srcSheet.slotAssignments[srcSlotIdx] = tgtPage
                    } else {
                        srcSheet.slotAssignments.remove(srcSlotIdx)
                    }
                    if (tgtCrop != null) {
                        srcSheet.slotCroppedPaths[srcSlotIdx] = tgtCrop
                    } else {
                        srcSheet.slotCroppedPaths.remove(srcSlotIdx)
                    }
                    srcSheet.slotTransforms[srcSlotIdx] = SlotTransform()
                    showToast("Swapped with Page ${tgtSheetIdx + 1}, Slot ${tgtSlotIdx + 1}")
                } else {
                    srcSheet.slotAssignments.remove(srcSlotIdx)
                    srcSheet.slotCroppedPaths.remove(srcSlotIdx)
                    srcSheet.slotTransforms.remove(srcSlotIdx)
                    showToast("Moved to Page ${tgtSheetIdx + 1}, Slot ${tgtSlotIdx + 1}")
                }

                onSelect(tgtSheetIdx, tgtSlotIdx)
            }
        }
        cancelDrag()
    }

    fun cancelDrag() {
        isDragging = false
        sourceSheetIndex = -1
        sourceSlotIndex = -1
        draggedPage = null
        draggedCustomPath = null
        draggedBitmap = null
        hoverSheetIndex = null
        hoverSlotIndex = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageStudioDialog(
    pages: List<Page>,
    documentTitle: String = "scan",
    onDismiss: () -> Unit,
    onGenerateCollage: ((
        layout: CollageLayout,
        pageIds: List<Long>,
        watermark: CollageWatermarkConfig?,
        transforms: Map<Int, SlotTransform>?,
        drawBorders: Boolean,
        slotCustomPaths: Map<Int, String>?
    ) -> Unit)? = null,
    onGenerateMultiCollage: ((
        specs: List<CollagePageSpec>,
        watermark: CollageWatermarkConfig?,
        drawBorders: Boolean
    ) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ScannerTheme.colors
    val listState = rememberLazyListState()

    var selectedTemplate by remember { mutableStateOf(COLLAGE_TEMPLATES[0]) } // 4-grid default
    var activeBottomTab by remember { mutableStateOf("template") } // "template", "page", "watermark", "size"
    var selectedPaperSize by remember { mutableStateOf("A4") }
    var showWatermarkInput by remember { mutableStateOf(false) }

    // Multi-page state list
    val sheets = remember { mutableStateListOf<CollageSheetState>() }

    // Currently focused sheet & slot for editing
    var selectedSheetIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSlotIndex by remember { mutableStateOf<Int?>(null) }

    // State for launching the modal CropScreen
    var cropEditingSheetIndex by remember { mutableStateOf<Int?>(null) }
    var cropEditingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var cropEditingPage by remember { mutableStateOf<Page?>(null) }

    // Preload all document bitmaps into memory cache at startup to completely prevent flickering
    LaunchedEffect(pages) {
        withContext(Dispatchers.IO) {
            for (p in pages) {
                val path = p.processedImagePath.ifEmpty { p.originalImagePath }
                if (path.isNotBlank() && File(path).exists()) {
                    CollageBitmapCache.getOrDecode(path, 1024, 1024)
                }
            }
        }
    }

    // Auto-calculate pages when template is chosen (e.g. 40 images with 2-grid -> 20 pages automatically created!)
    fun recalculateSheets(template: CollageTemplateOption, docPages: List<Page>) {
        val slotsPerPage = template.layout.maxPages
        sheets.clear()
        if (docPages.isEmpty()) {
            sheets.add(CollageSheetState())
            return
        }
        val neededSheets = maxOf(1, (docPages.size + slotsPerPage - 1) / slotsPerPage)
        for (sheetIdx in 0 until neededSheets) {
            val sheet = CollageSheetState()
            for (slotIdx in 0 until slotsPerPage) {
                val pageIdx = sheetIdx * slotsPerPage + slotIdx
                if (pageIdx < docPages.size) {
                    sheet.slotAssignments[slotIdx] = docPages[pageIdx]
                } else {
                    sheet.slotAssignments[slotIdx] = docPages[pageIdx % docPages.size]
                }
            }
            sheets.add(sheet)
        }
        selectedSheetIndex = null
        selectedSlotIndex = null
    }

    LaunchedEffect(selectedTemplate) {
        recalculateSheets(selectedTemplate, pages)
    }

    var watermarkConfig by remember {
        mutableStateOf(
            CollageWatermarkConfig(
                text = "",
                color = 0x88888888.toInt(),
                fontSizeSp = 32f,
                opacity = 0.35f,
                rotationAngle = -45f,
                isTiled = false
            )
        )
    }

    val paperAspectRatio = when (selectedPaperSize) {
        "Letter" -> 8.5f / 11f
        else -> 1f / 1.414f // Standard A4 and A5
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = colors.background,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Collage",
                                    color = colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (documentTitle.isNotBlank()) {
                                    Text(
                                        text = documentTitle,
                                        color = colors.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val specs = sheets.map { sheet ->
                                    val slotCount = selectedTemplate.layout.maxPages
                                    val slotPageIds = (0 until slotCount).map { i ->
                                        sheet.slotAssignments[i]?.id ?: 0L
                                    }
                                    CollagePageSpec(
                                        layout = selectedTemplate.layout,
                                        pageIds = slotPageIds,
                                        slotTransforms = sheet.slotTransforms.toMap(),
                                        slotCustomPaths = sheet.slotCroppedPaths.toMap()
                                    )
                                }
                                val finalWatermark = if (watermarkConfig.text.isNotBlank()) watermarkConfig else null
                                if (onGenerateMultiCollage != null) {
                                    onGenerateMultiCollage(specs, finalWatermark, false)
                                } else if (onGenerateCollage != null && specs.isNotEmpty()) {
                                    val firstSpec = specs[0]
                                    onGenerateCollage(
                                        firstSpec.layout,
                                        firstSpec.pageIds,
                                        finalWatermark,
                                        firstSpec.slotTransforms,
                                        false,
                                        firstSpec.slotCustomPaths
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Done",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .navigationBarsPadding()
                    ) {
                        // Tab Drawer Content
                        if (activeBottomTab == "template") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(COLLAGE_TEMPLATES, key = { it.id }) { template ->
                                    val isSelected = template.id == selectedTemplate.id
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) colors.primaryContainer else colors.surfaceSecondary,
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (isSelected) ScannerGreen else colors.cardBorder
                                        ),
                                        modifier = Modifier
                                            .width(84.dp)
                                            .height(76.dp)
                                            .clickable {
                                                selectedTemplate = template
                                                recalculateSheets(template, pages)
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TemplateVisualPreview(template.id, isSelected)
                                            Text(
                                                text = template.title,
                                                color = if (isSelected) ScannerGreen else colors.textSecondary,
                                                fontSize = 9.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (activeBottomTab == "page") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                val currentSheet = selectedSheetIndex?.let { sheets.getOrNull(it) } ?: sheets.firstOrNull()
                                val targetSlot = selectedSlotIndex ?: 0

                                Text(
                                    text = "Tap a document page to assign to Slot ${targetSlot + 1} (Page ${(selectedSheetIndex ?: 0) + 1}):",
                                    color = ScannerGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(pages, key = { it.id }) { page ->
                                        val isAssigned = currentSheet?.slotAssignments?.get(targetSlot)?.id == page.id

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.surfaceSecondary,
                                            border = BorderStroke(
                                                2.dp,
                                                if (isAssigned) ScannerGreen else colors.cardBorder
                                            ),
                                            modifier = Modifier
                                                .size(54.dp, 72.dp)
                                                .clickable {
                                                    if (currentSheet != null) {
                                                        currentSheet.slotAssignments[targetSlot] = page
                                                        currentSheet.slotCroppedPaths.remove(targetSlot)
                                                        currentSheet.slotTransforms[targetSlot] = SlotTransform()
                                                        Toast.makeText(
                                                            context,
                                                            "Assigned Page ${page.pageIndex + 1} to Slot ${targetSlot + 1}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                        ) {
                                            Box {
                                                AsyncImage(
                                                    model = File(page.thumbnailPath.ifEmpty { page.processedImagePath }),
                                                    contentDescription = "Page ${page.pageIndex + 1}",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = "P${page.pageIndex + 1}",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                    )
                                                }
                                                if (isAssigned) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = ScannerGreen,
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .align(Alignment.TopEnd)
                                                            .padding(2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)

                        // Bottom Navigation Bar with [+ Page], [Add Watermark], [Template], [Size]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // [+ Page] - Adds a BLANK PAGE to canvas
                            CollageBottomActionItem(
                                icon = Icons.Outlined.PostAdd,
                                label = "Page",
                                isActive = activeBottomTab == "page",
                                onClick = {
                                    val newSheet = CollageSheetState()
                                    // Leaves slotAssignments empty -> blank page!
                                    sheets.add(newSheet)
                                    selectedSheetIndex = sheets.size - 1
                                    selectedSlotIndex = 0
                                    activeBottomTab = "page"
                                    scope.launch {
                                        listState.animateScrollToItem(sheets.size - 1)
                                    }
                                    Toast.makeText(context, "Added Blank Page ${sheets.size}", Toast.LENGTH_SHORT).show()
                                }
                            )

                            // Watermark
                            CollageBottomActionItem(
                                icon = Icons.Outlined.WaterDamage,
                                label = if (watermarkConfig.text.isNotBlank()) "Watermark*" else "Add a watermark",
                                isActive = activeBottomTab == "watermark",
                                onClick = {
                                    activeBottomTab = "watermark"
                                    showWatermarkInput = true
                                }
                            )

                            // Template
                            CollageBottomActionItem(
                                icon = Icons.Outlined.GridView,
                                label = "Template",
                                isActive = activeBottomTab == "template",
                                onClick = { activeBottomTab = "template" }
                            )

                            // Paper Size (A4, A5, Letter)
                            CollageBottomActionItem(
                                icon = Icons.Outlined.AspectRatio,
                                label = selectedPaperSize,
                                isActive = activeBottomTab == "size",
                                onClick = {
                                    selectedPaperSize = when (selectedPaperSize) {
                                        "A4" -> "A5"
                                        "A5" -> "Letter"
                                        else -> "A4"
                                    }
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                // Vertically scrollable multi-page canvas area
                val dragController = remember { CrossPageDragController() }
                var canvasBoxSize by remember { mutableStateOf(IntSize.Zero) }

                LaunchedEffect(dragController.isDragging) {
                    if (dragController.isDragging) {
                        while (isActive && dragController.isDragging) {
                            if (canvasBoxSize.height > 0) {
                                val rootY = dragController.currentRootPos.y
                                val edgeThreshold = 140f
                                if (rootY > canvasBoxSize.height - edgeThreshold) {
                                    val intensity = ((rootY - (canvasBoxSize.height - edgeThreshold)) / edgeThreshold).coerceIn(0.1f, 1.0f)
                                    listState.scrollBy(24f * intensity)
                                } else if (rootY < edgeThreshold && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                                    val intensity = ((edgeThreshold - rootY) / edgeThreshold).coerceIn(0.1f, 1.0f)
                                    listState.scrollBy(-24f * intensity)
                                }
                            }
                            delay(16)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(colors.background)
                        .onSizeChanged { canvasBoxSize = it }
                ) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !dragController.isDragging,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(sheets, key = { _, sheet -> sheet.id }) { sheetIdx, sheet ->
                            CollageSheetCard(
                                sheetIndex = sheetIdx,
                                totalSheets = sheets.size,
                                sheetState = sheet,
                                layout = selectedTemplate.layout,
                                aspectRatio = paperAspectRatio,
                                isSheetSelected = selectedSheetIndex == sheetIdx,
                                selectedSlotIndex = if (selectedSheetIndex == sheetIdx) selectedSlotIndex else null,
                                watermarkConfig = watermarkConfig,
                                dragController = dragController,
                                onCommitCrossDrop = {
                                    dragController.commitDrop(
                                        sheets = sheets,
                                        onSelect = { sIdx, slotIdx ->
                                            selectedSheetIndex = sIdx
                                            selectedSlotIndex = slotIdx
                                        },
                                        showToast = { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onSelectSlot = { slot ->
                                    selectedSheetIndex = sheetIdx
                                    selectedSlotIndex = slot
                                },
                                onDeselect = {
                                    if (selectedSheetIndex == sheetIdx) {
                                        selectedSheetIndex = null
                                        selectedSlotIndex = null
                                    }
                                },
                                onDeleteSlot = { slot ->
                                    sheet.slotAssignments.remove(slot)
                                    sheet.slotCroppedPaths.remove(slot)
                                    sheet.slotTransforms.remove(slot)
                                    if (selectedSheetIndex == sheetIdx && selectedSlotIndex == slot) {
                                        selectedSlotIndex = null
                                    }
                                },
                                onDeleteSheet = {
                                    if (sheets.size > 1) {
                                        sheets.removeAt(sheetIdx)
                                        if (selectedSheetIndex == sheetIdx) {
                                            selectedSheetIndex = null
                                            selectedSlotIndex = null
                                        }
                                    }
                                },
                                onTransformChange = { slot, newTransform ->
                                    sheet.slotTransforms[slot] = newTransform
                                }
                            )
                        }
                    }

                    // Floating preview avatar of dragged image following the touch point
                    if (dragController.isDragging && dragController.draggedBitmap != null) {
                        val dragW = (dragController.dragItemSize.width * 0.75f).coerceIn(80f, 220f)
                        val dragH = (dragController.dragItemSize.height * 0.75f).coerceIn(80f, 220f)
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (dragController.currentRootPos.x - dragW / 2f).toInt(),
                                        (dragController.currentRootPos.y - dragH / 2f).toInt()
                                    )
                                }
                                .size(
                                    width = with(LocalDensity.current) { dragW.toDp() },
                                    height = with(LocalDensity.current) { dragH.toDp() }
                                )
                                .zIndex(999f)
                                .graphicsLayer {
                                    alpha = 0.88f
                                    shadowElevation = 20f
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.5.dp, ScannerGreen, RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = dragController.draggedBitmap!!.asImageBitmap(),
                                contentDescription = "Moving Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Integrated Floating Window with All Controls
                    if (selectedSheetIndex != null && selectedSlotIndex != null && !dragController.isDragging) {
                        val sIdx = selectedSheetIndex!!
                        val slotIdx = selectedSlotIndex!!
                        val currentSheet = sheets.getOrNull(sIdx)
                        if (currentSheet != null) {
                            FloatingTransformToolbar(
                                sheetIndex = sIdx,
                                slotIndex = slotIdx,
                                sheet = currentSheet,
                                totalSheets = sheets.size,
                                onCrop = {
                                    val p = currentSheet.slotAssignments[slotIdx]
                                    if (p != null) {
                                        cropEditingSheetIndex = sIdx
                                        cropEditingSlotIndex = slotIdx
                                        cropEditingPage = p
                                    }
                                },
                                onMoveToPage = { targetSheetIdx ->
                                    if (targetSheetIdx != sIdx && targetSheetIdx in 0 until sheets.size) {
                                        val targetSheet = sheets[targetSheetIdx]
                                        val maxSlots = selectedTemplate.layout.maxPages
                                        val emptySlot = (0 until maxSlots).firstOrNull { targetSheet.slotAssignments[it] == null } ?: 0
                                        val srcPage = currentSheet.slotAssignments[slotIdx]
                                        val srcCrop = currentSheet.slotCroppedPaths[slotIdx]
                                        val tgtPage = targetSheet.slotAssignments[emptySlot]
                                        val tgtCrop = targetSheet.slotCroppedPaths[emptySlot]

                                        if (srcPage != null) {
                                            targetSheet.slotAssignments[emptySlot] = srcPage
                                            if (srcCrop != null) targetSheet.slotCroppedPaths[emptySlot] = srcCrop
                                            else targetSheet.slotCroppedPaths.remove(emptySlot)
                                            targetSheet.slotTransforms[emptySlot] = SlotTransform()
                                        }

                                        if (tgtPage != null) {
                                            currentSheet.slotAssignments[slotIdx] = tgtPage
                                            if (tgtCrop != null) currentSheet.slotCroppedPaths[slotIdx] = tgtCrop
                                            else currentSheet.slotCroppedPaths.remove(slotIdx)
                                            currentSheet.slotTransforms[slotIdx] = SlotTransform()
                                            Toast.makeText(context, "Swapped with Page ${targetSheetIdx + 1}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            currentSheet.slotAssignments.remove(slotIdx)
                                            currentSheet.slotCroppedPaths.remove(slotIdx)
                                            currentSheet.slotTransforms.remove(slotIdx)
                                            Toast.makeText(context, "Moved to Page ${targetSheetIdx + 1}", Toast.LENGTH_SHORT).show()
                                        }

                                        selectedSheetIndex = targetSheetIdx
                                        selectedSlotIndex = emptySlot
                                        scope.launch {
                                            listState.animateScrollToItem(targetSheetIdx)
                                        }
                                    }
                                },
                                onAddNewPageAndMove = {
                                    val newSheet = CollageSheetState()
                                    sheets.add(newSheet)
                                    val targetSheetIdx = sheets.size - 1
                                    val srcPage = currentSheet.slotAssignments[slotIdx]
                                    val srcCrop = currentSheet.slotCroppedPaths[slotIdx]
                                    if (srcPage != null) {
                                        newSheet.slotAssignments[0] = srcPage
                                        if (srcCrop != null) newSheet.slotCroppedPaths[0] = srcCrop
                                        newSheet.slotTransforms[0] = SlotTransform()
                                        currentSheet.slotAssignments.remove(slotIdx)
                                        currentSheet.slotCroppedPaths.remove(slotIdx)
                                        currentSheet.slotTransforms.remove(slotIdx)
                                    }
                                    selectedSheetIndex = targetSheetIdx
                                    selectedSlotIndex = 0
                                    scope.launch {
                                        listState.animateScrollToItem(targetSheetIdx)
                                    }
                                    Toast.makeText(context, "Moved to new Page ${sheets.size}", Toast.LENGTH_SHORT).show()
                                },
                                onClose = {
                                    selectedSheetIndex = null
                                    selectedSlotIndex = null
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }

            // Full Watermark Customization Dialog
            if (showWatermarkInput) {
                WatermarkCustomizationDialog(
                    initialConfig = watermarkConfig,
                    onDismiss = { showWatermarkInput = false },
                    onSave = { updated ->
                        watermarkConfig = updated
                        showWatermarkInput = false
                    }
                )
            }

            // Modal CropScreen
            cropEditingPage?.let { pageToCrop ->
                val rawPath = pageToCrop.originalImagePath.ifEmpty { pageToCrop.processedImagePath }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    CropScreen(
                        imagePath = rawPath,
                        docId = pageToCrop.documentId,
                        onCropConfirmed = { quad ->
                            val sheetIndex = cropEditingSheetIndex ?: return@CropScreen
                            val slotIndex = cropEditingSlotIndex ?: return@CropScreen
                            val targetSheet = sheets.getOrNull(sheetIndex) ?: return@CropScreen

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val rawBitmap = BitmapFactory.decodeFile(rawPath)
                                    if (rawBitmap != null) {
                                        val targetW = Math.round(
                                            maxOf(
                                                Math.hypot((quad.topRight.x - quad.topLeft.x).toDouble(), (quad.topRight.y - quad.topLeft.y).toDouble()),
                                                Math.hypot((quad.bottomRight.x - quad.bottomLeft.x).toDouble(), (quad.bottomRight.y - quad.bottomLeft.y).toDouble())
                                            )
                                        ).toInt().coerceAtLeast(100)
                                        val targetH = Math.round(
                                            maxOf(
                                                Math.hypot((quad.bottomLeft.x - quad.topLeft.x).toDouble(), (quad.bottomLeft.y - quad.topLeft.y).toDouble()),
                                                Math.hypot((quad.bottomRight.x - quad.topRight.x).toDouble(), (quad.bottomRight.y - quad.topRight.y).toDouble())
                                            )
                                        ).toInt().coerceAtLeast(100)

                                        val warped = NativeScannerEngine.warpDocument(rawBitmap, quad, targetW, targetH)
                                        val croppedFile = File(
                                            context.cacheDir,
                                            "collage_crop_${pageToCrop.id}_${slotIndex}_${System.currentTimeMillis()}.jpg"
                                        )
                                        FileOutputStream(croppedFile).use { out ->
                                            warped.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }
                                        CollageBitmapCache.put(croppedFile.absolutePath, warped)
                                        if (!rawBitmap.isRecycled) {
                                            rawBitmap.recycle()
                                        }
                                        withContext(Dispatchers.Main) {
                                            targetSheet.slotCroppedPaths[slotIndex] = croppedFile.absolutePath
                                            cropEditingSheetIndex = null
                                            cropEditingSlotIndex = null
                                            cropEditingPage = null
                                            Toast.makeText(context, "Slot ${slotIndex + 1} cropped successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        cropEditingSheetIndex = null
                                        cropEditingSlotIndex = null
                                        cropEditingPage = null
                                    }
                                }
                            }
                        },
                        onClose = {
                            cropEditingSheetIndex = null
                            cropEditingSlotIndex = null
                            cropEditingPage = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Isolated Floating Window hosting all controls:
 * - Row 1: P:S Badge, Crop button, Zoom scrubber, Rotation scrubber, single 90° button, Reset button, Close button.
 * - Row 2: X, Y, Z Scrubbers (click, hold & drag with up=+, down=-, right=+, left=-) + Center (0,0) alignment button.
 * Being an isolated composable, state reads here never recompose the parent canvas or LazyColumn!
 */
@Composable
private fun FloatingTransformToolbar(
    sheetIndex: Int,
    slotIndex: Int,
    sheet: CollageSheetState,
    totalSheets: Int = 1,
    onCrop: () -> Unit,
    onMoveToPage: ((Int) -> Unit)? = null,
    onAddNewPageAndMove: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val curTransform = sheet.slotTransforms[slotIndex] ?: SlotTransform()
    var showMoveMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        border = BorderStroke(1.2.dp, ScannerGreen),
        shadowElevation = 14.dp,
        modifier = modifier.padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Badge, Crop, Move to Page, Zoom Scrubber, Rotation Scrubber, Single 90° button, Reset, Close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ScannerGreen.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Text(
                        text = "P${sheetIndex + 1}:S${slotIndex + 1}",
                        color = ScannerGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Crop
                IconButton(onClick = onCrop, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Crop, contentDescription = "Crop", tint = Color.White, modifier = Modifier.size(17.dp))
                }

                // Move to Page Dropdown Menu
                if (onMoveToPage != null) {
                    Box {
                        IconButton(onClick = { showMoveMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.DriveFileMove,
                                contentDescription = "Move to Page",
                                tint = ScannerGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoveMenu,
                            onDismissRequest = { showMoveMenu = false },
                            modifier = Modifier.background(Color(0xFF222222))
                        ) {
                            for (pIdx in 0 until totalSheets) {
                                if (pIdx != sheetIndex) {
                                    DropdownMenuItem(
                                        text = { Text("Move to Page ${pIdx + 1}", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            showMoveMenu = false
                                            onMoveToPage(pIdx)
                                        }
                                    )
                                }
                            }
                            if (onAddNewPageAndMove != null) {
                                DropdownMenuItem(
                                    text = { Text("+ Move to New Page", color = ScannerGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                    onClick = {
                                        showMoveMenu = false
                                        onAddNewPageAndMove()
                                    }
                                )
                            }
                        }
                    }
                }

                // Zoom Scrubber (Drag Up/Right = +, Down/Left = -)
                ValueScrubber(
                    label = "Zoom",
                    value = curTransform.scale * 100f,
                    displayValue = "${(curTransform.scale * 100).toInt()}%",
                    step = 1f,
                    minValue = 20f,
                    maxValue = 500f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(scale = it / 100f) }
                )

                // Rotation Scrubber (Drag Up/Right = +, Down/Left = -)
                ValueScrubber(
                    label = "Rot",
                    value = curTransform.rotation,
                    displayValue = "${curTransform.rotation.toInt()}°",
                    step = 1f,
                    minValue = 0f,
                    maxValue = 360f,
                    onValueChange = {
                        val r = ((it % 360f) + 360f) % 360f
                        sheet.slotTransforms[slotIndex] = curTransform.copy(rotation = r)
                    }
                )

                // Exactly ONE 90° rotate button
                IconButton(
                    onClick = {
                        val newRot = (curTransform.rotation + 90f) % 360f
                        sheet.slotTransforms[slotIndex] = curTransform.copy(rotation = newRot)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "+90°", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                // Reset
                IconButton(
                    onClick = { sheet.slotTransforms[slotIndex] = SlotTransform() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset", tint = Color.LightGray, modifier = Modifier.size(17.dp))
                }

                // Close / Deselect
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // Row 2: X, Y, Z Scrubbers (click, hold & move) + Center (0,0) alignment button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // X Scrubber (Pan X)
                ValueScrubber(
                    label = "X",
                    value = curTransform.panX * 100f,
                    displayValue = "${(curTransform.panX * 100).toInt()}%",
                    step = 0.5f,
                    minValue = -100f,
                    maxValue = 100f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(panX = it / 100f) }
                )

                // Y Scrubber (Pan Y)
                ValueScrubber(
                    label = "Y",
                    value = curTransform.panY * 100f,
                    displayValue = "${(curTransform.panY * 100).toInt()}%",
                    step = 0.5f,
                    minValue = -100f,
                    maxValue = 100f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(panY = it / 100f) }
                )

                // Z Scrubber (Layer / Stacking Order)
                ValueScrubber(
                    label = "Z",
                    value = curTransform.zIndex,
                    displayValue = "${curTransform.zIndex.toInt()}",
                    step = 1f,
                    minValue = 0f,
                    maxValue = 50f,
                    onValueChange = { sheet.slotTransforms[slotIndex] = curTransform.copy(zIndex = it.coerceIn(0f, 50f)) }
                )

                // Center alignment button
                IconButton(
                    onClick = { sheet.slotTransforms[slotIndex] = curTransform.copy(panX = 0f, panY = 0f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Center X/Y", tint = ScannerGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Click, hold & drag value scrubber:
 * - Moving UP or RIGHT increases value (+)
 * - Moving DOWN or LEFT decreases value (-)
 * - Live real-time preview
 * - Tap to open direct numerical input
 */
@Composable
private fun ValueScrubber(
    label: String,
    value: Float,
    displayValue: String,
    step: Float = 1f,
    minValue: Float = -Float.MAX_VALUE,
    maxValue: Float = Float.MAX_VALUE,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var showDirectInput by remember { mutableStateOf(false) }
    var directInputText by remember { mutableStateOf("") }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDragging) Color(0xFF26332B) else Color(0xFF282828),
        border = BorderStroke(1.dp, if (isDragging) ScannerGreen else Color(0xFF444444)),
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        var accumulatedValue = currentValue
                        var totalMovedX = 0f
                        var totalMovedY = 0f
                        var dragStarted = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (dragStarted) {
                                    isDragging = false
                                } else {
                                    // Tap without dragging opens direct manual input
                                    directInputText = "${currentValue.toInt()}"
                                    showDirectInput = true
                                }
                                break
                            }

                            val change = pressed[0].positionChange()
                            totalMovedX += change.x
                            totalMovedY += change.y

                            if (!dragStarted && (kotlin.math.abs(totalMovedX) > 6f || kotlin.math.abs(totalMovedY) > 6f)) {
                                dragStarted = true
                                isDragging = true
                            }

                            if (dragStarted) {
                                // Up = +, Down = -, Right = +, Left = -
                                // In Android screen coordinates: dy < 0 is UP (-dy is +), dx > 0 is RIGHT (dx is +)
                                val netChange = change.x - change.y
                                val delta = (netChange / 4f) * step
                                accumulatedValue = (accumulatedValue + delta).coerceIn(minValue, maxValue)
                                currentOnValueChange(accumulatedValue)
                                pressed[0].consume()
                            }
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "$label:",
                color = ScannerGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = displayValue,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showDirectInput) {
        AlertDialog(
            onDismissRequest = { showDirectInput = false },
            title = { Text("Set $label Value", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = directInputText,
                    onValueChange = { directInputText = it },
                    label = { Text("Enter number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        directInputText.toFloatOrNull()?.let { v ->
                            onValueChange(v.coerceIn(minValue, maxValue))
                        }
                        showDirectInput = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Renders a single collage sheet card with its page header, white canvas, floating items, and snapping guidelines.
 */
@Composable
private fun CollageSheetCard(
    sheetIndex: Int,
    totalSheets: Int,
    sheetState: CollageSheetState,
    layout: CollageLayout,
    aspectRatio: Float,
    isSheetSelected: Boolean,
    selectedSlotIndex: Int?,
    watermarkConfig: CollageWatermarkConfig,
    dragController: CrossPageDragController,
    onCommitCrossDrop: () -> Unit,
    onSelectSlot: (Int) -> Unit,
    onDeselect: () -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onDeleteSheet: () -> Unit,
    onTransformChange: (Int, SlotTransform) -> Unit
) {
    var sheetSize by remember { mutableStateOf(IntSize.Zero) }
    val snapGuidesState = remember { SnapGuidesState() }
    val colors = ScannerTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Page ${sheetIndex + 1} of $totalSheets",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (totalSheets > 1) {
                IconButton(
                    onClick = onDeleteSheet,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Page",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // White Canvas Sheet (Pure white background, clean shadow, no rigid boxes)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .onSizeChanged { sheetSize = it }
                .onGloballyPositioned { coords ->
                    dragController.sheetBoundsInRoot[sheetIndex] = coords.boundsInRoot()
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 0: Tap background to deselect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onDeselect()
                        }
                )

                if (sheetSize.width > 0 && sheetSize.height > 0) {
                    val sw = sheetSize.width.toFloat()
                    val sh = sheetSize.height.toFloat()
                    val slots = CollageGenerator.calculateSlotRects(layout, sheetSize.width, sheetSize.height)
                    val slotCount = layout.maxPages

                    // Render each slot item
                    for (i in 0 until minOf(slotCount, slots.size)) {
                        val initRect = slots[i]
                        val slotW = (initRect.right - initRect.left).toFloat()
                        val slotH = (initRect.bottom - initRect.top).toFloat()
                        val initCx = (initRect.left + initRect.right) / 2f
                        val initCy = (initRect.top + initRect.bottom) / 2f

                        val page = sheetState.slotAssignments[i]
                        val customPath = sheetState.slotCroppedPaths[i]
                        val isSelected = isSheetSelected && selectedSlotIndex == i

                        val isDropHovered = dragController.isDragging &&
                            dragController.hoverSheetIndex == sheetIndex &&
                            dragController.hoverSlotIndex == i &&
                            (dragController.sourceSheetIndex != sheetIndex || dragController.sourceSlotIndex != i)

                        if (page != null || !customPath.isNullOrBlank()) {
                            PhotoshopCanvasItem(
                                sheetIndex = sheetIndex,
                                slotIndex = i,
                                page = page,
                                customImagePath = customPath,
                                layout = layout,
                                dragController = dragController,
                                onCommitCrossDrop = onCommitCrossDrop,
                                isDropHovered = isDropHovered,
                                initCx = initCx,
                                initCy = initCy,
                                slotWidth = slotW,
                                slotHeight = slotH,
                                sheetWidth = sw,
                                sheetHeight = sh,
                                getTransform = { sheetState.slotTransforms[i] ?: SlotTransform() },
                                isSelected = isSelected,
                                onSelect = { onSelectSlot(i) },
                                onTransformChange = { newTrans -> onTransformChange(i, newTrans) },
                                onDelete = { onDeleteSlot(i) },
                                onSnapChange = { snapV, snapH ->
                                    snapGuidesState.snapVertical = snapV
                                    snapGuidesState.snapHorizontal = snapH
                                }
                            )
                        } else {
                            // Blank slot placeholder box
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(initRect.left, initRect.top) }
                                    .size(
                                        width = with(LocalDensity.current) { slotW.toDp() },
                                        height = with(LocalDensity.current) { slotH.toDp() }
                                    )
                                    .padding(6.dp)
                                    .border(
                                        width = if (isDropHovered) 2.5.dp else 1.dp,
                                        color = if (isDropHovered) ScannerGreen else Color(0xFFDDDDDD),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .background(if (isDropHovered) ScannerGreen.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { onSelectSlot(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (isDropHovered) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = if (isDropHovered) ScannerGreen else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isDropHovered) "Drop Here" else "Add image",
                                        color = if (isDropHovered) ScannerGreen else Color.Gray,
                                        fontWeight = if (isDropHovered) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Magnetic Snapping Guidelines Overlay (Dashed Green Lines)
                if (isSheetSelected) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                        if (snapGuidesState.snapVertical) {
                            drawLine(
                                color = ScannerGreen,
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }
                        if (snapGuidesState.snapHorizontal) {
                            drawLine(
                                color = ScannerGreen,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }
                    }
                }

                // Watermark overlay
                if (watermarkConfig.text.isNotBlank()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = watermarkConfig.color
                            alpha = (watermarkConfig.opacity * 255).toInt().coerceIn(0, 255)
                            textSize = watermarkConfig.fontSizeSp * (size.width / 400f)
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }

                        if (watermarkConfig.isTiled) {
                            val stepX = size.width / 3f
                            val stepY = size.height / 5f
                            for (y in 0..5) {
                                for (x in 0..3) {
                                    val px = x * stepX + (if (y % 2 == 1) stepX / 2f else 0f)
                                    val py = y * stepY
                                    rotate(watermarkConfig.rotationAngle, Offset(px, py)) {
                                        drawContext.canvas.nativeCanvas.drawText(watermarkConfig.text, px, py, paint)
                                    }
                                }
                            }
                        } else {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            rotate(watermarkConfig.rotationAngle, Offset(cx, cy)) {
                                drawContext.canvas.nativeCanvas.drawText(watermarkConfig.text, cx, cy, paint)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Maps local coordinates inside an item Box to invariant root screen coordinates,
 * taking into account the live graphicsLayer translation, scale, and rotation.
 */
private fun mapLocalItemToRoot(
    localPos: Offset,
    attachedCoords: LayoutCoordinates,
    baseWidth: Float,
    baseHeight: Float,
    panX: Float,
    panY: Float,
    scale: Float,
    rotationDeg: Float,
    sheetWidth: Float,
    sheetHeight: Float
): Offset {
    val cx = baseWidth / 2f
    val cy = baseHeight / 2f
    val rad = Math.toRadians(rotationDeg.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    val dx = (localPos.x - cx) * scale
    val dy = (localPos.y - cy) * scale

    val rotX = dx * cosR - dy * sinR
    val rotY = dx * sinR + dy * cosR

    val parentX = cx + rotX + panX * sheetWidth
    val parentY = cy + rotY + panY * sheetHeight
    return attachedCoords.localToRoot(Offset(parentX, parentY))
}

/**
 * Freeform Photoshop-style Canvas Item without rigid boxes.
 * Features:
 * - Border wraps EXACTLY to image size, not slot/column size.
 * - Corner handle simultaneously SCALES and ROTATES image smoothly.
 * - 1-finger move with magnetic snapping to vertical/horizontal center and edges.
 * - Red (X) delete handle on top-left.
 * - Zero flickering: transformations executed in graphicsLayer (Draw Phase) reading live state in invariant root coordinates,
 *   so NO recomposition occurs during motion. Synchronous in-memory bitmap cache ensures zero blank frames.
 * - Cross-page dragging: moving beyond drag threshold activates cross-page drag coordinator.
 */
@Composable
private fun PhotoshopCanvasItem(
    sheetIndex: Int,
    slotIndex: Int,
    page: Page?,
    customImagePath: String?,
    layout: CollageLayout,
    dragController: CrossPageDragController,
    onCommitCrossDrop: () -> Unit,
    isDropHovered: Boolean,
    initCx: Float,
    initCy: Float,
    slotWidth: Float,
    slotHeight: Float,
    sheetWidth: Float,
    sheetHeight: Float,
    getTransform: () -> SlotTransform,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTransformChange: (SlotTransform) -> Unit,
    onDelete: () -> Unit,
    onSnapChange: (Boolean, Boolean) -> Unit
) {
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnSnapChange by rememberUpdatedState(onSnapChange)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnCommitCrossDrop by rememberUpdatedState(onCommitCrossDrop)

    // Synchronous cached bitmap lookup to eliminate all image loading flickers
    val imagePath = customImagePath ?: page?.processedImagePath
    var cachedBitmap by remember(imagePath) {
        mutableStateOf(if (!imagePath.isNullOrBlank()) CollageBitmapCache.get(imagePath) else null)
    }

    LaunchedEffect(imagePath) {
        if (cachedBitmap == null && !imagePath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val bmp = CollageBitmapCache.getOrDecode(imagePath, 1024, 1024)
                withContext(Dispatchers.Main) {
                    cachedBitmap = bmp
                }
            }
        }
    }

    val imageBitmap = remember(cachedBitmap) {
        cachedBitmap?.asImageBitmap()
    }

    val bmpW = (cachedBitmap?.width ?: slotWidth.toInt()).toFloat().coerceAtLeast(10f)
    val bmpH = (cachedBitmap?.height ?: slotHeight.toInt()).toFloat().coerceAtLeast(10f)
    val fitScale = minOf(slotWidth / bmpW, slotHeight / bmpH)
    val baseWidth = bmpW * fitScale
    val baseHeight = bmpH * fitScale

    val curTransform = getTransform()
    var livePanX by remember { mutableFloatStateOf(curTransform.panX) }
    var livePanY by remember { mutableFloatStateOf(curTransform.panY) }
    var liveScale by remember { mutableFloatStateOf(curTransform.scale) }
    var liveRotation by remember { mutableFloatStateOf(curTransform.rotation) }
    var liveZIndex by remember { mutableFloatStateOf(curTransform.zIndex) }

    // Keep live values synced when external changes occur (e.g., toolbar reset or scrubber)
    LaunchedEffect(curTransform) {
        livePanX = curTransform.panX
        livePanY = curTransform.panY
        liveScale = curTransform.scale
        liveRotation = curTransform.rotation
        liveZIndex = curTransform.zIndex
    }

    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Check if this item is currently being dragged across pages
    val isBeingCrossDragged = dragController.isDragging &&
        dragController.sourceSheetIndex == sheetIndex &&
        dragController.sourceSlotIndex == slotIndex &&
        (dragController.hoverSheetIndex != sheetIndex || dragController.hoverSlotIndex != slotIndex)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (initCx - (baseWidth / 2f)).toInt(),
                    (initCy - (baseHeight / 2f)).toInt()
                )
            }
            .size(
                width = with(density) { baseWidth.toDp() },
                height = with(density) { baseHeight.toDp() }
            )
            .zIndex(if (isSelected) liveZIndex + 10f else liveZIndex)
            .onGloballyPositioned { itemCoordinates = it }
            .graphicsLayer {
                // Reading live values purely in Draw Phase -> ZERO recomposition during motion!
                translationX = livePanX * sheetWidth
                translationY = livePanY * sheetHeight
                scaleX = liveScale
                scaleY = liveScale
                rotationZ = liveRotation
                alpha = if (isBeingCrossDragged) 0.35f else 1.0f
            }
    ) {
        // Image content with gesture handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slotIndex) {
                    val SNAP_THRESHOLD_PX = 18f

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            currentOnSelect()
                            down.consume()

                            val coords = itemCoordinates
                            if (coords == null || !coords.isAttached) continue

                            val startPanX = livePanX
                            val startPanY = livePanY
                            val startScale = liveScale
                            val startRotation = liveRotation

                            val startRootPos = mapLocalItemToRoot(
                                down.position,
                                coords,
                                baseWidth,
                                baseHeight,
                                startPanX,
                                startPanY,
                                startScale,
                                startRotation,
                                sheetWidth,
                                sheetHeight
                            )
                            var prevRootPos = startRootPos
                            var isCrossDragging = false
                            val sheetBounds = dragController.sheetBoundsInRoot[sheetIndex]

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    currentOnSnapChange(false, false)
                                    if (isCrossDragging && dragController.isDragging) {
                                        val tgtSheet = dragController.hoverSheetIndex
                                        val tgtSlot = dragController.hoverSlotIndex
                                        if (tgtSheet != null && tgtSlot != null && (tgtSheet != sheetIndex || tgtSlot != slotIndex)) {
                                            currentOnCommitCrossDrop()
                                        } else {
                                            dragController.cancelDrag()
                                            currentOnTransformChange(
                                                SlotTransform(
                                                    scale = liveScale,
                                                    panX = livePanX,
                                                    panY = livePanY,
                                                    rotation = (liveRotation % 360f + 360f) % 360f,
                                                    zIndex = liveZIndex
                                                )
                                            )
                                        }
                                    } else {
                                        // Commit final transform state
                                        currentOnTransformChange(
                                            SlotTransform(
                                                scale = liveScale,
                                                panX = livePanX,
                                                panY = livePanY,
                                                rotation = (liveRotation % 360f + 360f) % 360f,
                                                zIndex = liveZIndex
                                            )
                                        )
                                    }
                                    break
                                }

                                val count = pressed.size
                                val attachedCoords = itemCoordinates
                                if (attachedCoords != null && attachedCoords.isAttached) {
                                    if (count == 1) {
                                        val curRoot = mapLocalItemToRoot(
                                            pressed[0].position,
                                            attachedCoords,
                                            baseWidth,
                                            baseHeight,
                                            livePanX,
                                            livePanY,
                                            liveScale,
                                            liveRotation,
                                            sheetWidth,
                                            sheetHeight
                                        )
                                        val totalRootDx = curRoot.x - startRootPos.x
                                        val totalRootDy = curRoot.y - startRootPos.y
                                        val totalDist = kotlin.math.hypot(totalRootDx, totalRootDy)

                                        // Activate cross-page dragging if moving outside current sheet or dragging far across sheets
                                        val isOutsideSheet = sheetBounds != null && !sheetBounds.contains(curRoot)
                                        if (isOutsideSheet || totalDist > 140f) {
                                            if (!isCrossDragging) {
                                                isCrossDragging = true
                                                dragController.startDrag(
                                                    sheetIndex = sheetIndex,
                                                    slotIndex = slotIndex,
                                                    page = page,
                                                    customPath = customImagePath,
                                                    bitmap = cachedBitmap,
                                                    itemSize = IntSize(baseWidth.toInt(), baseHeight.toInt()),
                                                    rootPos = curRoot
                                                )
                                            }
                                            dragController.updateDragPosition(curRoot, layout)
                                        } else if (isCrossDragging) {
                                            isCrossDragging = false
                                            dragController.cancelDrag()
                                        }

                                        // Pan using invariant root displacement
                                        var newPanX = startPanX + (totalRootDx / sheetWidth.coerceAtLeast(1f))
                                        var newPanY = startPanY + (totalRootDy / sheetHeight.coerceAtLeast(1f))

                                        // Magnetic center and edge snapping
                                        val curCenterX = initCx + newPanX * sheetWidth
                                        val curCenterY = initCy + newPanY * sheetHeight
                                        val pageCenterX = sheetWidth / 2f
                                        val pageCenterY = sheetHeight / 2f

                                        var snapV = false
                                        var snapH = false

                                        if (kotlin.math.abs(curCenterX - pageCenterX) < SNAP_THRESHOLD_PX) {
                                            newPanX = (pageCenterX - initCx) / sheetWidth
                                            snapV = true
                                        }
                                        if (kotlin.math.abs(curCenterY - pageCenterY) < SNAP_THRESHOLD_PX) {
                                            newPanY = (pageCenterY - initCy) / sheetHeight
                                            snapH = true
                                        }

                                        // Page boundary snapping
                                        val halfW = (baseWidth * liveScale) / 2f
                                        val halfH = (baseHeight * liveScale) / 2f
                                        if (kotlin.math.abs(curCenterX - halfW) < SNAP_THRESHOLD_PX) {
                                            newPanX = (halfW - initCx) / sheetWidth
                                        }
                                        if (kotlin.math.abs((curCenterX + halfW) - sheetWidth) < SNAP_THRESHOLD_PX) {
                                            newPanX = (sheetWidth - halfW - initCx) / sheetWidth
                                        }
                                        if (kotlin.math.abs(curCenterY - halfH) < SNAP_THRESHOLD_PX) {
                                            newPanY = (halfH - initCy) / sheetHeight
                                        }
                                        if (kotlin.math.abs((curCenterY + halfH) - sheetHeight) < SNAP_THRESHOLD_PX) {
                                            newPanY = (sheetHeight - halfH - initCy) / sheetHeight
                                        }

                                        livePanX = newPanX
                                        livePanY = newPanY
                                        currentOnSnapChange(snapV, snapH)
                                        pressed[0].consume()
                                    } else if (count >= 2) {
                                        if (isCrossDragging) {
                                            isCrossDragging = false
                                            dragController.cancelDrag()
                                        }
                                        val p0 = mapLocalItemToRoot(
                                            pressed[0].position,
                                            attachedCoords,
                                            baseWidth,
                                            baseHeight,
                                            livePanX,
                                            livePanY,
                                            liveScale,
                                            liveRotation,
                                            sheetWidth,
                                            sheetHeight
                                        )
                                        val p1 = mapLocalItemToRoot(
                                            pressed[1].position,
                                            attachedCoords,
                                            baseWidth,
                                            baseHeight,
                                            livePanX,
                                            livePanY,
                                            liveScale,
                                            liveRotation,
                                            sheetWidth,
                                            sheetHeight
                                        )
                                        val centroid = (p0 + p1) / 2f
                                        val rootDelta = centroid - prevRootPos
                                        prevRootPos = centroid

                                        val zoomChange = event.calculateZoom()
                                        val rotationChange = event.calculateRotation()

                                        liveScale = (liveScale * zoomChange).coerceIn(0.2f, 5.0f)
                                        liveRotation = (liveRotation + rotationChange) % 360f
                                        livePanX += rootDelta.x / sheetWidth.coerceAtLeast(1f)
                                        livePanY += rootDelta.y / sheetHeight.coerceAtLeast(1f)

                                        pressed.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Image ${slotIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (page != null) {
                AsyncImage(
                    model = File(imagePath.orEmpty()),
                    contentDescription = "Image ${slotIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Drop target indicator overlay when another item is dragged over this slot
        if (isDropHovered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.5.dp, ScannerGreen, RoundedCornerShape(2.dp))
                    .background(ScannerGreen.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E2822),
                    border = BorderStroke(1.dp, ScannerGreen)
                ) {
                    Text(
                        text = "Swap",
                        color = ScannerGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Selection Border & Corner Handles
        if (isSelected && !dragController.isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, ScannerGreen, RoundedCornerShape(2.dp))
            )

            // Top-Left: Red circular delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-11).dp, y = (-11).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { currentOnDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Bottom-Right: Green circular handle that SCALES and ROTATES simultaneously
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 11.dp, y = 11.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ScannerGreen)
                    .pointerInput(slotIndex) {
                        val handleOffsetPx = with(density) { 11.dp.toPx() }
                        val handleSizePx = with(density) { 28.dp.toPx() }
                        val handleTopLeft = Offset(
                            baseWidth - handleSizePx + handleOffsetPx,
                            baseHeight - handleSizePx + handleOffsetPx
                        )

                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                val iCoords = itemCoordinates
                                if (iCoords == null || !iCoords.isAttached) {
                                    continue
                                }

                                val centerParent = Offset(
                                    baseWidth / 2f + livePanX * sheetWidth,
                                    baseHeight / 2f + livePanY * sheetHeight
                                )
                                val centerRoot = iCoords.localToRoot(centerParent)
                                val downRoot = mapLocalItemToRoot(
                                    handleTopLeft + down.position,
                                    iCoords,
                                    baseWidth,
                                    baseHeight,
                                    livePanX,
                                    livePanY,
                                    liveScale,
                                    liveRotation,
                                    sheetWidth,
                                    sheetHeight
                                )
                                val initVec = downRoot - centerRoot
                                val initialRadius = kotlin.math.hypot(initVec.x, initVec.y).coerceAtLeast(1f)
                                val initialAngleRad = kotlin.math.atan2(initVec.y.toDouble(), initVec.x.toDouble())

                                val startScale = liveScale
                                val startRotation = liveRotation

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        currentOnTransformChange(
                                            SlotTransform(
                                                scale = liveScale,
                                                panX = livePanX,
                                                panY = livePanY,
                                                rotation = (liveRotation % 360f + 360f) % 360f,
                                                zIndex = liveZIndex
                                            )
                                        )
                                        break
                                    }

                                    val curCoords = itemCoordinates
                                    if (curCoords != null && curCoords.isAttached) {
                                        val curCenterRoot = curCoords.localToRoot(
                                            Offset(
                                                baseWidth / 2f + livePanX * sheetWidth,
                                                baseHeight / 2f + livePanY * sheetHeight
                                            )
                                        )
                                        val curRoot = mapLocalItemToRoot(
                                            handleTopLeft + pressed[0].position,
                                            curCoords,
                                            baseWidth,
                                            baseHeight,
                                            livePanX,
                                            livePanY,
                                            liveScale,
                                            liveRotation,
                                            sheetWidth,
                                            sheetHeight
                                        )
                                        val curVec = curRoot - curCenterRoot
                                        val curRadius = kotlin.math.hypot(curVec.x, curVec.y)
                                        val curAngleRad = kotlin.math.atan2(curVec.y.toDouble(), curVec.x.toDouble())

                                        val scaleFactor = curRadius / initialRadius
                                        liveScale = (startScale * scaleFactor).coerceIn(0.2f, 5.0f)

                                        val deltaAngleDeg = Math.toDegrees(curAngleRad - initialAngleRad).toFloat()
                                        liveRotation = (startRotation + deltaAngleDeg) % 360f
                                    }
                                    pressed[0].consume()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.OpenInFull,
                    contentDescription = "Resize and Rotate",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WatermarkCustomizationDialog(
    initialConfig: CollageWatermarkConfig,
    onDismiss: () -> Unit,
    onSave: (CollageWatermarkConfig) -> Unit
) {
    val initialText = if (initialConfig.text.isBlank()) "CONFIDENTIAL" else initialConfig.text
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length)
            )
        )
    }

    var selectedColor by remember { mutableIntStateOf(initialConfig.color) }
    var opacity by remember { mutableFloatStateOf(initialConfig.opacity) }
    var fontSizeSp by remember { mutableFloatStateOf(initialConfig.fontSizeSp) }
    var rotationAngle by remember { mutableFloatStateOf(initialConfig.rotationAngle) }
    var isTiled by remember { mutableStateOf(initialConfig.isTiled) }

    val presetColors = listOf(
        0xFF111111.toInt(), // Black
        0xFF888888.toInt(), // Gray
        0xFFD32F2F.toInt(), // Red
        0xFF1976D2.toInt(), // Blue
        0xFF00A86B.toInt()  // Emerald
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Watermark Studio",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("Watermark Text") },
                    placeholder = { Text("CONFIDENTIAL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Color:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    presetColors.forEach { c ->
                        val isPicked = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) ScannerGreen else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPicked) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Opacity Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", fontSize = 12.sp)
                        Text("${(opacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.10f..0.90f
                    )
                }

                // Font Size Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size", fontSize = 12.sp)
                        Text("${fontSizeSp.toInt()} sp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 16f..64f
                    )
                }

                // Rotation Angle Slider & Quick Angle Presets
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rotation Angle", fontSize = 12.sp)
                        Text("${rotationAngle.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = rotationAngle,
                        onValueChange = { rotationAngle = it },
                        valueRange = -90f..90f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-45f, 0f, 45f, 90f).forEach { ang ->
                            FilterChip(
                                selected = rotationAngle == ang,
                                onClick = { rotationAngle = ang },
                                label = { Text("${ang.toInt()}°", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Tiling Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Repeat Tile Pattern", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Multiply across whole sheet", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isTiled,
                        onCheckedChange = { isTiled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CollageWatermarkConfig(
                            text = textFieldValue.text,
                            color = selectedColor,
                            fontSizeSp = fontSizeSp,
                            opacity = opacity,
                            rotationAngle = rotationAngle,
                            isTiled = isTiled
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
            ) {
                Text("Apply", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSave(initialConfig.copy(text = ""))
                }
            ) {
                Text("Remove")
            }
        }
    )
}

@Composable
private fun TemplateVisualPreview(id: String, isSelected: Boolean) {
    val borderColor = if (isSelected) ScannerGreen else Color(0xFF777777)
    when (id) {
        "4_grid" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
        "6_grid" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
        "3x1" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
            }
        }
        "3_top_two" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
        "driver_license" -> {
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth(0.92f).weight(1f)
                ) {}
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth(0.92f).weight(1f)
                ) {}
            }
        }
        "passport", "1x2" -> {
            Row(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
            }
        }
        "question_book" -> {
            Row(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxHeight().weight(1f)
                ) {}
            }
        }
        else -> { // 2x1 and others
            Column(
                modifier = Modifier
                    .size(26.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().weight(1f)) {}
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().weight(1f)) {}
            }
        }
    }
}

@Composable
private fun CollageBottomActionItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) ScannerGreen else colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) ScannerGreen else colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

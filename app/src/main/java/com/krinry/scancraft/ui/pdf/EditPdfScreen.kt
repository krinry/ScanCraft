package com.krinry.scancraft.ui.pdf

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.pdf.PdfPageSize
import com.krinry.scancraft.pdf.PdfQuality
import com.krinry.scancraft.pdf.export.PdfExportMode
import com.krinry.scancraft.pdf.export.PdfStudioExporter
import com.krinry.scancraft.pdf.export.StudioExportConfig
import com.krinry.scancraft.pdf.model.*
import com.krinry.scancraft.pdf.signature.SavedSignature
import com.krinry.scancraft.pdf.signature.SignatureVaultDialog
import com.krinry.scancraft.pdf.ui.PdfStudioPageManagerSheet
import com.krinry.scancraft.pdf.ui.StudioTool
import com.krinry.scancraft.pdf.viewer.PdfContinuousViewer
import com.krinry.scancraft.pdf.viewer.PdfPageCache
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPdfScreen(
    docId: Long,
    initialPageIndex: Int = 0,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as ScanCraftApp
    val repository = remember {
        ScannerRepository(context, app.database.documentDao(), app.database.pageDao(), app.database.folderDao())
    }

    val pageCache = remember { PdfPageCache(context, coroutineScope, maxPagesInMemory = 4) }
    DisposableEffect(Unit) {
        onDispose { pageCache.clear() }
    }

    var document by remember { mutableStateOf<Document?>(null) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    // Mode: Read/Scroll vs Edit/Draw
    var isEditMode by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(StudioTool.PENCIL) }

    // Tool Settings
    var selectedShapeType by remember { mutableStateOf(AnnotationShapeType.RECTANGLE) }
    var selectedColor by remember { mutableStateOf(Color(0xFFFFEB3B)) } // Fluorescent highlighter yellow
    var strokeWidthNorm by remember { mutableFloatStateOf(0.008f) }
    var annotationOpacity by remember { mutableFloatStateOf(1.0f) }
    var selectedFillType by remember { mutableStateOf(ShapeFillType.NONE) }
    var selectedDashStyle by remember { mutableStateOf(ShapeDashStyle.SOLID) }
    var gradientEndColor by remember { mutableStateOf(Color(0xFFFF9800)) }

    // Annotations Map: pageId -> List<PdfAnnotation>
    var pageAnnotations by remember { mutableStateOf(mapOf<Long, List<PdfAnnotation>>()) }
    // Undo/Redo Stacks: pageId -> History
    var undoStack by remember { mutableStateOf(mapOf<Long, List<List<PdfAnnotation>>>()) }
    var redoStack by remember { mutableStateOf(mapOf<Long, List<List<PdfAnnotation>>>()) }

    // Selection & Transform
    var selectedAnnotation by remember { mutableStateOf<PdfAnnotation?>(null) }
    var selectedAnnotationPageId by remember { mutableStateOf<Long?>(null) }

    // Zoom & Pan
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Dialogs & Sheets
    var showPageManagerSheet by remember { mutableStateOf(false) }
    var showSignatureVaultDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var targetTextPageId by remember { mutableStateOf<Long?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Export Options
    var exportMode by remember { mutableStateOf(PdfExportMode.FLATTENED_TAMPER_RESISTANT) }
    var exportPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var exportQuality by remember { mutableStateOf(PdfQuality.MEDIUM) }
    var exportWatermark by remember { mutableStateOf("") }
    var exportPassword by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Auto-save annotations on back
    fun persistAllAnnotations() {
        coroutineScope.launch(Dispatchers.IO) {
            for ((pId, annots) in pageAnnotations) {
                val json = PdfAnnotationSerializer.serialize(annots)
                repository.updatePageAnnotations(pId, json)
            }
        }
    }

    BackHandler {
        persistAllAnnotations()
        onNavigateBack()
    }

    // Load Document and Pages
    LaunchedEffect(docId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val doc = repository.getDocumentById(docId)
            val pageList = repository.getPagesListForDocument(docId)
            val initialMap = mutableMapOf<Long, List<PdfAnnotation>>()
            for (p in pageList) {
                if (p.annotationsJson.isNotBlank()) {
                    initialMap[p.id] = PdfAnnotationSerializer.deserialize(p.annotationsJson)
                }
            }
            withContext(Dispatchers.Main) {
                document = doc
                pages = pageList
                pageAnnotations = initialMap
                isLoading = false
                if (initialPageIndex in pageList.indices) {
                    listState.scrollToItem(initialPageIndex)
                }
            }
        }
    }

    // Prefetch adjacent pages when scrolling
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val currIdx = listState.firstVisibleItemIndex
        if (pages.isNotEmpty() && currIdx in pages.indices) {
            pageCache.prefetchAdjacentPages(pages, currIdx)
        }
    }

    // Undo / Redo helpers
    fun pushUndo(pageId: Long, newAnnotations: List<PdfAnnotation>) {
        val current = pageAnnotations[pageId] ?: emptyList()
        val history = undoStack[pageId] ?: emptyList()
        undoStack = undoStack + (pageId to (history + listOf(current)))
        redoStack = redoStack + (pageId to emptyList())
        pageAnnotations = pageAnnotations + (pageId to newAnnotations)
    }

    fun performUndo(pageId: Long) {
        val history = undoStack[pageId] ?: return
        if (history.isEmpty()) return
        val prev = history.last()
        val current = pageAnnotations[pageId] ?: emptyList()

        undoStack = undoStack + (pageId to history.dropLast(1))
        val redos = redoStack[pageId] ?: emptyList()
        redoStack = redoStack + (pageId to (redos + listOf(current)))
        pageAnnotations = pageAnnotations + (pageId to prev)
    }

    fun performRedo(pageId: Long) {
        val redos = redoStack[pageId] ?: return
        if (redos.isEmpty()) return
        val next = redos.last()
        val current = pageAnnotations[pageId] ?: emptyList()

        redoStack = redoStack + (pageId to redos.dropLast(1))
        val history = undoStack[pageId] ?: emptyList()
        undoStack = undoStack + (pageId to (history + listOf(current)))
        pageAnnotations = pageAnnotations + (pageId to next)
    }

    // Active page based on list scroll
    val activeVisiblePageIndex = remember(listState.firstVisibleItemIndex, pages) {
        listState.firstVisibleItemIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }
    val activeVisiblePage = pages.getOrNull(activeVisiblePageIndex)

    // Export execution
    fun executeExport() {
        isExporting = true
        showExportDialog = false
        coroutineScope.launch {
            persistAllAnnotations()
            val targetPages = repository.getPagesListForDocument(docId)
            val config = StudioExportConfig(
                exportMode = exportMode,
                pageSize = exportPageSize,
                quality = exportQuality,
                watermarkText = exportWatermark.ifBlank { null },
                password = exportPassword.ifBlank { null }
            )
            val exportDir = File(context.cacheDir, "studio_exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = document?.title?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").orEmpty().ifBlank { "Document" }
            val pdfFile = File(exportDir, "${cleanTitle}_studio.pdf")

            val result = PdfStudioExporter.exportPdf(
                context = context,
                pages = targetPages,
                pageAnnotationsMap = pageAnnotations,
                config = config,
                outputFile = pdfFile
            )

            isExporting = false
            result.fold(
                onSuccess = { file ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Document PDF"))
                },
                onFailure = { err ->
                    Toast.makeText(context, "Export error: ${err.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    persistAllAnnotations()
                    onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document?.title ?: "PDF Studio",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "Page ${activeVisiblePageIndex + 1} of ${pages.size.coerceAtLeast(1)}",
                        color = ScannerGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Undo
                val canUndo = activeVisiblePage?.let { (undoStack[it.id]?.isNotEmpty()) } == true
                IconButton(
                    onClick = { activeVisiblePage?.let { performUndo(it.id) } },
                    enabled = canUndo
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color.Gray.copy(alpha = 0.35f)
                    )
                }

                // Redo
                val canRedo = activeVisiblePage?.let { (redoStack[it.id]?.isNotEmpty()) } == true
                IconButton(
                    onClick = { activeVisiblePage?.let { performRedo(it.id) } },
                    enabled = canRedo
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color.Gray.copy(alpha = 0.35f)
                    )
                }

                // Page Manager
                IconButton(onClick = { showPageManagerSheet = true }) {
                    Icon(Icons.Outlined.Layers, contentDescription = "Page Manager", tint = Color.White)
                }

                // Export Button
                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        bottomBar = {
            if (isEditMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .navigationBarsPadding()
                ) {
                    // Secondary ribbon: palette & stroke size
                    if (activeTool != StudioTool.HAND && activeTool != StudioTool.ERASER) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // If Shapes tool active: shape chips & fill styles
                            if (activeTool == StudioTool.SHAPES) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    items(AnnotationShapeType.entries) { st ->
                                        val isSel = selectedShapeType == st
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) ScannerGreen.copy(alpha = 0.22f) else Color(0xFF222222),
                                            border = BorderStroke(1.dp, if (isSel) ScannerGreen else Color.DarkGray),
                                            modifier = Modifier.clickable { selectedShapeType = st }
                                        ) {
                                            Text(
                                                st.label,
                                                color = if (isSel) ScannerGreen else Color.LightGray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Fill Type
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(
                                            ShapeFillType.NONE to "Outline",
                                            ShapeFillType.SOLID to "Solid",
                                            ShapeFillType.LINEAR_GRADIENT to "Linear",
                                            ShapeFillType.RADIAL_GRADIENT to "Radial"
                                        ).forEach { (ft, lbl) ->
                                            val isSel = selectedFillType == ft
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSel) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF1E1E1E),
                                                border = BorderStroke(1.dp, if (isSel) ScannerGreen else Color(0xFF333333)),
                                                modifier = Modifier.clickable { selectedFillType = ft }
                                            ) {
                                                Text(
                                                    lbl,
                                                    color = if (isSel) ScannerGreen else Color.Gray,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Dash Style
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(
                                            ShapeDashStyle.SOLID to "Solid",
                                            ShapeDashStyle.DASHED to "Dash",
                                            ShapeDashStyle.DOTTED to "Dot"
                                        ).forEach { (ds, lbl) ->
                                            val isSel = selectedDashStyle == ds
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSel) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF1E1E1E),
                                                border = BorderStroke(1.dp, if (isSel) ScannerGreen else Color(0xFF333333)),
                                                modifier = Modifier.clickable { selectedDashStyle = ds }
                                            ) {
                                                Text(
                                                    lbl,
                                                    color = if (isSel) ScannerGreen else Color.Gray,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Color Palette and Size Sliders
                            val palette = listOf(
                                Color(0xFFFFEB3B), // Fluorescent Yellow
                                Color(0xFF00E676), // Neon Green
                                Color(0xFF29B6F6), // Sky Blue
                                Color(0xFFFF5252), // Coral Red
                                Color(0xFFAB47BC), // Purple
                                Color(0xFF212121), // Charcoal Black
                                Color(0xFFFFFFFF)  // Pure White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    palette.forEach { c ->
                                        val isSel = selectedColor == c
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(if (isSel) 2.5.dp else 1.dp, if (isSel) Color.White else Color.DarkGray, CircleShape)
                                                .clickable { selectedColor = c }
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Alpha", color = Color.Gray, fontSize = 10.sp)
                                    Slider(
                                        value = annotationOpacity,
                                        onValueChange = { annotationOpacity = it },
                                        valueRange = 0.1f..1.0f,
                                        modifier = Modifier.width(62.dp),
                                        colors = SliderDefaults.colors(thumbColor = ScannerGreen, activeTrackColor = ScannerGreen)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Size", color = Color.Gray, fontSize = 10.sp)
                                    Slider(
                                        value = strokeWidthNorm * 1000f,
                                        onValueChange = { strokeWidthNorm = it / 1000f },
                                        valueRange = 2f..36f,
                                        modifier = Modifier.width(68.dp),
                                        colors = SliderDefaults.colors(thumbColor = ScannerGreen, activeTrackColor = ScannerGreen)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
                    }

                    // Main Tool Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioToolButton(icon = Icons.Outlined.PanTool, label = "Hand", isActive = activeTool == StudioTool.HAND) {
                            activeTool = StudioTool.HAND
                        }
                        StudioToolButton(icon = Icons.Default.Edit, label = "Pen", isActive = activeTool == StudioTool.PENCIL) {
                            activeTool = StudioTool.PENCIL
                            if (strokeWidthNorm > 0.015f) strokeWidthNorm = 0.008f
                        }
                        StudioToolButton(icon = Icons.Outlined.Draw, label = "Marker", isActive = activeTool == StudioTool.MARKER) {
                            activeTool = StudioTool.MARKER
                            if (strokeWidthNorm < 0.014f) strokeWidthNorm = 0.022f
                        }
                        StudioToolButton(icon = Icons.Outlined.BlurOn, label = "Smudge", isActive = activeTool == StudioTool.SMUDGE_BLUR) {
                            activeTool = StudioTool.SMUDGE_BLUR
                            if (strokeWidthNorm < 0.018f) strokeWidthNorm = 0.026f
                        }
                        StudioToolButton(icon = Icons.Outlined.Category, label = "Shapes", isActive = activeTool == StudioTool.SHAPES) {
                            activeTool = StudioTool.SHAPES
                        }
                        StudioToolButton(icon = Icons.Default.TextFields, label = "Text", isActive = activeTool == StudioTool.TEXT) {
                            activeTool = StudioTool.TEXT
                            targetTextPageId = activeVisiblePage?.id
                            showTextDialog = true
                        }
                        StudioToolButton(icon = Icons.Outlined.Gesture, label = "Sign", isActive = activeTool == StudioTool.SIGNATURE) {
                            activeTool = StudioTool.SIGNATURE
                            showSignatureVaultDialog = true
                        }
                        StudioToolButton(icon = Icons.Outlined.CleaningServices, label = "Erase", isActive = activeTool == StudioTool.ERASER) {
                            activeTool = StudioTool.ERASER
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0D0D0D))
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.align(Alignment.Center))
            } else {
                // Continuous Vertical Viewer
                PdfContinuousViewer(
                    pages = pages,
                    pageAnnotationsMap = pageAnnotations,
                    pageCache = pageCache,
                    listState = listState,
                    isEditMode = isEditMode,
                    activeTool = activeTool,
                    selectedShapeType = selectedShapeType,
                    selectedColor = selectedColor,
                    strokeWidthNorm = strokeWidthNorm,
                    annotationOpacity = annotationOpacity,
                    fillType = selectedFillType,
                    dashStyle = selectedDashStyle,
                    gradientEndColor = gradientEndColor,
                    selectedAnnotation = selectedAnnotation,
                    selectedAnnotationPageId = selectedAnnotationPageId,
                    onAnnotationSelected = { pageId, annot ->
                        selectedAnnotationPageId = pageId
                        selectedAnnotation = annot
                    },
                    onAnnotationCreated = { pageId, annot ->
                        val current = pageAnnotations[pageId] ?: emptyList()
                        pushUndo(pageId, current + annot)
                    },
                    onAnnotationUpdated = { pageId, updated ->
                        val current = pageAnnotations[pageId] ?: emptyList()
                        val newAnnotList = current.map { if (it.id == updated.id) updated else it }
                        pushUndo(pageId, newAnnotList)
                        selectedAnnotation = updated
                    },
                    onAnnotationErased = { pageId, annotId ->
                        val current = pageAnnotations[pageId] ?: emptyList()
                        val newAnnotList = current.filter { it.id != annotId }
                        pushUndo(pageId, newAnnotList)
                        if (selectedAnnotation?.id == annotId) {
                            selectedAnnotation = null
                            selectedAnnotationPageId = null
                        }
                    },
                    zoomScale = zoomScale,
                    panOffset = panOffset,
                    onZoomPanChanged = { z, p ->
                        zoomScale = z
                        panOffset = p
                    }
                )

                // Floating Mode Switcher (Read vs Edit) & Zoom Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(50f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Zoom Reset button (when zoomed)
                    if (zoomScale > 1.05f) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF222222),
                            border = BorderStroke(1.dp, Color.DarkGray),
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${(zoomScale * 100).toInt()}%", color = ScannerGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Floating Mode Switcher Button
                    FloatingActionButton(
                        onClick = {
                            isEditMode = !isEditMode
                            if (isEditMode) {
                                activeTool = StudioTool.PENCIL
                            } else {
                                selectedAnnotation = null
                                selectedAnnotationPageId = null
                            }
                        },
                        containerColor = if (isEditMode) ScannerGreen else Color(0xFF263238),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Icon(
                                if (isEditMode) Icons.Default.Edit else Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEditMode) "Done" else "Annotate",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Exporting progress overlay
            if (isExporting) {
                Surface(
                    modifier = Modifier.fillMaxSize().zIndex(100f),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ScannerGreen)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Exporting Studio PDF...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // In-Studio Page Manager Sheet
    if (showPageManagerSheet) {
        PdfStudioPageManagerSheet(
            context = context,
            scope = coroutineScope,
            docId = docId,
            pages = pages,
            currentPageIndex = activeVisiblePageIndex,
            repository = repository,
            onPageSelected = { idx ->
                coroutineScope.launch { listState.scrollToItem(idx) }
            },
            onPagesUpdated = { updatedList ->
                pages = updatedList
            },
            onDismissRequest = { showPageManagerSheet = false }
        )
    }

    // Signature Vault Dialog
    if (showSignatureVaultDialog) {
        SignatureVaultDialog(
            context = context,
            onDismissRequest = { showSignatureVaultDialog = false },
            onSignatureSelected = { savedSig ->
                activeVisiblePage?.let { currPage ->
                    val stamp = PdfAnnotation.SignatureStamp(
                        bounds = RectNormalized(0.55f, 0.72f, 0.90f, 0.85f),
                        inkColorArgb = savedSig.inkColorArgb,
                        strokes = savedSig.strokes
                    )
                    val current = pageAnnotations[currPage.id] ?: emptyList()
                    pushUndo(currPage.id, current + stamp)
                    selectedAnnotation = stamp
                    selectedAnnotationPageId = currPage.id
                    Toast.makeText(context, "Signature stamped on page", Toast.LENGTH_SHORT).show()
                }
                showSignatureVaultDialog = false
            }
        )
    }

    // Text Note Dialog
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Add Text Label", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type note here...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ScannerGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pId = targetTextPageId ?: activeVisiblePage?.id
                        if (pId != null && textInput.isNotBlank()) {
                            val textNote = PdfAnnotation.TextNote(
                                text = textInput.trim(),
                                position = OffsetNormalized(0.15f, 0.25f),
                                colorArgb = selectedColor.toArgb(),
                                fontSizeNorm = 0.024f
                            )
                            val current = pageAnnotations[pId] ?: emptyList()
                            pushUndo(pId, current + textNote)
                            selectedAnnotation = textNote
                            selectedAnnotationPageId = pId
                        }
                        textInput = ""
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Place Text", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // PDF Export Settings Sheet / Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Color(0xFF1C1C1C),
            title = { Text("Export Document PDF", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Export Mode Toggle (Standard vs Flattened)
                    Text("Annotation Format:", color = Color.Gray, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (exportMode == PdfExportMode.FLATTENED_TAMPER_RESISTANT) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF242424),
                            border = BorderStroke(1.dp, if (exportMode == PdfExportMode.FLATTENED_TAMPER_RESISTANT) ScannerGreen else Color.DarkGray),
                            modifier = Modifier.weight(1f).clickable { exportMode = PdfExportMode.FLATTENED_TAMPER_RESISTANT }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Flattened", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Tamper-proof vectors", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (exportMode == PdfExportMode.STANDARD_NATIVE_ANNOTATIONS) ScannerGreen.copy(alpha = 0.25f) else Color(0xFF242424),
                            border = BorderStroke(1.dp, if (exportMode == PdfExportMode.STANDARD_NATIVE_ANNOTATIONS) ScannerGreen else Color.DarkGray),
                            modifier = Modifier.weight(1f).clickable { exportMode = PdfExportMode.STANDARD_NATIVE_ANNOTATIONS }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Standard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("ISO 32000 editable", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }

                    // Watermark
                    OutlinedTextField(
                        value = exportWatermark,
                        onValueChange = { exportWatermark = it },
                        placeholder = { Text("Watermark text (Optional)", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Password
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        placeholder = { Text("Password protection (Optional)", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ScannerGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { executeExport() },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGreen)
                ) {
                    Text("Export & Share", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
private fun StudioToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) ScannerGreen.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isActive) ScannerGreen else Color.LightGray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) ScannerGreen else Color.LightGray,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

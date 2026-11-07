package com.krinry.scancraft.pdf.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.pdf.model.*
import com.krinry.scancraft.pdf.ui.*
import com.krinry.scancraft.ui.theme.ScannerGreen
import java.io.File

@Composable
fun PdfContinuousViewer(
    pages: List<Page>,
    pageAnnotationsMap: Map<Long, List<PdfAnnotation>>,
    pageCache: PdfPageCache,
    listState: LazyListState,
    isEditMode: Boolean,
    activeTool: StudioTool,
    selectedShapeType: AnnotationShapeType,
    selectedColor: Color,
    strokeWidthNorm: Float,
    annotationOpacity: Float,
    fillType: ShapeFillType,
    dashStyle: ShapeDashStyle,
    gradientEndColor: Color,
    selectedAnnotation: PdfAnnotation?,
    selectedAnnotationPageId: Long?,
    onAnnotationSelected: (pageId: Long, PdfAnnotation?) -> Unit,
    onAnnotationCreated: (pageId: Long, PdfAnnotation) -> Unit,
    onAnnotationUpdated: (pageId: Long, PdfAnnotation) -> Unit,
    onAnnotationErased: (pageId: Long, String) -> Unit,
    zoomScale: Float,
    panOffset: Offset,
    onZoomPanChanged: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .onSizeChanged { viewportSize = it }
            .pointerInput(isEditMode, activeTool, zoomScale) {
                // If in read mode, or 2 fingers detected: allow pinch-zoom and pan
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (zoomScale * zoom).coerceIn(1f, 4.5f)
                    val newPan = if (newZoom <= 1.01f) {
                        Offset.Zero
                    } else {
                        val maxPanX = (viewportSize.width * (newZoom - 1f)) / 2f
                        val maxPanY = (viewportSize.height * (newZoom - 1f)) / 2f
                        Offset(
                            (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    }
                    onZoomPanChanged(newZoom, newPan)
                }
            }
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = panOffset.x
                translationY = panOffset.y
            }
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = (!isEditMode || activeTool == StudioTool.HAND),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                val annotations = pageAnnotationsMap[page.id] ?: emptyList()
                val isAnnotationSelectedOnThisPage = selectedAnnotationPageId == page.id && selectedAnnotation != null

                PdfPageItem(
                    page = page,
                    pageIndex = index,
                    pageCache = pageCache,
                    annotations = annotations,
                    isEditMode = isEditMode,
                    activeTool = activeTool,
                    selectedShapeType = selectedShapeType,
                    selectedColor = selectedColor,
                    strokeWidthNorm = strokeWidthNorm,
                    annotationOpacity = annotationOpacity,
                    fillType = fillType,
                    dashStyle = dashStyle,
                    gradientEndColor = gradientEndColor,
                    selectedAnnotation = if (isAnnotationSelectedOnThisPage) selectedAnnotation else null,
                    onAnnotationSelected = { annot -> onAnnotationSelected(page.id, annot) },
                    onAnnotationCreated = { annot -> onAnnotationCreated(page.id, annot) },
                    onAnnotationUpdated = { annot -> onAnnotationUpdated(page.id, annot) },
                    onAnnotationErased = { annotId -> onAnnotationErased(page.id, annotId) }
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    page: Page,
    pageIndex: Int,
    pageCache: PdfPageCache,
    annotations: List<PdfAnnotation>,
    isEditMode: Boolean,
    activeTool: StudioTool,
    selectedShapeType: AnnotationShapeType,
    selectedColor: Color,
    strokeWidthNorm: Float,
    annotationOpacity: Float,
    fillType: ShapeFillType,
    dashStyle: ShapeDashStyle,
    gradientEndColor: Color,
    selectedAnnotation: PdfAnnotation?,
    onAnnotationSelected: (PdfAnnotation?) -> Unit,
    onAnnotationCreated: (PdfAnnotation) -> Unit,
    onAnnotationUpdated: (PdfAnnotation) -> Unit,
    onAnnotationErased: (String) -> Unit
) {
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val pageFile = remember(page) { File(page.processedImagePath) }

    // Prefetch and load into memory-safe LRU cache
    LaunchedEffect(page.id) {
        pageCache.loadPageBitmap(page) { bmp ->
            loadedBitmap = bmp
        }
    }

    // A4 default aspect ratio 1 : 1.414 unless image gives exact ratio
    val aspectRatio = remember(loadedBitmap) {
        val b = loadedBitmap
        if (b != null && b.height > 0) b.width.toFloat() / b.height.toFloat() else 0.707f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .onSizeChanged { pageSize = it }
    ) {
        // Page Image Content
        if (loadedBitmap != null && !loadedBitmap!!.isRecycled) {
            androidx.compose.foundation.Image(
                bitmap = loadedBitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else if (pageFile.exists()) {
            AsyncImage(
                model = pageFile,
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ScannerGreen, modifier = Modifier.size(24.dp))
            }
        }

        // Vector Annotation Overlay Canvas
        if (pageSize.width > 0 && pageSize.height > 0) {
            PdfInteractiveCanvas(
                pageWidth = pageSize.width.toFloat(),
                pageHeight = pageSize.height.toFloat(),
                annotations = annotations,
                activeTool = activeTool,
                isEditMode = isEditMode,
                selectedShapeType = selectedShapeType,
                selectedColor = selectedColor,
                strokeWidthNorm = strokeWidthNorm,
                annotationOpacity = annotationOpacity,
                fillType = fillType,
                dashStyle = dashStyle,
                gradientEndColor = gradientEndColor,
                selectedAnnotationId = selectedAnnotation?.id,
                onAnnotationSelected = onAnnotationSelected,
                onAnnotationCreated = onAnnotationCreated,
                onAnnotationErased = onAnnotationErased,
                modifier = Modifier.fillMaxSize()
            )

            // Object Selection & Transform Overlay (if an item is selected on this page)
            if (selectedAnnotation != null) {
                AnnotationTransformOverlay(
                    annotation = selectedAnnotation,
                    pageWidth = pageSize.width.toFloat(),
                    pageHeight = pageSize.height.toFloat(),
                    onUpdate = onAnnotationUpdated,
                    onDelete = { onAnnotationErased(selectedAnnotation.id) },
                    onDeselect = { onAnnotationSelected(null) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

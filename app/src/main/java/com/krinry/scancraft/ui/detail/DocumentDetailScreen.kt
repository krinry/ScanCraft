package com.krinry.scancraft.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.cv.OcrEngine
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.ui.detail.components.DetailBottomBar
import com.krinry.scancraft.ui.detail.components.DetailTopBar
import com.krinry.scancraft.ui.detail.components.PageGridCard
import com.krinry.scancraft.ui.detail.components.PageSelectionBottomBar
import com.krinry.scancraft.ui.detail.dialogs.CollageStudioDialog
import com.krinry.scancraft.ui.detail.dialogs.DocumentMoreDialog
import com.krinry.scancraft.ui.detail.dialogs.MoveCopyPagesDialog
import com.krinry.scancraft.ui.detail.dialogs.MoveToPositionDialog
import com.krinry.scancraft.ui.detail.dialogs.ShareExportBottomSheet
import com.krinry.scancraft.ui.home.dialogs.MoveCopyAction
import com.krinry.scancraft.ui.pdf.PdfExportDialog
import com.krinry.scancraft.ui.signature.SignatureDialog
import com.krinry.scancraft.ui.theme.ScannerTheme
import java.io.File

@Composable
fun DocumentDetailScreen(
    docId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (docId: Long) -> Unit,
    onNavigateToViewer: (pageId: Long) -> Unit,
    onNavigateToEditPdf: (docId: Long, initialPageIndex: Int) -> Unit = { _, _ -> },
    viewModel: DocumentDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = ScannerTheme.colors
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(docId) {
        viewModel.load(docId)
    }

    val document by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allDocuments by viewModel.allDocuments.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val selectedPageIds by viewModel.selectedPageIds.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val isReorderMode by viewModel.isReorderMode.collectAsState()

    var showPdfDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedPagesDialog by remember { mutableStateOf(false) }
    var showMovePagesDialog by remember { mutableStateOf(false) }
    var showCopyPagesDialog by remember { mutableStateOf(false) }
    var pageToMovePosition by remember { mutableStateOf<Page?>(null) }
    var showBatchMovePositionDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var capturedSignatureBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showCollageDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showShareExportSheet by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showOcrResultDialog by remember { mutableStateOf(false) }
    var extractedOcrText by remember { mutableStateOf("") }
    var isOcrExtracting by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // Intercept back button when in select mode or reorder mode
    BackHandler(enabled = isSelectMode || isReorderMode) {
        if (isSelectMode) {
            viewModel.clearSelection()
        } else if (isReorderMode) {
            viewModel.setReorderMode(false)
        }
    }

    // Pages targeted for export/sharing (selected pages if selection active, else all pages)
    val pagesToShare = remember(pages, selectedPageIds, isSelectMode) {
        if (isSelectMode && selectedPageIds.isNotEmpty()) {
            pages.filter { it.id in selectedPageIds }
        } else {
            pages
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            DetailTopBar(
                title = document?.title ?: "Document",
                onNavigateBack = onNavigateBack,
                onRenameClick = {
                    renameText = document?.title.orEmpty()
                    showRenameDialog = true
                },
                onExportPdfClick = {
                    if (pages.isNotEmpty()) {
                        showPdfDialog = true
                    } else {
                        Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
                    }
                },
                isSelectMode = isSelectMode,
                selectedCount = selectedPageIds.size,
                totalCount = pages.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = {
                    if (selectedPageIds.size == pages.size && pages.isNotEmpty()) {
                        viewModel.clearSelection()
                    } else {
                        viewModel.selectAllPages()
                    }
                },
                isReorderMode = isReorderMode,
                onToggleReorderMode = { viewModel.setReorderMode(!isReorderMode) },
                onEnterSelectMode = { viewModel.setSelectMode(true) }
            )
        },
        bottomBar = {
            if (isSelectMode) {
                PageSelectionBottomBar(
                    selectedCount = selectedPageIds.size,
                    onShare = {
                        if (selectedPageIds.isNotEmpty()) {
                            showShareExportSheet = true
                        }
                    },
                    onMove = {
                        if (selectedPageIds.isNotEmpty()) {
                            showMovePagesDialog = true
                        }
                    },
                    onCopy = {
                        if (selectedPageIds.isNotEmpty()) {
                            showCopyPagesDialog = true
                        }
                    },
                    onSetPosition = {
                        if (selectedPageIds.isNotEmpty()) {
                            showBatchMovePositionDialog = true
                        }
                    },
                    onDelete = {
                        if (selectedPageIds.isNotEmpty()) {
                            showDeleteSelectedPagesDialog = true
                        }
                    }
                )
            } else if (!isReorderMode) {
                DetailBottomBar(
                    onAddClick = { onNavigateToCamera(docId) },
                    onCollageClick = {
                        if (pages.size >= 2) {
                            showCollageDialog = true
                        } else {
                            Toast.makeText(context, "Scan at least 2 pages for collage", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShareClick = {
                        if (pages.isNotEmpty()) {
                            showShareExportSheet = true
                        } else {
                            Toast.makeText(context, "No pages to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEditPdfClick = {
                        if (pages.isNotEmpty()) {
                            onNavigateToEditPdf(docId, 0)
                        } else {
                            Toast.makeText(context, "Add pages to edit PDF", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMoreClick = { showMoreDialog = true }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (pages.isEmpty()) {
                // Empty state for document
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = colors.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Pages in Document",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Add' below to scan pages into this document",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onNavigateToCamera(docId) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Page")
                    }
                }
            } else {
                // 2-column page grid with long-press multi-select & drag/align support
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { pageIndex, page ->
                        PageGridCard(
                            page = page,
                            isSelected = selectedPageIds.contains(page.id),
                            isSelectMode = isSelectMode,
                            isReorderMode = isReorderMode,
                            canMoveLeft = pageIndex > 0,
                            canMoveRight = pageIndex < pages.size - 1,
                            onMoveLeft = { viewModel.movePage(pageIndex, pageIndex - 1) },
                            onMoveRight = { viewModel.movePage(pageIndex, pageIndex + 1) },
                            onPageNumberClick = { pageToMovePosition = page },
                            onClick = {
                                if (isSelectMode) {
                                    viewModel.toggleSelectPage(page.id)
                                } else {
                                    onNavigateToViewer(page.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelectPage(page.id)
                            }
                        )
                    }
                }
            }

            // PDF Generation Progress Overlay
            if (isGeneratingPdf) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background.copy(alpha = 0.75f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Compiling PDF document...", color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameDocument(renameText.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Document?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all pages from internal storage.", color = colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument {
                            showDeleteConfirmDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE3F3F))
                ) {
                    Text("Delete")
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

    // Confirmation dialog for deleting selected pages
    if (showDeleteSelectedPagesDialog) {
        val count = selectedPageIds.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedPagesDialog = false },
            title = {
                Text(
                    "Delete $count Page${if (count > 1) "s" else ""}?",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete $count selected page${if (count > 1) "s" else ""}? This action cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSelectedPagesDialog = false
                        viewModel.batchDeleteSelectedPages {
                            Toast.makeText(context, "Deleted $count page(s)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE3F3F))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedPagesDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    val pageToMove = pageToMovePosition
    if (pageToMove != null) {
        MoveToPositionDialog(
            currentPosition1Based = pageToMove.pageIndex + 1,
            totalPages = pages.size,
            onDismiss = { pageToMovePosition = null },
            onConfirm = { targetPos ->
                viewModel.movePageToPosition(pageToMove.id, targetPos)
                pageToMovePosition = null
                Toast.makeText(context, "Moved Page ${pageToMove.pageIndex + 1} to Page $targetPos", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showBatchMovePositionDialog) {
        MoveToPositionDialog(
            currentPosition1Based = 1,
            totalPages = pages.size,
            selectedPageCount = selectedPageIds.size,
            onDismiss = { showBatchMovePositionDialog = false },
            onConfirm = { targetPos ->
                viewModel.movePagesToPosition(selectedPageIds, targetPos)
                showBatchMovePositionDialog = false
                Toast.makeText(context, "Moved ${selectedPageIds.size} page(s) to position $targetPos", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Move or Copy Pages Dialog
    if (showMovePagesDialog || showCopyPagesDialog) {
        val action = if (showMovePagesDialog) MoveCopyAction.MOVE else MoveCopyAction.COPY
        val count = selectedPageIds.size
        MoveCopyPagesDialog(
            pageCount = count,
            currentDocId = docId,
            documents = allDocuments,
            initialAction = action,
            onDismiss = {
                showMovePagesDialog = false
                showCopyPagesDialog = false
            },
            onDuplicateInCurrentDoc = {
                showCopyPagesDialog = false
                viewModel.copySelectedPagesWithinCurrentDoc {
                    Toast.makeText(context, "Duplicated $count page(s)", Toast.LENGTH_SHORT).show()
                }
            },
            onCreateNewDocAndConfirm = { act, newTitle ->
                showMovePagesDialog = false
                showCopyPagesDialog = false
                if (act == MoveCopyAction.MOVE) {
                    viewModel.createDocAndMoveSelectedPages(newTitle) {
                        Toast.makeText(context, "Moved $count page(s) to '$newTitle'", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createDocAndCopySelectedPages(newTitle) {
                        Toast.makeText(context, "Copied $count page(s) to '$newTitle'", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onConfirmTargetDoc = { act, targetDocId, targetDocTitle ->
                showMovePagesDialog = false
                showCopyPagesDialog = false
                if (act == MoveCopyAction.MOVE) {
                    viewModel.moveSelectedPages(targetDocId) {
                        Toast.makeText(context, "Moved $count page(s) to '$targetDocTitle'", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.copySelectedPages(targetDocId) {
                        Toast.makeText(context, "Copied $count page(s) to '$targetDocTitle'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showMoveFolderDialog) {
        AlertDialog(
            onDismissRequest = { showMoveFolderDialog = false },
            title = { Text("Move to Folder", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = colors.surfaceSecondary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveToFolder(-1L)
                                showMoveFolderDialog = false
                            }
                    ) {
                        Text("Root (No folder)", color = colors.textPrimary, modifier = Modifier.padding(14.dp))
                    }

                    folders.forEach { f ->
                        Surface(
                            color = colors.surfaceSecondary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveToFolder(f.id)
                                    showMoveFolderDialog = false
                                }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = colors.folderYellow)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(f.name, color = colors.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveFolderDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showPdfDialog) {
        PdfExportDialog(
            onDismiss = { showPdfDialog = false },
            onSaveToDownloads = { options ->
                viewModel.savePdfToDownloads(context, options, pagesToShare)
                showPdfDialog = false
            },
            onShare = { options ->
                viewModel.exportAndSharePdf(context, options, pagesToShare)
                showPdfDialog = false
            }
        )
    }

    if (showCollageDialog) {
        CollageStudioDialog(
            pages = pages,
            documentTitle = document?.title ?: "scan",
            onDismiss = { showCollageDialog = false },
            onGenerateMultiCollage = { specs, watermarkConfig, drawBorders ->
                viewModel.createMultiPageCollage(specs, watermarkConfig, drawBorders) {
                    showCollageDialog = false
                    Toast.makeText(context, "${specs.size} collage page(s) generated!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { bmp ->
                showSignatureDialog = false
                capturedSignatureBitmap = bmp
            }
        )
    }

    capturedSignatureBitmap?.let { sigBmp ->
        val targetPage = pages.firstOrNull()
        if (targetPage != null) {
            com.krinry.scancraft.ui.signature.SignaturePlacementDialog(
                page = targetPage,
                signatureBitmap = sigBmp,
                onDismiss = { capturedSignatureBitmap = null },
                onStamp = { relX, relY, relW, relH ->
                    viewModel.stampSignature(targetPage.id, sigBmp, relX, relY, relW, relH) {
                        capturedSignatureBitmap = null
                        Toast.makeText(context, "Signature applied to page!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            Toast.makeText(context, "No pages available to sign", Toast.LENGTH_SHORT).show()
            capturedSignatureBitmap = null
        }
    }

    if (showShareExportSheet) {
        ShareExportBottomSheet(
            pages = pagesToShare,
            onDismiss = { showShareExportSheet = false },
            onSharePdf = {
                showPdfDialog = true
            },
            onShareJpg = {
                viewModel.shareAsImages(context, pagesToShare)
                showShareExportSheet = false
            },
            onShareLongImage = {
                viewModel.shareLongImage(context, pagesToShare)
                showShareExportSheet = false
            },
            onExtractText = {
                isOcrExtracting = true
                showShareExportSheet = false
                val selectedLang = com.krinry.scancraft.data.repository.SettingsManager.getInstance(context).ocrLanguage.value
                coroutineScope.launch(Dispatchers.IO) {
                    val fullText = StringBuilder()
                    for (p in pagesToShare) {
                        val bmp = BitmapFactory.decodeFile(p.processedImagePath)
                        if (bmp != null) {
                            try {
                                val recognized = OcrEngine.recognizeText(bmp, language = selectedLang)
                                if (recognized.isNotBlank()) {
                                    fullText.appendLine("--- Page ${p.pageIndex + 1} ---")
                                    fullText.appendLine(recognized)
                                    fullText.appendLine()
                                }
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isOcrExtracting = false
                        extractedOcrText = fullText.toString().ifBlank { "No text recognized in pages." }
                        showOcrResultDialog = true
                    }
                }
            },
            onSplitPdf = {
                showShareExportSheet = false
                Toast.makeText(context, "Pages are managed independently in ScanCraft.", Toast.LENGTH_SHORT).show()
            },
            onSaveToGallery = {
                showShareExportSheet = false
                viewModel.savePagesToGallery(context)
            },
            onEmailToMyself = {
                showShareExportSheet = false
                viewModel.emailToMyself(context)
            }
        )
    }

    if (showMoreDialog) {
        DocumentMoreDialog(
            onDismiss = { showMoreDialog = false },
            onBatchEditClick = {
                showMoreDialog = false
                viewModel.setSelectMode(true)
            },
            onSetTagClick = {
                Toast.makeText(context, "Tag management.", Toast.LENGTH_SHORT).show()
            },
            onPdfPasswordClick = {
                showMoreDialog = false
                onNavigateToEditPdf(docId, 0)
            },
            onMoveToFolderClick = {
                showMoveFolderDialog = true
            },
            onRenameClick = {
                renameText = document?.title.orEmpty()
                showRenameDialog = true
            },
            onDeleteClick = {
                showDeleteConfirmDialog = true
            },
            onSignatureClick = {
                showSignatureDialog = true
            }
        )
    }

    if (showOcrResultDialog) {
        AlertDialog(
            onDismissRequest = { showOcrResultDialog = false },
            title = {
                Text(
                    text = "Extracted Text (Offline OCR)",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = extractedOcrText,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Scanned Text", extractedOcrText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        showOcrResultDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Copy Text", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrResultDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}

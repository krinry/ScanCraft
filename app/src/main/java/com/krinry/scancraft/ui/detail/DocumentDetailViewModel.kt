package com.krinry.scancraft.ui.detail

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.cv.CollageLayout
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.pdf.PdfExportOptions
import com.krinry.scancraft.pdf.PdfGenerator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList

class DocumentDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScanCraftApp).database
    private val repository = ScannerRepository(application, db.documentDao(), db.pageDao(), db.folderDao())

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf

    private val _allDocuments = MutableStateFlow<List<Document>>(emptyList())
    val allDocuments: StateFlow<List<Document>> = _allDocuments

    private val _selectedPageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPageIds: StateFlow<Set<Long>> = _selectedPageIds

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode: StateFlow<Boolean> = _isReorderMode

    fun load(docId: Long) {
        viewModelScope.launch {
            _document.value = repository.getDocumentById(docId)
            repository.getPagesForDocument(docId).collectLatest { list ->
                _pages.value = list
            }
        }
        viewModelScope.launch {
            repository.getAllFolders().collectLatest { folderList ->
                _folders.value = folderList
            }
        }
        viewModelScope.launch {
            repository.getAllDocuments().collectLatest { docList ->
                _allDocuments.value = docList
            }
        }
    }

    fun toggleSelectPage(pageId: Long) {
        val current = _selectedPageIds.value
        if (current.contains(pageId)) {
            val next = current - pageId
            _selectedPageIds.value = next
            if (next.isEmpty()) {
                _isSelectMode.value = false
            }
        } else {
            _selectedPageIds.value = current + pageId
            _isSelectMode.value = true
        }
    }

    fun selectAllPages() {
        _selectedPageIds.value = _pages.value.map { it.id }.toSet()
        _isSelectMode.value = true
    }

    fun clearSelection() {
        _selectedPageIds.value = emptySet()
        _isSelectMode.value = false
    }

    fun setSelectMode(enabled: Boolean) {
        _isSelectMode.value = enabled
        if (!enabled) {
            _selectedPageIds.value = emptySet()
        }
    }

    fun setReorderMode(enabled: Boolean) {
        _isReorderMode.value = enabled
        if (enabled) {
            clearSelection()
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _pages.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _pages.value = current
            reorderPages(current)
        }
    }

    fun movePageToPosition(pageId: Long, targetPosition1Based: Int) {
        val current = _pages.value.toMutableList()
        val fromIndex = current.indexOfFirst { it.id == pageId }
        val toIndex = (targetPosition1Based - 1).coerceIn(0, current.size - 1)
        if (fromIndex != -1 && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _pages.value = current
            reorderPages(current)
        }
    }

    fun movePagesToPosition(pageIds: Set<Long>, targetPosition1Based: Int) {
        val current = _pages.value.toMutableList()
        val targetIndex = (targetPosition1Based - 1).coerceIn(0, current.size - 1)
        val pagesToMove = current.filter { it.id in pageIds }
        if (pagesToMove.isEmpty()) return

        current.removeAll(pagesToMove)
        val insertAt = targetIndex.coerceIn(0, current.size)
        current.addAll(insertAt, pagesToMove)
        _pages.value = current
        reorderPages(current)
    }

    fun batchDeleteSelectedPages(onComplete: () -> Unit) {
        val selected = _pages.value.filter { it.id in _selectedPageIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.batchDeletePages(selected)
            withContext(Dispatchers.Main) {
                clearSelection()
                onComplete()
            }
        }
    }

    fun moveSelectedPages(targetDocId: Long, onComplete: () -> Unit) {
        val selected = _pages.value.filter { it.id in _selectedPageIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.movePagesToDocument(selected, targetDocId)
            withContext(Dispatchers.Main) {
                clearSelection()
                onComplete()
            }
        }
    }

    fun copySelectedPages(targetDocId: Long, onComplete: () -> Unit) {
        val selected = _pages.value.filter { it.id in _selectedPageIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.copyPagesToDocument(selected, targetDocId)
            withContext(Dispatchers.Main) {
                clearSelection()
                onComplete()
            }
        }
    }

    fun createDocAndMoveSelectedPages(newDocTitle: String, onComplete: (Long) -> Unit) {
        val selected = _pages.value.filter { it.id in _selectedPageIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val newDocId = repository.createNewDocument(newDocTitle)
            repository.movePagesToDocument(selected, newDocId)
            withContext(Dispatchers.Main) {
                clearSelection()
                onComplete(newDocId)
            }
        }
    }

    fun createDocAndCopySelectedPages(newDocTitle: String, onComplete: (Long) -> Unit) {
        val selected = _pages.value.filter { it.id in _selectedPageIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val newDocId = repository.createNewDocument(newDocTitle)
            repository.copyPagesToDocument(selected, newDocId)
            withContext(Dispatchers.Main) {
                clearSelection()
                onComplete(newDocId)
            }
        }
    }

    fun copySelectedPagesWithinCurrentDoc(onComplete: () -> Unit) {
        val selected = _pages.value.filter { it.id in _selectedPageIds.value }
        if (selected.isEmpty()) return
        val docId = _document.value?.id ?: return
        viewModelScope.launch {
            repository.copyPagesToDocument(selected, docId)
            withContext(Dispatchers.Main) {
                clearSelection()
                onComplete()
            }
        }
    }

    fun renameDocument(newTitle: String) {
        val doc = _document.value ?: return
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val updated = doc.copy(title = newTitle.trim(), modifiedTime = System.currentTimeMillis())
            repository.updateDocument(updated)
            _document.value = updated
        }
    }

    fun moveToFolder(folderId: Long) {
        val doc = _document.value ?: return
        viewModelScope.launch {
            repository.moveDocumentToFolder(doc.id, folderId)
            _document.value = repository.getDocumentById(doc.id)
        }
    }

    fun deleteDocument(onDeleted: () -> Unit) {
        val doc = _document.value ?: return
        viewModelScope.launch {
            repository.deleteDocument(doc)
            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch {
            repository.deletePage(page)
        }
    }

    fun reorderPages(orderedPages: List<Page>) {
        val docId = _document.value?.id ?: return
        viewModelScope.launch {
            repository.reorderPages(docId, orderedPages)
        }
    }

    fun createCollage(
        layout: CollageLayout,
        pageIds: List<Long>,
        watermarkConfig: com.krinry.scancraft.cv.CollageWatermarkConfig? = null,
        slotTransforms: Map<Int, com.krinry.scancraft.cv.SlotTransform>? = null,
        drawBorders: Boolean = false,
        slotCustomPaths: Map<Int, String>? = null,
        onDone: () -> Unit
    ) {
        val doc = _document.value ?: return
        val docTitle = doc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "scan" }
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val collageDocTitle = if (docTitle.startsWith("collage-")) docTitle else "collage-${docTitle}"
                val spec = com.krinry.scancraft.data.repository.CollagePageSpec(
                    layout = layout,
                    pageIds = pageIds,
                    slotTransforms = slotTransforms ?: emptyMap(),
                    slotCustomPaths = slotCustomPaths ?: emptyMap()
                )
                repository.createCollageDocument(
                    sourceDocId = doc.id,
                    specs = listOf(spec),
                    watermarkConfig = watermarkConfig,
                    drawBorders = drawBorders,
                    customTitle = collageDocTitle
                )
                repository.createCollagePage(
                    documentId = doc.id,
                    pageIds = pageIds,
                    layout = layout,
                    watermarkConfig = watermarkConfig,
                    slotTransforms = slotTransforms,
                    drawBorders = drawBorders,
                    slotCustomPaths = slotCustomPaths,
                    customFileName = docTitle
                )
                load(doc.id)
                withContext(Dispatchers.Main) {
                    _isGeneratingPdf.value = false
                    onDone()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isGeneratingPdf.value = false
                }
                e.printStackTrace()
            }
        }
    }

    fun createMultiPageCollage(
        specs: List<com.krinry.scancraft.data.repository.CollagePageSpec>,
        watermarkConfig: com.krinry.scancraft.cv.CollageWatermarkConfig? = null,
        drawBorders: Boolean = false,
        onDone: () -> Unit
    ) {
        val doc = _document.value ?: return
        val docTitle = doc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "scan" }
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val collageDocTitle = if (docTitle.startsWith("collage-")) docTitle else "collage-${docTitle}"
                repository.createCollageDocument(
                    sourceDocId = doc.id,
                    specs = specs,
                    watermarkConfig = watermarkConfig,
                    drawBorders = drawBorders,
                    customTitle = collageDocTitle
                )
                repository.createCollagePages(
                    documentId = doc.id,
                    specs = specs,
                    watermarkConfig = watermarkConfig,
                    drawBorders = drawBorders,
                    customFileName = docTitle
                )
                load(doc.id)
                withContext(Dispatchers.Main) {
                    _isGeneratingPdf.value = false
                    onDone()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isGeneratingPdf.value = false
                }
                e.printStackTrace()
            }
        }
    }

    fun stampSignature(
        pageId: Long,
        signatureBitmap: android.graphics.Bitmap,
        relX: Float = 0.55f,
        relY: Float = 0.75f,
        relW: Float = 0.35f,
        relH: Float = -1f,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repository.stampSignatureOnPage(pageId, signatureBitmap, relX, relY, relW, relH)
            val docId = _document.value?.id
            if (docId != null) {
                _document.value = repository.getDocumentById(docId)
            }
            withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun shareAsImages(context: Context, customPages: List<Page>? = null) {
        val currentPages = customPages ?: _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uriList = ArrayList<Uri>()
            for (page in currentPages) {
                val file = File(page.processedImagePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uriList.add(uri)
                }
            }

            if (uriList.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Document Images"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share images: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportAndSharePdf(context: Context, options: PdfExportOptions, customPages: List<Page>? = null) {
        val currentDoc = _document.value ?: return
        val currentPages = customPages ?: _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
            return
        }

        _isGeneratingPdf.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val pdfDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = currentDoc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Document" }
            val pdfFile = File(pdfDir, "${cleanTitle}.pdf")

            val result = PdfGenerator.generatePdf(context, currentPages, options, pdfFile)
            withContext(Dispatchers.Main) {
                _isGeneratingPdf.value = false
                result.fold(
                    onSuccess = { file ->
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Document PDF"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "PDF generation failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    fun savePdfToDownloads(context: Context, options: PdfExportOptions, customPages: List<Page>? = null) {
        val currentDoc = _document.value ?: return
        val currentPages = customPages ?: _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
            return
        }

        _isGeneratingPdf.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.pdf")
            val result = PdfGenerator.generatePdf(context, currentPages, options, tempFile)

            withContext(Dispatchers.Main) {
                _isGeneratingPdf.value = false
                result.fold(
                    onSuccess = { file ->
                        val cleanTitle = currentDoc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Scan" }
                        val fileName = "${cleanTitle}.pdf"
                        var savedSuccessfully = false

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null) {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        FileInputStream(file).use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    savedSuccessfully = true
                                }
                            } else {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val target = File(downloadsDir, fileName)
                                FileInputStream(file).use { input ->
                                    FileOutputStream(target).use { out ->
                                        input.copyTo(out)
                                    }
                                }
                                savedSuccessfully = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (savedSuccessfully) {
                            Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Could not save to Downloads directory", Toast.LENGTH_LONG).show()
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "PDF generation failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    fun shareLongImage(context: Context, customPages: List<Page>? = null) {
        val currentPages = customPages ?: _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to share", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val filePaths = currentPages.map { it.processedImagePath }
            val longBmp = com.krinry.scancraft.cv.CollageGenerator.generateLongImage(filePaths)
            if (longBmp == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to stitch long image", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val longImgFile = File(exportDir, "long_image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(longImgFile).use { out ->
                longBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            longBmp.recycle()

            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        longImgFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Long Image"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to share long image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun savePagesToGallery(context: Context) {
        val currentPages = _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to save", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var savedCount = 0
            for (p in currentPages) {
                val srcFile = File(p.processedImagePath)
                if (!srcFile.exists()) continue

                val fileName = "ScanCraft_${System.currentTimeMillis()}_${p.pageIndex + 1}.jpg"
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScanCraft")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                FileInputStream(srcFile).use { input -> input.copyTo(out) }
                            }
                            savedCount++
                        }
                    } else {
                        val picsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ScanCraft").apply { if (!exists()) mkdirs() }
                        val destFile = File(picsDir, fileName)
                        FileInputStream(srcFile).use { input ->
                            FileOutputStream(destFile).use { out -> input.copyTo(out) }
                        }
                        savedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved $savedCount page(s) to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun emailToMyself(context: Context) {
        val currentDoc = _document.value ?: return
        val currentPages = _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to email", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pdfDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = currentDoc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Document" }
            val pdfFile = File(pdfDir, "${cleanTitle}.pdf")
            val options = PdfExportOptions()
            PdfGenerator.generatePdf(context, currentPages, options, pdfFile)

            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_SUBJECT, currentDoc.title)
                        putExtra(Intent.EXTRA_TEXT, "Scanned document sent via ScanCraft.")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(emailIntent, "Email Document"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open email client: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

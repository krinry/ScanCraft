package com.krinry.scancraft.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.krinry.scancraft.cv.BookSpineDetector
import com.krinry.scancraft.cv.CollageGenerator
import com.krinry.scancraft.cv.CollageLayout
import com.krinry.scancraft.data.local.DocumentDao
import com.krinry.scancraft.data.local.FolderDao
import com.krinry.scancraft.data.local.PageDao
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class CollagePageSpec(
    val layout: CollageLayout,
    val pageIds: List<Long>,
    val slotTransforms: Map<Int, com.krinry.scancraft.cv.SlotTransform>? = null,
    val slotCustomPaths: Map<Int, String>? = null
)

class ScannerRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val folderDao: FolderDao
) {

    // --- Document Queries ---
    fun getAllDocuments(): Flow<List<Document>> = documentDao.getAllDocuments()

    fun getRootDocuments(): Flow<List<Document>> = documentDao.getRootDocuments()

    fun getDocumentsInFolder(folderId: Long): Flow<List<Document>> = documentDao.getDocumentsInFolder(folderId)

    fun searchDocuments(query: String): Flow<List<Document>> = documentDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): Document? = documentDao.getDocumentById(id)

    // --- Hierarchical Folder Queries & Mutations ---
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()

    fun getRootFolders(): Flow<List<Folder>> = folderDao.getRootFolders()

    fun getSubfolders(parentFolderId: Long): Flow<List<Folder>> = folderDao.getSubfolders(parentFolderId)

    suspend fun getFolderById(id: Long): Folder? = folderDao.getFolderById(id)

    suspend fun createFolder(
        name: String,
        parentFolderId: Long = -1L,
        color: String = "#EEAA3B"
    ): Long = withContext(Dispatchers.IO) {
        val parent = if (parentFolderId > 0) folderDao.getFolderById(parentFolderId) else null
        val parentPath = parent?.let { "${it.path}${it.id}/" } ?: "/"
        val folder = Folder(
            name = name.trim(),
            color = color,
            parentFolderId = parentFolderId,
            path = parentPath
        )
        folderDao.insertFolder(folder)
    }

    suspend fun createFolder(
        name: String,
        color: String
    ): Long = createFolder(name = name, parentFolderId = -1L, color = color)

    suspend fun getBreadcrumbs(folderId: Long): List<Folder> = withContext(Dispatchers.IO) {
        if (folderId <= 0) return@withContext emptyList()
        val trail = mutableListOf<Folder>()
        var currentId: Long? = folderId
        val visited = mutableSetOf<Long>()
        while (currentId != null && currentId > 0 && visited.add(currentId)) {
            val folder = folderDao.getFolderById(currentId) ?: break
            trail.add(0, folder)
            currentId = folder.parentFolderId.takeIf { it > 0 }
        }
        trail
    }

    suspend fun moveFolder(folderId: Long, targetParentFolderId: Long) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderById(folderId) ?: return@withContext
        if (folder.id == targetParentFolderId) return@withContext

        // Prevent moving a folder into its own descendant
        if (targetParentFolderId > 0) {
            val targetBreadcrumbs = getBreadcrumbs(targetParentFolderId)
            if (targetBreadcrumbs.any { it.id == folderId }) {
                throw IllegalArgumentException("Cannot move folder into one of its descendants")
            }
        }

        val oldDescendantPrefix = "${folder.path}${folder.id}/"
        val targetParent = if (targetParentFolderId > 0) folderDao.getFolderById(targetParentFolderId) else null
        val newParentPath = targetParent?.let { "${it.path}${it.id}/" } ?: "/"
        val newDescendantPrefix = "${newParentPath}${folder.id}/"

        // Update the folder itself
        folderDao.updateFolder(folder.copy(parentFolderId = targetParentFolderId, path = newParentPath))

        // Update all descendant paths
        folderDao.updateFolderPathPrefix(oldPrefix = oldDescendantPrefix, newPrefix = newDescendantPrefix)
    }

    suspend fun deleteFolderCascade(folder: Folder, deleteContainedDocuments: Boolean = false) = withContext(Dispatchers.IO) {
        val descendantPrefix = "${folder.path}${folder.id}/"
        val allFolders = folderDao.getAllFoldersList()
        val affectedFolders = allFolders.filter { it.id == folder.id || it.path.startsWith(descendantPrefix) }

        for (f in affectedFolders) {
            if (deleteContainedDocuments) {
                val docs = documentDao.getDocumentsListInFolder(f.id)
                docs.forEach { deleteDocument(it) }
            } else {
                documentDao.unlinkDocumentsFromFolder(f.id)
            }
            folderDao.deleteFolderById(f.id)
        }
    }

    suspend fun renameFolder(folderId: Long, newName: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderById(folderId) ?: return@withContext
        folderDao.updateFolder(folder.copy(name = newName.trim()))
        val docs = documentDao.getDocumentsListInFolder(folderId)
        docs.forEach { syncDocumentMetadata(it.id) }
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        documentDao.unlinkDocumentsFromFolder(folder.id)
        folderDao.deleteFolder(folder)
    }

    suspend fun getFolderDocCount(folderId: Long): Int = withContext(Dispatchers.IO) {
        documentDao.getFolderDocCount(folderId)
    }

    suspend fun moveDocumentToFolder(docId: Long, folderId: Long) = withContext(Dispatchers.IO) {
        documentDao.updateDocumentFolder(docId, folderId)
        syncDocumentMetadata(docId)
    }

    suspend fun copyDocumentToFolder(docId: Long, targetFolderId: Long): Long = withContext(Dispatchers.IO) {
        val origDoc = documentDao.getDocumentById(docId)
            ?: throw IllegalArgumentException("Document with id $docId not found")
        val origPages = pageDao.getPagesListForDocument(docId)

        val scansDir = StorageVault.getScansDirectory(context)
        val thumbsDir = StorageVault.getThumbsDirectory(context)

        val now = System.currentTimeMillis()
        val newDoc = Document(
            title = "${origDoc.title} (Copy)",
            createdTime = now,
            modifiedTime = now,
            pageCount = origPages.size,
            tags = origDoc.tags,
            folderId = targetFolderId
        )
        val newDocId = documentDao.insertDocument(newDoc)

        val copiedFiles = mutableListOf<File>()
        try {
            val newPages = mutableListOf<Page>()
            for ((idx, origPage) in origPages.withIndex()) {
                val timestamp = now + idx
                val newProcessed = File(scansDir, "page_${newDocId}_${timestamp}.jpg")
                val newOriginal = File(scansDir, "orig_${newDocId}_${timestamp}.jpg")
                val newThumb = File(thumbsDir, "thumb_${newDocId}_${timestamp}.jpg")

                val origProcessedFile = File(origPage.processedImagePath)
                if (origProcessedFile.exists()) {
                    origProcessedFile.copyTo(newProcessed, overwrite = true)
                    copiedFiles.add(newProcessed)
                }

                val origOriginalFile = File(origPage.originalImagePath)
                if (origOriginalFile.exists()) {
                    origOriginalFile.copyTo(newOriginal, overwrite = true)
                    copiedFiles.add(newOriginal)
                } else if (newProcessed.exists()) {
                    newProcessed.copyTo(newOriginal, overwrite = true)
                    copiedFiles.add(newOriginal)
                }

                val origThumbFile = File(origPage.thumbnailPath)
                if (origThumbFile.exists()) {
                    origThumbFile.copyTo(newThumb, overwrite = true)
                    copiedFiles.add(newThumb)
                }

                val newPage = Page(
                    documentId = newDocId,
                    pageIndex = origPage.pageIndex,
                    processedImagePath = if (newProcessed.exists()) newProcessed.absolutePath else origPage.processedImagePath,
                    originalImagePath = if (newOriginal.exists()) newOriginal.absolutePath else origPage.originalImagePath,
                    thumbnailPath = if (newThumb.exists()) newThumb.absolutePath else origPage.thumbnailPath,
                    cropBorderJson = origPage.cropBorderJson,
                    enhanceMode = origPage.enhanceMode,
                    rotationDegrees = origPage.rotationDegrees,
                    brightness = origPage.brightness,
                    contrast = origPage.contrast,
                    ocrText = origPage.ocrText,
                    watermarkText = origPage.watermarkText,
                    createdTime = now
                )
                newPages.add(newPage)
            }

            if (newPages.isNotEmpty()) {
                pageDao.insertPages(newPages)
            }
            syncDocumentMetadata(newDocId)
            newDocId
        } catch (e: Exception) {
            copiedFiles.forEach { it.delete() }
            documentDao.deleteDocument(newDoc.copy(id = newDocId))
            throw e
        }
    }

    suspend fun batchMoveToFolder(docIds: List<Long>, folderId: Long) = withContext(Dispatchers.IO) {
        documentDao.batchUpdateFolder(docIds, folderId)
        docIds.forEach { syncDocumentMetadata(it) }
    }

    suspend fun batchCopyDocumentsToFolder(docIds: List<Long>, targetFolderId: Long): List<Long> = withContext(Dispatchers.IO) {
        docIds.map { copyDocumentToFolder(it, targetFolderId) }
    }

    suspend fun mergeDocuments(
        docIds: List<Long>,
        mergedTitle: String? = null,
        targetFolderId: Long = -1L,
        deleteOriginals: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        if (docIds.isEmpty()) return@withContext -1L
        val now = System.currentTimeMillis()
        val title = mergedTitle?.trim()?.ifEmpty { null }
            ?: ("Merged Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date(now)))

        val mergedDoc = Document(
            title = title,
            createdTime = now,
            modifiedTime = now,
            pageCount = 0,
            folderId = targetFolderId
        )
        val mergedDocId = documentDao.insertDocument(mergedDoc)

        val scansDir = StorageVault.getScansDirectory(context)
        val thumbsDir = StorageVault.getThumbsDirectory(context)

        var pageIndexCounter = 0
        val newPages = mutableListOf<Page>()

        for (docId in docIds) {
            val pages = pageDao.getPagesListForDocument(docId).sortedBy { it.pageIndex }
            for (origPage in pages) {
                val timestamp = now + pageIndexCounter
                val newProcessed = File(scansDir, "page_${mergedDocId}_${timestamp}.jpg")
                val newOriginal = File(scansDir, "orig_${mergedDocId}_${timestamp}.jpg")
                val newThumb = File(thumbsDir, "thumb_${mergedDocId}_${timestamp}.jpg")

                val origProcessedFile = File(origPage.processedImagePath)
                if (origProcessedFile.exists()) {
                    origProcessedFile.copyTo(newProcessed, overwrite = true)
                }

                val origOriginalFile = File(origPage.originalImagePath)
                if (origOriginalFile.exists()) {
                    origOriginalFile.copyTo(newOriginal, overwrite = true)
                } else if (newProcessed.exists()) {
                    newProcessed.copyTo(newOriginal, overwrite = true)
                }

                val origThumbFile = File(origPage.thumbnailPath)
                if (origThumbFile.exists()) {
                    origThumbFile.copyTo(newThumb, overwrite = true)
                }

                val newPage = Page(
                    documentId = mergedDocId,
                    pageIndex = pageIndexCounter,
                    processedImagePath = if (newProcessed.exists()) newProcessed.absolutePath else origPage.processedImagePath,
                    originalImagePath = if (newOriginal.exists()) newOriginal.absolutePath else origPage.originalImagePath,
                    thumbnailPath = if (newThumb.exists()) newThumb.absolutePath else origPage.thumbnailPath,
                    cropBorderJson = origPage.cropBorderJson,
                    enhanceMode = origPage.enhanceMode,
                    rotationDegrees = origPage.rotationDegrees,
                    brightness = origPage.brightness,
                    contrast = origPage.contrast,
                    ocrText = origPage.ocrText,
                    watermarkText = origPage.watermarkText,
                    createdTime = now
                )
                newPages.add(newPage)
                pageIndexCounter++
            }
        }

        if (newPages.isNotEmpty()) {
            pageDao.insertPages(newPages)
            documentDao.updatePageCount(mergedDocId, newPages.size)
        }

        if (deleteOriginals) {
            batchDeleteDocuments(docIds)
        }

        syncDocumentMetadata(mergedDocId)
        mergedDocId
    }

    suspend fun batchDeleteDocuments(docIds: List<Long>) = withContext(Dispatchers.IO) {
        for (id in docIds) {
            val pages = pageDao.getPagesListForDocument(id)
            pages.forEach { p ->
                File(p.processedImagePath).delete()
                File(p.originalImagePath).delete()
                File(p.thumbnailPath).delete()
            }
        }
        documentDao.batchDeleteDocuments(docIds)
        for (id in docIds) {
            removeDocumentMetadata(id)
        }
    }

    // --- Page Operations ---
    fun getPagesForDocument(docId: Long): Flow<List<Page>> = pageDao.getPagesForDocument(docId)

    suspend fun getPagesListForDocument(docId: Long): List<Page> = pageDao.getPagesListForDocument(docId)

    suspend fun createNewDocument(title: String? = null, folderId: Long = -1L): Long = withContext(Dispatchers.IO) {
        val defaultTitle = title?.ifBlank { null }
            ?: try {
                SettingsManager.getInstance(context).generateDocumentTitle().ifBlank { null }
            } catch (e: Exception) {
                null
            }
            ?: ("New Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date()))
        val doc = Document(title = defaultTitle, folderId = folderId)
        val docId = documentDao.insertDocument(doc)
        syncDocumentMetadata(docId)
        docId
    }

    suspend fun renameDocument(docId: Long, title: String) = withContext(Dispatchers.IO) {
        documentDao.updateDocumentTitle(docId, title)
        syncDocumentMetadata(docId)
    }

    suspend fun importPdfDocument(uri: Uri, folderId: Long = -1L): Long = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext -1L
        try {
            val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
            val maxPages = 100 // Cap maximum imported pages to prevent memory and storage exhaustion
            val count = minOf(pdfRenderer.pageCount, maxPages)
            if (count == 0) {
                pdfRenderer.close()
                return@withContext -1L
            }
            val title = "PDF " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            val docId = createNewDocument(title, folderId)
            for (i in 0 until count) {
                val page = pdfRenderer.openPage(i)
                val w = (page.width * 2).coerceIn(400, 3000)
                val h = (page.height * 2).coerceIn(600, 4200)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                savePage(
                    documentId = docId,
                    processedBitmap = bmp,
                    originalBitmap = bmp,
                    cropBorderJson = "",
                    enhanceModeId = 0
                )
                if (!bmp.isRecycled) bmp.recycle()
            }
            pdfRenderer.close()
            docId
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        } finally {
            pfd.close()
        }
    }

    suspend fun savePage(
        documentId: Long,
        processedBitmap: Bitmap,
        originalBitmap: Bitmap?,
        cropBorderJson: String,
        enhanceModeId: Int,
        fileNamePrefix: String? = null
    ): Page = withContext(Dispatchers.IO) {
        val scansDir = StorageVault.getScansDirectory(context)
        val thumbsDir = StorageVault.getThumbsDirectory(context)

        val timestamp = System.currentTimeMillis()
        val prefix = if (!fileNamePrefix.isNullOrBlank()) {
            fileNamePrefix
        } else {
            "page_${documentId}"
        }
        val processedFile = File(scansDir, "${prefix}_${timestamp}.jpg")
        val originalFile = File(scansDir, "orig_${prefix}_${timestamp}.jpg")
        val thumbFile = File(thumbsDir, "thumb_${prefix}_${timestamp}.jpg")

        // Save processed full image
        FileOutputStream(processedFile).use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        // Save original photo (for re-cropping later)
        if (originalBitmap != null) {
            FileOutputStream(originalFile).use { out ->
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }

        // Generate and save thumbnail
        val thumb = Bitmap.createScaledBitmap(
            processedBitmap,
            240,
            (240f * processedBitmap.height / processedBitmap.width).toInt().coerceAtLeast(1),
            true
        )
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        val existingPages = pageDao.getPagesListForDocument(documentId)
        val newPageIndex = existingPages.size

        val page = Page(
            documentId = documentId,
            pageIndex = newPageIndex,
            processedImagePath = processedFile.absolutePath,
            originalImagePath = originalFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            cropBorderJson = cropBorderJson,
            enhanceMode = enhanceModeId
        )

        val pageId = pageDao.insertPage(page)
        documentDao.updatePageCount(documentId, existingPages.size + 1)
        syncDocumentMetadata(documentId)

        page.copy(id = pageId)
    }

    suspend fun updatePage(page: Page) = withContext(Dispatchers.IO) {
        pageDao.updatePage(page)
        syncDocumentMetadata(page.documentId)
    }

    suspend fun updatePageAnnotations(pageId: Long, annotationsJson: String) = withContext(Dispatchers.IO) {
        pageDao.updatePageAnnotations(pageId, annotationsJson)
        val p = pageDao.getPageById(pageId)
        if (p != null) {
            syncDocumentMetadata(p.documentId)
        }
    }

    suspend fun updateDocument(document: Document) = withContext(Dispatchers.IO) {
        documentDao.updateDocument(document)
        syncDocumentMetadata(document.id)
    }

    suspend fun updateDocumentTitle(docId: Long, title: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(title = title, modifiedTime = System.currentTimeMillis()))
            syncDocumentMetadata(docId)
        }
    }

    suspend fun stampSignatureOnPage(
        pageId: Long,
        signatureBitmap: Bitmap,
        relX: Float = 0.55f,
        relY: Float = 0.75f,
        relWidth: Float = 0.35f,
        relHeight: Float = -1f
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val processedFile = File(page.processedImagePath)
        if (!processedFile.exists()) return@withContext

        val origBitmap = BitmapFactory.decodeFile(processedFile.absolutePath) ?: return@withContext
        val mutable = origBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutable)

        val targetWidth = (mutable.width * relWidth).toInt().coerceAtLeast(10)
        val targetHeight = if (relHeight > 0f) {
            (mutable.height * relHeight).toInt().coerceAtLeast(10)
        } else {
            (targetWidth * signatureBitmap.height / signatureBitmap.width.toFloat()).toInt().coerceAtLeast(10)
        }

        val left = (mutable.width * relX).toInt().coerceIn(0, (mutable.width - targetWidth).coerceAtLeast(0))
        val top = (mutable.height * relY).toInt().coerceIn(0, (mutable.height - targetHeight).coerceAtLeast(0))
        val destRect = android.graphics.Rect(left, top, left + targetWidth, top + targetHeight)

        canvas.drawBitmap(signatureBitmap, null, destRect, null)

        FileOutputStream(processedFile).use { out ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val thumbFile = File(page.thumbnailPath)
        if (thumbFile.exists()) {
            val thumb = Bitmap.createScaledBitmap(mutable, 240, (240f * mutable.height / mutable.width).toInt().coerceAtLeast(1), true)
            FileOutputStream(thumbFile).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }

        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
            syncDocumentMetadata(page.documentId)
        }
    }

    suspend fun updatePageBitmap(
        pageId: Long,
        newBitmap: Bitmap,
        cropBorderJson: String? = null,
        enhanceModeId: Int? = null,
        rotationDegrees: Int? = null,
        brightness: Int? = null,
        contrast: Int? = null
    ): Page? = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext null
        val processedFile = File(page.processedImagePath)
        FileOutputStream(processedFile).use { out ->
            newBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val thumbFile = File(page.thumbnailPath)
        val thumb = Bitmap.createScaledBitmap(
            newBitmap,
            240,
            (240f * newBitmap.height / newBitmap.width).toInt().coerceAtLeast(1),
            true
        )
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        val updatedPage = page.copy(
            cropBorderJson = cropBorderJson ?: page.cropBorderJson,
            enhanceMode = enhanceModeId ?: page.enhanceMode,
            rotationDegrees = rotationDegrees ?: page.rotationDegrees,
            brightness = brightness ?: page.brightness,
            contrast = contrast ?: page.contrast
        )
        pageDao.updatePage(updatedPage)
        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(page.documentId)
        updatedPage
    }

    suspend fun applyWatermarkToPage(
        pageId: Long,
        watermarkText: String,
        color: Int = 0x88888888.toInt(),
        opacity: Float = 0.35f,
        isTiled: Boolean = false,
        angle: Float = -45f
    ): Page? = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext null
        val file = File(page.processedImagePath)
        if (!file.exists()) return@withContext null
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        CollageGenerator.drawWatermarkOnCanvas(
            canvas = canvas,
            canvasWidth = mutable.width,
            canvasHeight = mutable.height,
            config = com.krinry.scancraft.cv.CollageWatermarkConfig(
                text = watermarkText,
                color = color,
                opacity = opacity,
                isTiled = isTiled,
                rotationAngle = angle
            )
        )
        FileOutputStream(file).use { out ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val thumbFile = File(page.thumbnailPath)
        val thumb = Bitmap.createScaledBitmap(
            mutable,
            240,
            (240f * mutable.height / mutable.width).toInt().coerceAtLeast(1),
            true
        )
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        val updatedPage = page.copy(watermarkText = watermarkText)
        pageDao.updatePage(updatedPage)
        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(page.documentId)
        updatedPage
    }

    suspend fun replacePageWithPhoto(
        pageId: Long,
        newPhotoPath: String
    ): Page? = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext null
        val newPhotoFile = File(newPhotoPath)
        if (!newPhotoFile.exists()) return@withContext null

        val bmp = BitmapFactory.decodeFile(newPhotoPath) ?: return@withContext null
        // Save as original photo for future re-cropping
        val origFile = File(page.originalImagePath)
        FileOutputStream(origFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        // Save as processed
        val processedFile = File(page.processedImagePath)
        FileOutputStream(processedFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        // Save thumbnail
        val thumbFile = File(page.thumbnailPath)
        val thumb = Bitmap.createScaledBitmap(
            bmp,
            240,
            (240f * bmp.height / bmp.width).toInt().coerceAtLeast(1),
            true
        )
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        val updatedPage = page.copy(cropBorderJson = "", rotationDegrees = 0)
        pageDao.updatePage(updatedPage)
        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(page.documentId)
        updatedPage
    }

    suspend fun savePageToGallery(page: Page): Boolean = withContext(Dispatchers.IO) {
        val srcFile = File(page.processedImagePath)
        if (!srcFile.exists()) return@withContext false

        val fileName = "ScanCraft_${System.currentTimeMillis()}_${page.pageIndex + 1}.jpg"
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
                    return@withContext true
                }
            } else {
                val picsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ScanCraft"
                ).apply { if (!exists()) mkdirs() }
                val destFile = File(picsDir, fileName)
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(destFile).use { out -> input.copyTo(out) }
                }
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }

    suspend fun createCollagePage(
        documentId: Long,
        pageIds: List<Long>,
        layout: CollageLayout,
        watermarkConfig: com.krinry.scancraft.cv.CollageWatermarkConfig? = null,
        slotTransforms: Map<Int, com.krinry.scancraft.cv.SlotTransform>? = null,
        drawBorders: Boolean = false,
        slotCustomPaths: Map<Int, String>? = null,
        customFileName: String? = null
    ): Page = withContext(Dispatchers.IO) {
        val targetSlotRects = CollageGenerator.calculateSlotRects(layout)
        val bitmaps = mutableListOf<Bitmap?>()
        val slotCount = layout.maxPages

        for (index in 0 until slotCount) {
            val customPath = slotCustomPaths?.get(index)
            val path = if (!customPath.isNullOrBlank() && File(customPath).exists()) {
                customPath
            } else {
                val pId = pageIds.getOrNull(index) ?: pageIds.getOrNull(index % pageIds.size.coerceAtLeast(1))
                if (pId != null && pId != 0L) {
                    val page = pageDao.getPageById(pId)
                    page?.processedImagePath.orEmpty()
                } else ""
            }

            if (path.isNotBlank() && File(path).exists()) {
                val slot = targetSlotRects.getOrNull(index)
                val bmp = if (slot != null) {
                    CollageGenerator.decodeSampledBitmap(
                        filePath = path,
                        reqWidth = slot.right - slot.left,
                        reqHeight = slot.bottom - slot.top
                    )
                } else {
                    BitmapFactory.decodeFile(path)
                }
                bitmaps.add(bmp)
            } else {
                bitmaps.add(null)
            }
        }

        val nonNullBitmaps = bitmaps.filterNotNull()
        if (nonNullBitmaps.isEmpty()) {
            throw IllegalStateException("No valid page images found for collage")
        }

        val collageBitmap = try {
            CollageGenerator.createCollage(
                bitmaps = bitmaps,
                layout = layout,
                drawBorders = drawBorders,
                watermarkConfig = watermarkConfig,
                slotTransforms = slotTransforms
            )
        } finally {
            // Recycle intermediate downsampled page bitmaps to prevent heap bloat
            nonNullBitmaps.forEach { bmp ->
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }

        val doc = documentDao.getDocumentById(documentId)
        val cleanDocTitle = doc?.title?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")?.ifBlank { "doc_${documentId}" } ?: "doc_${documentId}"
        val baseName = customFileName ?: cleanDocTitle
        val collagePrefix = if (baseName.startsWith("collage-")) baseName else "collage-${baseName}"

        savePage(
            documentId = documentId,
            processedBitmap = collageBitmap,
            originalBitmap = collageBitmap,
            cropBorderJson = "",
            enhanceModeId = 1,
            fileNamePrefix = collagePrefix
        )
    }

    suspend fun createCollagePages(
        documentId: Long,
        specs: List<CollagePageSpec>,
        watermarkConfig: com.krinry.scancraft.cv.CollageWatermarkConfig? = null,
        drawBorders: Boolean = false,
        customFileName: String? = null
    ): List<Page> = withContext(Dispatchers.IO) {
        specs.mapIndexed { specIndex, spec ->
            val pageSuffix = if (specs.size > 1) "_p${specIndex + 1}" else ""
            val specFileName = customFileName?.let { "$it$pageSuffix" }
            createCollagePage(
                documentId = documentId,
                pageIds = spec.pageIds,
                layout = spec.layout,
                watermarkConfig = watermarkConfig,
                slotTransforms = spec.slotTransforms,
                drawBorders = drawBorders,
                slotCustomPaths = spec.slotCustomPaths,
                customFileName = specFileName
            )
        }
    }

    suspend fun createCollageDocument(
        sourceDocId: Long,
        specs: List<CollagePageSpec>,
        watermarkConfig: com.krinry.scancraft.cv.CollageWatermarkConfig? = null,
        drawBorders: Boolean = false,
        customTitle: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val sourceDoc = documentDao.getDocumentById(sourceDocId)
        val baseTitle = sourceDoc?.title?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")?.ifBlank { "scan" } ?: "scan"
        val docTitle = customTitle ?: if (baseTitle.startsWith("collage-")) baseTitle else "collage-${baseTitle}"
        val newDocId = createNewDocument(title = docTitle, folderId = sourceDoc?.folderId ?: -1L)
        createCollagePages(
            documentId = newDocId,
            specs = specs,
            watermarkConfig = watermarkConfig,
            drawBorders = drawBorders,
            customFileName = baseTitle
        )
        newDocId
    }

    suspend fun reorderPages(documentId: Long, orderedPages: List<Page>) = withContext(Dispatchers.IO) {
        orderedPages.forEachIndexed { index, page ->
            pageDao.updatePage(page.copy(pageIndex = index))
        }
        val doc = documentDao.getDocumentById(documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(documentId)
    }

    suspend fun stitchIdCardPages(
        frontBitmap: Bitmap,
        backBitmap: Bitmap,
        targetDocId: Long? = null
    ): Page = withContext(Dispatchers.IO) {
        val docId = if (targetDocId != null && targetDocId > 0) {
            targetDocId
        } else {
            createNewDocument("ID Card Scan")
        }

        val a4Width = 2480
        val a4Height = 3508
        val a4Bitmap = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(a4Bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val cardW = (a4Width * 0.70f).toInt() // 1736 px
        val cardH = Math.round(cardW / 1.586f).toInt() // 1095 px
        val cardX = (a4Width - cardW) / 2 // 372 px

        val frontY = (a4Height * 0.20f).toInt() // 701 px
        val backY = (a4Height * 0.56f).toInt() // 1964 px

        val cardCornerRadius = 16f

        val bitmapPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E0E0E0")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Draw Front Card with rounded clipping and #E0E0E0 border
        val frontRectF = android.graphics.RectF(
            cardX.toFloat(),
            frontY.toFloat(),
            (cardX + cardW).toFloat(),
            (frontY + cardH).toFloat()
        )
        val frontPath = android.graphics.Path().apply {
            addRoundRect(frontRectF, cardCornerRadius, cardCornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(frontPath)
        canvas.drawBitmap(frontBitmap, null, frontRectF, bitmapPaint)
        canvas.restore()
        canvas.drawRoundRect(frontRectF, cardCornerRadius, cardCornerRadius, borderPaint)

        // Draw Back Card with rounded clipping and #E0E0E0 border
        val backRectF = android.graphics.RectF(
            cardX.toFloat(),
            backY.toFloat(),
            (cardX + cardW).toFloat(),
            (backY + cardH).toFloat()
        )
        val backPath = android.graphics.Path().apply {
            addRoundRect(backRectF, cardCornerRadius, cardCornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(backPath)
        canvas.drawBitmap(backBitmap, null, backRectF, bitmapPaint)
        canvas.restore()
        canvas.drawRoundRect(backRectF, cardCornerRadius, cardCornerRadius, borderPaint)

        savePage(
            documentId = docId,
            processedBitmap = a4Bitmap,
            originalBitmap = a4Bitmap,
            cropBorderJson = "",
            enhanceModeId = 1
        )
    }

    /**
     * Splits a book spread bitmap into Left and Right pages, applies 1D cylindrical curvature unwarping
     * and crease shadow compensation, and saves both as two consecutive document pages.
     *
     * @return Pair of (leftPageId, rightPageId)
     */
    suspend fun splitAndSaveBookPages(
        spreadBitmap: Bitmap,
        spineX: Int,
        targetDocId: Long? = null
    ): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val docId = if (targetDocId != null && targetDocId > 0) {
            targetDocId
        } else {
            val defaultTitle = "Book Scan " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            createNewDocument(title = defaultTitle)
        }

        val width = spreadBitmap.width
        val validSpineX = spineX.coerceIn(
            (width * 0.15f).toInt().coerceAtLeast(1),
            (width * 0.85f).toInt().coerceAtMost(width - 1)
        )

        val (unwarpedLeft, unwarpedRight) = BookSpineDetector.splitAndUnwarpSpread(
            spreadBitmap = spreadBitmap,
            spineX = validSpineX
        )

        val leftPage = try {
            savePage(
                documentId = docId,
                processedBitmap = unwarpedLeft,
                originalBitmap = unwarpedLeft,
                cropBorderJson = "",
                enhanceModeId = 1
            )
        } finally {
            if (!unwarpedLeft.isRecycled) {
                unwarpedLeft.recycle()
            }
        }

        val rightPage = try {
            savePage(
                documentId = docId,
                processedBitmap = unwarpedRight,
                originalBitmap = unwarpedRight,
                cropBorderJson = "",
                enhanceModeId = 1
            )
        } finally {
            if (!unwarpedRight.isRecycled) {
                unwarpedRight.recycle()
            }
        }

        Pair(leftPage.id, rightPage.id)
    }

    suspend fun deleteDocument(document: Document) = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesListForDocument(document.id)
        pages.forEach { p ->
            File(p.processedImagePath).delete()
            File(p.originalImagePath).delete()
            File(p.thumbnailPath).delete()
        }
        documentDao.deleteDocument(document)
        removeDocumentMetadata(document.id)
    }

    suspend fun deletePage(page: Page) = withContext(Dispatchers.IO) {
        File(page.processedImagePath).delete()
        File(page.originalImagePath).delete()
        File(page.thumbnailPath).delete()
        pageDao.deletePage(page)

        val remaining = pageDao.getPagesListForDocument(page.documentId)
        remaining.forEachIndexed { index, p ->
            if (p.pageIndex != index) {
                pageDao.updatePage(p.copy(pageIndex = index))
            }
        }
        documentDao.updatePageCount(page.documentId, remaining.size)
        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(page.documentId)
    }

    suspend fun batchDeletePages(pages: List<Page>) = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext
        val docId = pages.first().documentId
        pages.forEach { p ->
            File(p.processedImagePath).delete()
            File(p.originalImagePath).delete()
            File(p.thumbnailPath).delete()
            pageDao.deletePage(p)
        }

        val remaining = pageDao.getPagesListForDocument(docId)
        remaining.forEachIndexed { index, p ->
            if (p.pageIndex != index) {
                pageDao.updatePage(p.copy(pageIndex = index))
            }
        }
        documentDao.updatePageCount(docId, remaining.size)
        val doc = documentDao.getDocumentById(docId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(docId)
    }

    suspend fun movePagesToDocument(pages: List<Page>, targetDocId: Long) = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext
        val sourceDocId = pages.first().documentId
        val existingTargetPages = pageDao.getPagesListForDocument(targetDocId)

        pages.forEachIndexed { index, p ->
            pageDao.updatePage(
                p.copy(
                    documentId = targetDocId,
                    pageIndex = existingTargetPages.size + index
                )
            )
        }

        // Re-index remaining pages in source document
        val remainingSourcePages = pageDao.getPagesListForDocument(sourceDocId)
        remainingSourcePages.forEachIndexed { index, p ->
            if (p.pageIndex != index) {
                pageDao.updatePage(p.copy(pageIndex = index))
            }
        }

        documentDao.updatePageCount(sourceDocId, remainingSourcePages.size)
        documentDao.updatePageCount(targetDocId, existingTargetPages.size + pages.size)

        val sourceDoc = documentDao.getDocumentById(sourceDocId)
        if (sourceDoc != null) {
            documentDao.updateDocument(sourceDoc.copy(modifiedTime = System.currentTimeMillis()))
        }
        val targetDoc = documentDao.getDocumentById(targetDocId)
        if (targetDoc != null) {
            documentDao.updateDocument(targetDoc.copy(modifiedTime = System.currentTimeMillis()))
        }
        syncDocumentMetadata(sourceDocId)
        syncDocumentMetadata(targetDocId)
    }

    suspend fun copyPagesToDocument(pages: List<Page>, targetDocId: Long): List<Page> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext emptyList()
        val scansDir = StorageVault.getScansDirectory(context)
        val thumbsDir = StorageVault.getThumbsDirectory(context)
        val existingTargetPages = pageDao.getPagesListForDocument(targetDocId)
        val copiedPages = mutableListOf<Page>()
        val copiedFiles = mutableListOf<File>()

        try {
            pages.forEachIndexed { index, p ->
                val timestamp = System.currentTimeMillis() + index
                val newProc = File(scansDir, "page_${targetDocId}_${timestamp}.jpg")
                val newOrig = File(scansDir, "orig_${targetDocId}_${timestamp}.jpg")
                val newThumb = File(thumbsDir, "thumb_${targetDocId}_${timestamp}.jpg")

                val procFile = File(p.processedImagePath)
                if (procFile.exists()) {
                    procFile.copyTo(newProc, overwrite = true)
                    copiedFiles.add(newProc)
                }

                val origFile = File(p.originalImagePath)
                if (origFile.exists()) {
                    origFile.copyTo(newOrig, overwrite = true)
                    copiedFiles.add(newOrig)
                }

                val thumbFile = File(p.thumbnailPath)
                if (thumbFile.exists()) {
                    thumbFile.copyTo(newThumb, overwrite = true)
                    copiedFiles.add(newThumb)
                }

                val newPage = Page(
                    documentId = targetDocId,
                    pageIndex = existingTargetPages.size + index,
                    processedImagePath = if (newProc.exists()) newProc.absolutePath else p.processedImagePath,
                    originalImagePath = if (newOrig.exists()) newOrig.absolutePath else p.originalImagePath,
                    thumbnailPath = if (newThumb.exists()) newThumb.absolutePath else p.thumbnailPath,
                    cropBorderJson = p.cropBorderJson,
                    enhanceMode = p.enhanceMode
                )
                val newId = pageDao.insertPage(newPage)
                copiedPages.add(newPage.copy(id = newId))
            }

            documentDao.updatePageCount(targetDocId, existingTargetPages.size + pages.size)
            val targetDoc = documentDao.getDocumentById(targetDocId)
            if (targetDoc != null) {
                documentDao.updateDocument(targetDoc.copy(modifiedTime = System.currentTimeMillis()))
            }
            syncDocumentMetadata(targetDocId)
            copiedPages
        } catch (e: Exception) {
            copiedFiles.forEach { it.delete() }
            copiedPages.forEach { runCatching { pageDao.deletePage(it) } }
            throw e
        }
    }

    suspend fun copyPagesWithinDocument(pages: List<Page>): List<Page> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext emptyList()
        val docId = pages.first().documentId
        copyPagesToDocument(pages, docId)
    }

    private val documentSyncLocks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    /**
     * Serializes snapshot capture and disk synchronization per document to guarantee
     * older asynchronous reads never overwrite newer mutations.
     */
    private suspend fun syncDocumentMetadata(docId: Long) {
        val lock = documentSyncLocks.computeIfAbsent(docId) { Mutex() }
        lock.withLock {
            try {
                val doc = documentDao.getDocumentById(docId) ?: return@withLock
                val pages = pageDao.getPagesListForDocument(docId)
                val folder = if (doc.folderId > 0) folderDao.getFolderById(doc.folderId) else null
                StorageVault.saveDocumentMetadata(context, doc, pages, folder?.name)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }
    }

    /**
     * Removes document metadata from the storage vault under the per-document lock.
     */
    private suspend fun removeDocumentMetadata(docId: Long) {
        val lock = documentSyncLocks.computeIfAbsent(docId) { Mutex() }
        lock.withLock {
            try {
                StorageVault.deleteDocumentMetadata(context, docId)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }
    }
}

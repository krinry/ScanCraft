package com.krinry.scancraft.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.krinry.scancraft.data.local.AppDatabase
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Automatically detects and restores scanned documents from the public storage vault
 * ('Documents/ScanCraft') if the app is reinstalled after deletion.
 */
object DocumentRecoveryManager {

    /**
     * Checks if local database is empty. If so, automatically scans the storage vault
     * and restores all documents and pages.
     */
    suspend fun autoRestoreIfDatabaseEmpty(context: Context, database: AppDatabase): Int = withContext(Dispatchers.IO) {
        val existingDocs = database.documentDao().getAllDocumentsList()
        if (existingDocs.isNotEmpty()) {
            return@withContext 0
        }
        scanAndRestoreFromVault(context, database)
    }

    /**
     * Prevents path traversal vulnerabilities by enforcing strict file name boundaries.
     */
    private fun safeFileName(name: String): String? {
        if (name.isBlank()) return null
        val base = File(name).name
        return if (base == name && !name.contains("..") && !name.contains("/") && !name.contains("\\")) {
            name
        } else {
            null
        }
    }

    private val recoveryMutex = Mutex()

    /**
     * Scans Documents/ScanCraft/meta/ and imports all backed up documents atomically.
     * Prevents duplicate entries when recovery is triggered multiple times or concurrently.
     */
    suspend fun scanAndRestoreFromVault(context: Context, database: AppDatabase): Int = withContext(Dispatchers.IO) {
        recoveryMutex.withLock {
            val publicVaultDir = try {
                val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                if (docs != null && docs.isAbsolute) File(docs, StorageVault.VAULT_FOLDER_NAME) else null
            } catch (_: Throwable) { null }

            val candidateMetaDirs = listOfNotNull(
                StorageVault.getMetaDirectory(context),
                publicVaultDir?.let { File(it, "meta") }
            ).distinct().filter { it.exists() }

            val metaFiles = candidateMetaDirs.flatMap { dir ->
                dir.listFiles { file -> file.isFile && file.name.endsWith("_meta.json") }?.toList().orEmpty()
            }.distinctBy { it.name }

            if (metaFiles.isEmpty()) return@withLock 0

            val candidateScanDirs = listOfNotNull(
                StorageVault.getScansDirectory(context),
                publicVaultDir?.let { File(it, "scans") },
                File(context.filesDir, "scans"),
                File(context.getExternalFilesDir(null) ?: context.filesDir, "scans")
            ).distinct().filter { it.exists() }

            val candidateThumbDirs = listOfNotNull(
                StorageVault.getThumbsDirectory(context),
                publicVaultDir?.let { File(it, "thumbs") },
                File(context.filesDir, "thumbs"),
                File(context.getExternalFilesDir(null) ?: context.filesDir, "thumbs")
            ).distinct().filter { it.exists() }

            var restoredCount = 0

            for (file in metaFiles) {
                try {
                    val jsonStr = FileInputStream(file).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(jsonStr)

                    val originalDocId = json.optLong("documentId", 0L)
                    val title = json.optString("title", "Restored Document")
                    val createdTime = json.optLong("createdTime", System.currentTimeMillis())
                    val modifiedTime = json.optLong("modifiedTime", createdTime)
                    val tags = json.optString("tags", "")
                    val folderName = json.optString("folderName", "").trim()

                    // Deduplication check: if a document with this title and createdTime is ALREADY in DB, skip!
                    val existingInDb = database.documentDao().getAllDocumentsList()
                    val alreadyExists = existingInDb.any { existing ->
                        (originalDocId > 0L && existing.id == originalDocId && existing.createdTime == createdTime) ||
                        (existing.createdTime == createdTime && existing.title == title)
                    }
                    if (alreadyExists) {
                        continue
                    }

                    val pagesArray = json.optJSONArray("pages")
                    val pageCount = pagesArray?.length() ?: 0

                    // Atomic transaction per document
                    database.withTransaction {
                        // Resolve or create folder if needed
                        var folderId: Long = -1L
                        if (folderName.isNotEmpty()) {
                            val existingFolder = database.folderDao().getFolderByName(folderName)
                            folderId = existingFolder?.id ?: run {
                                val newFolder = Folder(name = folderName, createdTime = createdTime)
                                database.folderDao().insertFolder(newFolder)
                            }
                        }

                        // Prevent ID collision: if a document already exists with originalDocId,
                        // assign a new non-conflicting ID (id = 0L) so Room auto-generates a new ID
                        // without overwriting existing records or cascade-deleting existing pages.
                        val existingWithId = if (originalDocId > 0L) database.documentDao().getDocumentById(originalDocId) else null
                        val targetId = if (originalDocId > 0L && existingWithId == null) {
                            originalDocId
                        } else {
                            0L
                        }

                        val doc = Document(
                            id = targetId,
                            title = title,
                            createdTime = createdTime,
                            modifiedTime = modifiedTime,
                            pageCount = pageCount,
                            tags = tags,
                            folderId = folderId
                        )
                        val newDocId = database.documentDao().insertDocument(doc)

                        if (pagesArray != null) {
                            for (i in 0 until pagesArray.length()) {
                                val pJson = pagesArray.getJSONObject(i)
                                val pageIndex = pJson.optInt("pageIndex", i)
                                val procName = safeFileName(pJson.optString("processedFileName", ""))
                                val origName = safeFileName(pJson.optString("originalFileName", ""))
                                val thumbName = safeFileName(pJson.optString("thumbnailFileName", ""))

                                val procFile = procName?.let { name -> candidateScanDirs.map { File(it, name) }.firstOrNull { it.exists() && it.canRead() } }
                                val origFile = origName?.let { name -> candidateScanDirs.map { File(it, name) }.firstOrNull { it.exists() && it.canRead() } }
                                val thumbFile = thumbName?.let { name -> candidateThumbDirs.map { File(it, name) }.firstOrNull { it.exists() && it.canRead() } }

                                // Mirror thumbnail into app-private storage so it remains visible even if storage permission is revoked
                                val effectiveThumbPath = if (thumbFile != null && thumbFile.canRead()) {
                                    try {
                                        val privateThumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }
                                        val privateThumb = File(privateThumbsDir, thumbFile.name)
                                        if (!privateThumb.exists() || privateThumb.length() == 0L) {
                                            thumbFile.copyTo(privateThumb, overwrite = true)
                                        }
                                        privateThumb.absolutePath
                                    } catch (_: Exception) {
                                        thumbFile.absolutePath
                                    }
                                } else {
                                    thumbFile?.absolutePath ?: ""
                                }

                                val page = Page(
                                    documentId = newDocId,
                                    pageIndex = pageIndex,
                                    processedImagePath = procFile?.absolutePath ?: "",
                                    originalImagePath = origFile?.absolutePath ?: "",
                                    thumbnailPath = effectiveThumbPath,
                                    cropBorderJson = pJson.optString("cropBorderJson", ""),
                                    enhanceMode = pJson.optInt("enhanceMode", 1),
                                    rotationDegrees = pJson.optInt("rotationDegrees", 0),
                                    brightness = pJson.optInt("brightness", 0),
                                    contrast = pJson.optInt("contrast", 0),
                                    ocrText = pJson.optString("ocrText", ""),
                                    watermarkText = pJson.optString("watermarkText", ""),
                                    annotationsJson = pJson.optString("annotationsJson", ""),
                                    createdTime = createdTime
                                )
                                database.pageDao().insertPage(page)
                            }
                        }
                    }
                    restoredCount++
                } catch (_: Exception) {
                    // Continue with remaining files
                }
            }
            restoredCount
        }
    }

    /**
     * Ensures all existing document thumbnails are cached into app-private storage
     * so they remain readable even if external storage permissions are revoked.
     */
    suspend fun syncThumbnailsToPrivateCache(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            val docs = database.documentDao().getAllDocumentsList()
            val privateThumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }
            for (doc in docs) {
                val pages = database.pageDao().getPagesListForDocument(doc.id)
                for (p in pages) {
                    val tf = if (p.thumbnailPath.isNotBlank()) File(p.thumbnailPath) else null
                    if (tf != null && tf.canRead() && tf.length() > 0) {
                        val dest = File(privateThumbsDir, tf.name)
                        if (!dest.exists() || dest.length() == 0L) {
                            try { tf.copyTo(dest, overwrite = true) } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

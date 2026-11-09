package com.krinry.scancraft.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.krinry.scancraft.data.local.AppDatabase
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import kotlinx.coroutines.Dispatchers
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

    /**
     * Scans Documents/ScanCraft/meta/ and imports all backed up documents atomically.
     */
    suspend fun scanAndRestoreFromVault(context: Context, database: AppDatabase): Int = withContext(Dispatchers.IO) {
        val metaDir = StorageVault.getMetaDirectory(context)
        if (!metaDir.exists()) return@withContext 0

        val metaFiles = metaDir.listFiles { file -> file.isFile && file.name.endsWith("_meta.json") }
        if (metaFiles.isNullOrEmpty()) return@withContext 0

        val candidateScanDirs = listOf(
            StorageVault.getScansDirectory(context),
            File(context.filesDir, "scans"),
            File(context.getExternalFilesDir(null) ?: context.filesDir, "scans")
        ).filter { it.exists() }

        val candidateThumbDirs = listOf(
            StorageVault.getThumbsDirectory(context),
            File(context.filesDir, "thumbs"),
            File(context.getExternalFilesDir(null) ?: context.filesDir, "thumbs")
        ).filter { it.exists() }

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

                    val doc = Document(
                        id = if (originalDocId > 0L) originalDocId else 0L,
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

                            val procFile = procName?.let { name -> candidateScanDirs.map { File(it, name) }.firstOrNull { it.exists() } }
                            val origFile = origName?.let { name -> candidateScanDirs.map { File(it, name) }.firstOrNull { it.exists() } }
                            val thumbFile = thumbName?.let { name -> candidateThumbDirs.map { File(it, name) }.firstOrNull { it.exists() } }

                            val page = Page(
                                documentId = newDocId,
                                pageIndex = pageIndex,
                                processedImagePath = procFile?.absolutePath ?: "",
                                originalImagePath = origFile?.absolutePath ?: "",
                                thumbnailPath = thumbFile?.absolutePath ?: "",
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

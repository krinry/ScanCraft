package com.krinry.scancraft.data.repository

import android.content.Context
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
     * Scans Documents/ScanCraft/meta/ and imports all backed up documents.
     */
    suspend fun scanAndRestoreFromVault(context: Context, database: AppDatabase): Int = withContext(Dispatchers.IO) {
        val metaDir = StorageVault.getMetaDirectory(context)
        if (!metaDir.exists()) return@withContext 0

        val metaFiles = metaDir.listFiles { file -> file.isFile && file.name.endsWith("_meta.json") }
        if (metaFiles.isNullOrEmpty()) return@withContext 0

        val scansDir = StorageVault.getScansDirectory(context)
        val thumbsDir = StorageVault.getThumbsDirectory(context)
        var restoredCount = 0

        for (file in metaFiles) {
            try {
                val jsonStr = FileInputStream(file).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(jsonStr)

                val title = json.optString("title", "Restored Document")
                val createdTime = json.optLong("createdTime", System.currentTimeMillis())
                val modifiedTime = json.optLong("modifiedTime", createdTime)
                val tags = json.optString("tags", "")
                val folderName = json.optString("folderName", "").trim()

                // Resolve or create folder if needed
                var folderId: Long = -1L
                if (folderName.isNotEmpty()) {
                    val existingFolder = database.folderDao().getFolderByName(folderName)
                    folderId = existingFolder?.id ?: run {
                        val newFolder = Folder(name = folderName, createdTime = createdTime)
                        database.folderDao().insertFolder(newFolder)
                    }
                }

                val pagesArray = json.optJSONArray("pages")
                val pageCount = pagesArray?.length() ?: 0

                val doc = Document(
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
                        val procName = pJson.optString("processedFileName", "")
                        val origName = pJson.optString("originalFileName", "")
                        val thumbName = pJson.optString("thumbnailFileName", "")

                        val procFile = File(scansDir, procName)
                        val origFile = File(scansDir, origName)
                        val thumbFile = File(thumbsDir, thumbName)

                        val page = Page(
                            documentId = newDocId,
                            pageIndex = pageIndex,
                            processedImagePath = if (procFile.exists()) procFile.absolutePath else "",
                            originalImagePath = if (origFile.exists()) origFile.absolutePath else "",
                            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else "",
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
                restoredCount++
            } catch (_: Exception) {
                // Continue with remaining files
            }
        }
        restoredCount
    }
}

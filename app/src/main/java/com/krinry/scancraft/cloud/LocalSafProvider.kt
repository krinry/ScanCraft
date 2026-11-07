package com.krinry.scancraft.cloud

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Storage Access Framework (SAF) local & SD card backup provider.
 */
class LocalSafProvider(private val context: Context) : CloudProvider {

    override val id: String = "local_saf"
    override val displayName: String = "Local / SD Card Backup"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_TREE_URI = "saf_tree_uri"
        private const val PREF_TREE_NAME = "saf_tree_name"
    }

    suspend fun saveFolderUri(uri: Uri, folderName: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_TREE_URI, uri.toString())
            .putString(PREF_TREE_NAME, folderName)
            .putBoolean("sync_local", true)
            .apply()
    }

    fun getSavedTreeUri(): Uri? {
        val str = prefs.getString(PREF_TREE_URI, null) ?: return null
        return Uri.parse(str)
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val uriStr = prefs.getString(PREF_TREE_URI, null)
        val enabled = prefs.getBoolean("sync_local", true)
        !uriStr.isNullOrBlank() && enabled
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(PREF_TREE_URI)
            .remove(PREF_TREE_NAME)
            .putBoolean("sync_local", false)
            .apply()
    }

    override suspend fun getAccountIdentifier(): String = withContext(Dispatchers.IO) {
        prefs.getString(PREF_TREE_NAME, "ScanCraft Backup Folder") ?: "ScanCraft Backup Folder"
    }

    override suspend fun getQuota(): CloudQuota? = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free
            CloudQuota(usedBytes = used, totalBytes = total)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val uriStr = prefs.getString(PREF_TREE_URI, null)
            ?: return@withContext Result.failure(IllegalStateException("No backup folder selected"))

        try {
            val treeUri = Uri.parse(uriStr)
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot access folder"))

            // Find or create existing file
            val existing = root.findFile(file.name)
            existing?.delete()

            val doc = root.createFile(mimeType, file.name)
                ?: return@withContext Result.failure(IllegalStateException("Failed to create document file"))

            context.contentResolver.openOutputStream(doc.uri)?.use { outStream ->
                FileInputStream(file).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            Result.success(doc.uri.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        val uriStr = prefs.getString(PREF_TREE_URI, null)
            ?: return@withContext SyncResult.Error("No backup folder selected")

        try {
            val treeUri = Uri.parse(uriStr)
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext SyncResult.Error("Cannot access backup folder")

            var count = 0
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val existing = root.findFile(file.name)
                existing?.delete()

                val doc = root.createFile(mime, file.name) ?: continue
                context.contentResolver.openOutputStream(doc.uri)?.use { outStream ->
                    FileInputStream(file).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                count++
            }
            SyncResult.Success(count, "Backed up $count files to local storage")
        } catch (e: Exception) {
            SyncResult.Error("Local backup error: ${e.localizedMessage}", e)
        }
    }
}

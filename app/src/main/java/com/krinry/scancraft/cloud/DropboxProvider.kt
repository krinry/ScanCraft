package com.krinry.scancraft.cloud

import android.content.Context
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Dropbox synchronization provider powered by official Dropbox Core SDK.
 */
class DropboxProvider(private val context: Context) : CloudProvider {

    override val id: String = "dropbox"
    override val displayName: String = "Dropbox"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_TOKEN = "dropbox_access_token"
        private const val PREF_ACCOUNT = "dropbox_account_email"
    }

    suspend fun saveAccessToken(token: String, accountName: String? = null) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_TOKEN, token)
            .putString(PREF_ACCOUNT, accountName ?: "Connected Account")
            .putBoolean("sync_dropbox", true)
            .apply()
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val token = prefs.getString(PREF_TOKEN, null)
        val enabled = prefs.getBoolean("sync_dropbox", false)
        !token.isNullOrBlank() && enabled
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(PREF_TOKEN)
            .remove(PREF_ACCOUNT)
            .putBoolean("sync_dropbox", false)
            .apply()
    }

    override suspend fun getAccountIdentifier(): String = withContext(Dispatchers.IO) {
        prefs.getString(PREF_ACCOUNT, "Dropbox Account") ?: "Dropbox Account"
    }

    override suspend fun getQuota(): CloudQuota? = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext null
        try {
            val space = client.users().spaceUsage
            val used = space.used
            val total = space.allocation.individualValue?.allocated ?: (2L * 1024 * 1024 * 1024)
            CloudQuota(usedBytes = used, totalBytes = total)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext Result.failure(IllegalStateException("Dropbox is not authenticated"))
        try {
            val remotePath = "/ScanCraft/${file.name}"
            FileInputStream(file).use { inStream ->
                val metadata = client.files().uploadBuilder(remotePath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inStream)
                Result.success(metadata.pathDisplay ?: file.name)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext SyncResult.Error("Dropbox is not authenticated")
        var count = 0
        try {
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val remotePath = "/ScanCraft/${file.name}"
                FileInputStream(file).use { inStream ->
                    client.files().uploadBuilder(remotePath)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inStream)
                    count++
                }
            }
            SyncResult.Success(count, "Uploaded $count files to Dropbox /ScanCraft")
        } catch (e: Exception) {
            SyncResult.Error("Dropbox sync failed: ${e.localizedMessage}", e)
        }
    }

    private fun getClient(): DbxClientV2? {
        val token = prefs.getString(PREF_TOKEN, null) ?: return null
        val config = DbxRequestConfig.newBuilder("ScanCraft/1.0").build()
        return DbxClientV2(config, token)
    }
}

package com.krinry.scancraft.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lightweight Dropbox synchronization provider powered by direct Dropbox REST API v2.
 * Eliminates the heavy official Dropbox Core SDK and Jackson dependencies.
 */
class DropboxProvider(private val context: Context) : CloudProvider {

    override val id: String = "dropbox"
    override val displayName: String = "Dropbox"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val PREF_TOKEN = "dropbox_access_token"
        private const val PREF_ACCOUNT = "dropbox_account_email"

        private const val URL_SPACE_USAGE = "https://api.dropboxapi.com/2/users/get_space_usage"
        private const val URL_FILE_UPLOAD = "https://content.dropboxapi.com/2/files/upload"
    }

    suspend fun saveAccessToken(token: String, accountName: String? = null) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_TOKEN, token.trim())
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
        val token = prefs.getString(PREF_TOKEN, null) ?: return@withContext null
        try {
            val request = Request.Builder()
                .url(URL_SPACE_USAGE)
                .header("Authorization", "Bearer $token")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val jsonBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(jsonBody)

            val used = json.optLong("used", 0L)
            val allocation = json.optJSONObject("allocation")
            val allocated = allocation?.optLong("allocated") ?: (2L * 1024 * 1024 * 1024)

            CloudQuota(usedBytes = used, totalBytes = allocated)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val token = prefs.getString(PREF_TOKEN, null)
            ?: return@withContext Result.failure(IllegalStateException("Dropbox is not authenticated"))

        try {
            val remotePath = "/ScanCraft/${file.name}"
            val argJson = JSONObject().apply {
                put("path", remotePath)
                put("mode", "overwrite")
                put("autorename", false)
                put("mute", false)
            }.toString()

            val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(URL_FILE_UPLOAD)
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", argJson)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: "HTTP ${response.code}"
                return@withContext Result.failure(IllegalStateException("Dropbox upload failed: $errorMsg"))
            }

            val jsonBody = response.body?.string()
            val displayPath = if (!jsonBody.isNullOrBlank()) {
                JSONObject(jsonBody).optString("path_display", remotePath)
            } else {
                remotePath
            }

            Result.success(displayPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext SyncResult.Error("Dropbox is not authenticated")
        }

        var count = 0
        try {
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val res = uploadFile(file, if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg")
                if (res.isSuccess) {
                    count++
                }
            }
            SyncResult.Success(count, "Uploaded $count files to Dropbox /ScanCraft")
        } catch (e: Exception) {
            SyncResult.Error("Dropbox sync failed: ${e.localizedMessage}", e)
        }
    }
}

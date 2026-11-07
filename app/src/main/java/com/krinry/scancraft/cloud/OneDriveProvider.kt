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
 * Microsoft OneDrive cloud provider communicating directly via Microsoft Graph REST API.
 */
class OneDriveProvider(private val context: Context) : CloudProvider {

    override val id: String = "onedrive"
    override val displayName: String = "Microsoft OneDrive"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_TOKEN = "onedrive_access_token"
        private const val PREF_ACCOUNT = "onedrive_account_name"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun saveAuth(token: String, accountName: String? = null) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_TOKEN, token.trim())
            .putString(PREF_ACCOUNT, accountName?.trim() ?: "OneDrive User")
            .putBoolean("sync_onedrive", true)
            .apply()
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val token = prefs.getString(PREF_TOKEN, null)
        val enabled = prefs.getBoolean("sync_onedrive", false)
        !token.isNullOrBlank() && enabled
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(PREF_TOKEN)
            .remove(PREF_ACCOUNT)
            .putBoolean("sync_onedrive", false)
            .apply()
    }

    override suspend fun getAccountIdentifier(): String = withContext(Dispatchers.IO) {
        prefs.getString(PREF_ACCOUNT, "OneDrive Account") ?: "OneDrive Account"
    }

    override suspend fun getQuota(): CloudQuota? = withContext(Dispatchers.IO) {
        val token = prefs.getString(PREF_TOKEN, null) ?: return@withContext null
        try {
            val req = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val quota = json.optJSONObject("quota") ?: return@withContext null
                val used = quota.optLong("used", 0L)
                val total = quota.optLong("total", 5L * 1024 * 1024 * 1024)
                CloudQuota(usedBytes = used, totalBytes = total)
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val token = prefs.getString(PREF_TOKEN, null)
            ?: return@withContext Result.failure(IllegalStateException("OneDrive is not authenticated"))

        try {
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/ScanCraft/${file.name}:/content"
            val body = file.asRequestBody(mimeType.toMediaType())
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(body)
                .build()

            okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(file.name)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        val token = prefs.getString(PREF_TOKEN, null)
            ?: return@withContext SyncResult.Error("OneDrive is not authenticated")

        var count = 0
        try {
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val url = "https://graph.microsoft.com/v1.0/me/drive/root:/ScanCraft/${file.name}:/content"
                val body = file.asRequestBody(mime.toMediaType())
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .put(body)
                    .build()

                okHttpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        count++
                    }
                }
            }
            SyncResult.Success(count, "Uploaded $count files to OneDrive /ScanCraft")
        } catch (e: Exception) {
            SyncResult.Error("OneDrive sync failed: ${e.localizedMessage}", e)
        }
    }
}

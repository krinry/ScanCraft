package com.krinry.scancraft.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import com.krinry.scancraft.data.security.CryptoHelper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Standard WebDAV / Nextcloud synchronization client.
 */
class WebDavProvider(private val context: Context) : CloudProvider {

    override val id: String = "webdav"
    override val displayName: String = "WebDAV / Nextcloud"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_URL = "webdav_server_url"
        private const val PREF_USER = "webdav_username"
        private const val PREF_PASS = "webdav_password"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun getStoredPassword(): String? {
        val raw = prefs.getString(PREF_PASS, null) ?: return null
        return try {
            CryptoHelper.decrypt(raw)
        } catch (e: Exception) {
            // Graceful fallback for migration of previously stored plaintext passwords
            raw
        }
    }

    suspend fun saveCredentials(serverUrl: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val cleanUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val securePass = try {
            CryptoHelper.encrypt(password)
        } catch (e: Exception) {
            e.printStackTrace()
            password
        }
        prefs.edit()
            .putString(PREF_URL, cleanUrl)
            .putString(PREF_USER, username.trim())
            .putString(PREF_PASS, securePass)
            .putBoolean("sync_webdav", true)
            .apply()
    }

    suspend fun testConnection(serverUrl: String, username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val credential = Credentials.basic(username, password)
            val req = Request.Builder()
                .url(cleanUrl)
                .header("Authorization", credential)
                .header("Depth", "0")
                .method("PROPFIND", null)
                .build()

            okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success("Connection Successful (${response.code})")
                } else {
                    Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val url = prefs.getString(PREF_URL, null)
        val user = prefs.getString(PREF_USER, null)
        val enabled = prefs.getBoolean("sync_webdav", false)
        !url.isNullOrBlank() && !user.isNullOrBlank() && enabled
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(PREF_URL)
            .remove(PREF_USER)
            .remove(PREF_PASS)
            .putBoolean("sync_webdav", false)
            .apply()
    }

    override suspend fun getAccountIdentifier(): String = withContext(Dispatchers.IO) {
        val url = prefs.getString(PREF_URL, "") ?: ""
        val user = prefs.getString(PREF_USER, "") ?: ""
        if (url.isNotBlank() && user.isNotBlank()) {
            val host = try { java.net.URI(url).host } catch (e: Exception) { url }
            "$user@$host"
        } else {
            "Not connected"
        }
    }

    override suspend fun getQuota(): CloudQuota? = withContext(Dispatchers.IO) {
        // Return null or estimated quota
        null
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val url = prefs.getString(PREF_URL, null) ?: return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        val user = prefs.getString(PREF_USER, null) ?: return@withContext Result.failure(IllegalStateException("WebDAV username missing"))
        val pass = getStoredPassword() ?: return@withContext Result.failure(IllegalStateException("WebDAV password missing"))

        try {
            val targetUrl = "$url${file.name}"
            val credential = Credentials.basic(user, pass)
            val body = file.asRequestBody(mimeType.toMediaType())
            val req = Request.Builder()
                .url(targetUrl)
                .header("Authorization", credential)
                .put(body)
                .build()

            okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful || response.code in 200..204) {
                    Result.success(targetUrl)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext SyncResult.Error("WebDAV is not connected")
        val url = prefs.getString(PREF_URL, null) ?: return@withContext SyncResult.Error("No server URL")
        val user = prefs.getString(PREF_USER, null) ?: return@withContext SyncResult.Error("No username")
        val pass = getStoredPassword() ?: return@withContext SyncResult.Error("No password")

        var count = 0
        try {
            val credential = Credentials.basic(user, pass)
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val targetUrl = "$url${file.name}"
                val body = file.asRequestBody(mime.toMediaType())
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("Authorization", credential)
                    .put(body)
                    .build()

                okHttpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful || response.code in 200..204) {
                        count++
                    }
                }
            }
            SyncResult.Success(count, "Uploaded $count files to WebDAV")
        } catch (e: Exception) {
            SyncResult.Error("WebDAV sync error: ${e.localizedMessage}", e)
        }
    }
}

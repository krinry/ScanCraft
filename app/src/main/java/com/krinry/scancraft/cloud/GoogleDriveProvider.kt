package com.krinry.scancraft.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * Lightweight Google Drive synchronization provider supporting:
 * 1. Direct Google Drive Storage Access Framework (SAF) folder mounting (offline, private, zero-config)
 * 2. Native Google Sign-In & direct Google Drive REST API v3 over OkHttp
 * Completely eliminates heavy legacy Google API Client and Drive Services SDK dependencies.
 */
class GoogleDriveProvider(private val context: Context) : CloudProvider {

    override val id: String = "gdrive"
    override val displayName: String = "Google Drive"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        const val PREF_ACCOUNT = "gdrive_account_email"
        const val PREF_SAF_URI = "gdrive_saf_folder_uri"
        const val PREF_SAF_NAME = "gdrive_saf_folder_name"
        const val PREF_WEB_CLIENT_ID = "gdrive_web_client_id"
        const val PREF_AUTH_TOKEN = "gdrive_auth_token"

        const val APP_PACKAGE_NAME = "com.krinry.scancraft"
        const val DEBUG_SHA1 = "9C:40:44:66:6F:73:1C:9E:2D:D3:86:F1:5B:04:48:A8:0A:08:7A:35"
        const val DEBUG_SHA256 = "72:3B:83:B3:A8:4A:B3:44:B9:12:08:1F:FA:C7:4A:DF:2C:71:AB:A1:E2:A3:9C:D2:B8:CD:06:55:D3:9F:BA:3B"

        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

        private const val URL_DRIVE_ABOUT = "https://www.googleapis.com/drive/v3/about?fields=storageQuota"
        private const val URL_DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val URL_DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
    }

    fun getSignInIntent(webClientId: String? = null): Intent {
        val clientId = webClientId ?: prefs.getString(PREF_WEB_CLIENT_ID, null)
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE), Scope(DRIVE_APPDATA_SCOPE))

        if (!clientId.isNullOrBlank()) {
            builder.requestIdToken(clientId.trim())
        }

        val client = GoogleSignIn.getClient(context, builder.build())
        return client.signInIntent
    }

    suspend fun onAccountSignedIn(account: GoogleSignInAccount) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_ACCOUNT, account.email ?: "Google Drive User")
            .putBoolean("sync_gdrive", true)
            .apply()
    }

    suspend fun saveSafDriveFolder(uri: Uri, folderName: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_SAF_URI, uri.toString())
            .putString(PREF_SAF_NAME, folderName)
            .putBoolean("sync_gdrive", true)
            .apply()
    }

    suspend fun saveWebClientId(clientId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_WEB_CLIENT_ID, clientId.trim())
            .apply()
    }

    suspend fun saveAuthToken(token: String, accountEmail: String? = null) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(PREF_AUTH_TOKEN, token.trim())
            .putString(PREF_ACCOUNT, accountEmail?.trim() ?: "Google Drive (OAuth Token)")
            .putBoolean("sync_gdrive", true)
            .apply()
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        val prefEnabled = prefs.getBoolean("sync_gdrive", false)
        if (!prefEnabled) return@withContext false

        val account = GoogleSignIn.getLastSignedInAccount(context)
        val hasScope = account != null && GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))
        val hasEmail = prefs.getString(PREF_ACCOUNT, null) != null
        val hasSaf = prefs.getString(PREF_SAF_URI, null) != null
        val hasToken = prefs.getString(PREF_AUTH_TOKEN, null) != null

        (account != null && hasScope) || hasEmail || hasSaf || hasToken
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, builder).signOut()
        } catch (_: Exception) {}
        prefs.edit()
            .remove(PREF_ACCOUNT)
            .remove(PREF_SAF_URI)
            .remove(PREF_SAF_NAME)
            .remove(PREF_AUTH_TOKEN)
            .putBoolean("sync_gdrive", false)
            .apply()
    }

    override suspend fun getAccountIdentifier(): String = withContext(Dispatchers.IO) {
        val safName = prefs.getString(PREF_SAF_NAME, null)
        if (!safName.isNullOrBlank()) {
            return@withContext "Google Drive ($safName)"
        }
        val account = GoogleSignIn.getLastSignedInAccount(context)
        account?.email ?: prefs.getString(PREF_ACCOUNT, "Google Drive") ?: "Google Drive"
    }

    override suspend fun getQuota(): CloudQuota? = withContext(Dispatchers.IO) {
        val token = getOAuthToken() ?: return@withContext null
        try {
            val request = Request.Builder()
                .url(URL_DRIVE_ABOUT)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val jsonBody = response.body?.string() ?: return@withContext null
            val quotaJson = JSONObject(jsonBody).optJSONObject("storageQuota") ?: return@withContext null

            val used = quotaJson.optLong("usage", 0L)
            val limit = quotaJson.optLong("limit", 15L * 1024 * 1024 * 1024)
            CloudQuota(usedBytes = used, totalBytes = limit)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        // 1. Primary: SAF Google Drive folder if configured
        val safUriStr = prefs.getString(PREF_SAF_URI, null)
        if (!safUriStr.isNullOrBlank()) {
            return@withContext uploadViaSaf(file, mimeType, Uri.parse(safUriStr))
        }

        // 2. Direct REST Upload via OAuth token
        val token = getOAuthToken()
            ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated. Please select a Drive folder or enter Auth Token."))

        try {
            val folderId = getOrCreateScanCraftFolder(token)

            val metadataJson = JSONObject().apply {
                put("name", file.name)
                if (!folderId.isNullOrBlank()) {
                    put("parents", org.json.JSONArray().put(folderId))
                }
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    file.asRequestBody(mimeType.toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(URL_DRIVE_UPLOAD)
                .header("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                return@withContext Result.failure(IllegalStateException("Drive upload failed: $err"))
            }

            val body = response.body?.string()
            val fileId = if (!body.isNullOrBlank()) JSONObject(body).optString("id", file.name) else file.name
            Result.success(fileId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext SyncResult.Error("Google Drive is not connected")
        }

        val safUriStr = prefs.getString(PREF_SAF_URI, null)
        if (!safUriStr.isNullOrBlank()) {
            return@withContext syncViaSaf(files, Uri.parse(safUriStr))
        }

        var uploadedCount = 0
        try {
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val res = uploadFile(file, mime)
                if (res.isSuccess) {
                    uploadedCount++
                }
            }
            SyncResult.Success(uploadedCount, "Successfully uploaded $uploadedCount files to Google Drive/ScanCraft")
        } catch (e: Exception) {
            SyncResult.Error("Google Drive sync failed: ${e.localizedMessage ?: "Unknown error"}", e)
        }
    }

    private fun uploadViaSaf(file: File, mimeType: String, treeUri: Uri): Result<String> {
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return Result.failure(IllegalStateException("Cannot access Google Drive folder"))

            root.findFile(file.name)?.delete()
            val doc = root.createFile(mimeType, file.name)
                ?: return Result.failure(IllegalStateException("Failed to create file in Google Drive"))

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

    private fun syncViaSaf(files: List<File>, treeUri: Uri): SyncResult {
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return SyncResult.Error("Cannot access Google Drive folder")

            var count = 0
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                root.findFile(file.name)?.delete()
                val doc = root.createFile(mime, file.name) ?: continue
                context.contentResolver.openOutputStream(doc.uri)?.use { outStream ->
                    FileInputStream(file).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                count++
            }
            SyncResult.Success(count, "Uploaded $count files to Google Drive folder")
        } catch (e: Exception) {
            SyncResult.Error("Google Drive sync error: ${e.localizedMessage}", e)
        }
    }

    private fun getOAuthToken(): String? {
        val manualToken = prefs.getString(PREF_AUTH_TOKEN, null)
        if (!manualToken.isNullOrBlank()) return manualToken

        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.idToken
    }

    private fun getOrCreateScanCraftFolder(token: String): String? {
        return try {
            val query = "mimeType='application/vnd.google-apps.folder' and name='ScanCraft' and trashed=false"
            val url = "$URL_DRIVE_FILES?q=${Uri.encode(query)}&spaces=drive&fields=files(id,name)"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val filesArray = JSONObject(body).optJSONArray("files")
                    if (filesArray != null && filesArray.length() > 0) {
                        return filesArray.getJSONObject(0).optString("id")
                    }
                }
            }

            // Create folder
            val folderMetadata = JSONObject().apply {
                put("name", "ScanCraft")
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString()

            val createReq = Request.Builder()
                .url(URL_DRIVE_FILES)
                .header("Authorization", "Bearer $token")
                .post(folderMetadata.toRequestBody("application/json".toMediaType()))
                .build()

            val createResp = httpClient.newCall(createReq).execute()
            if (createResp.isSuccessful) {
                val createBody = createResp.body?.string()
                if (!createBody.isNullOrBlank()) {
                    return JSONObject(createBody).optString("id")
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

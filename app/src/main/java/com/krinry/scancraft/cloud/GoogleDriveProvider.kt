package com.krinry.scancraft.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Collections

/**
 * Google Drive synchronization provider supporting:
 * 1. Native Google Play Services Auth (GoogleSignInClient) & Google Drive REST API v3
 * 2. Direct Google Drive Storage Access Framework (SAF) folder mounting (no Google Cloud Console setup required)
 * 3. Custom Google OAuth 2.0 Web Client ID / Token integration
 */
class GoogleDriveProvider(private val context: Context) : CloudProvider {

    override val id: String = "gdrive"
    override val displayName: String = "Google Drive"

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    companion object {
        const val PREF_ACCOUNT = "gdrive_account_email"
        const val PREF_SAF_URI = "gdrive_saf_folder_uri"
        const val PREF_SAF_NAME = "gdrive_saf_folder_name"
        const val PREF_WEB_CLIENT_ID = "gdrive_web_client_id"
        const val PREF_AUTH_TOKEN = "gdrive_auth_token"

        const val APP_PACKAGE_NAME = "com.krinry.scancraft"
        const val DEBUG_SHA1 = "9C:40:44:66:6F:73:1C:9E:2D:D3:86:F1:5B:04:48:A8:0A:08:7A:35"
        const val DEBUG_SHA256 = "72:3B:83:B3:A8:4A:B3:44:B9:12:08:1F:FA:C7:4A:DF:2C:71:AB:A1:E2:A3:9C:D2:B8:CD:06:55:D3:9F:BA:3B"
    }

    fun getSignInIntent(webClientId: String? = null): Intent {
        val clientId = webClientId ?: prefs.getString(PREF_WEB_CLIENT_ID, null)
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))

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
        val hasScope = account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
        val hasEmail = prefs.getString(PREF_ACCOUNT, null) != null
        val hasSaf = prefs.getString(PREF_SAF_URI, null) != null
        val hasToken = prefs.getString(PREF_AUTH_TOKEN, null) != null

        (account != null && hasScope) || hasEmail || hasSaf || hasToken
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, builder).signOut()
        } catch (e: Exception) {
            // Ignore sign out error
        }
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
        try {
            val drive = getDriveService() ?: return@withContext null
            val about = drive.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota ?: return@withContext null
            val used = quota.usage ?: 0L
            val limit = quota.limit ?: (15L * 1024 * 1024 * 1024)
            CloudQuota(usedBytes = used, totalBytes = limit)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        // 1. Try via SAF Google Drive folder if configured
        val safUriStr = prefs.getString(PREF_SAF_URI, null)
        if (!safUriStr.isNullOrBlank()) {
            return@withContext uploadViaSaf(file, mimeType, Uri.parse(safUriStr))
        }

        // 2. Try via Google Drive API SDK
        try {
            val drive = getDriveService()
                ?: return@withContext Result.failure(IllegalStateException("Google Drive is not authenticated"))

            val folderId = getOrCreateScanCraftFolder(drive)
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = file.name
                parents = Collections.singletonList(folderId)
            }
            val mediaContent = FileContent(mimeType, file)
            val uploaded = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute()

            Result.success(uploaded.id ?: file.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncScans(files: List<File>): SyncResult = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext SyncResult.Error("Google Drive is not connected")
        }

        // 1. If connected via SAF Google Drive folder
        val safUriStr = prefs.getString(PREF_SAF_URI, null)
        if (!safUriStr.isNullOrBlank()) {
            return@withContext syncViaSaf(files, Uri.parse(safUriStr))
        }

        // 2. If connected via Drive REST API SDK
        val drive = getDriveService()
            ?: return@withContext SyncResult.Error("Google Drive service unavailable")

        var uploadedCount = 0
        try {
            val folderId = getOrCreateScanCraftFolder(drive)
            for (file in files) {
                if (!file.exists() || file.length() == 0L) continue
                val mime = if (file.name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = file.name
                    parents = Collections.singletonList(folderId)
                }
                val mediaContent = FileContent(mime, file)
                drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
                uploadedCount++
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

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("ScanCraft").build()
    }

    private fun getOrCreateScanCraftFolder(drive: Drive): String {
        val query = "mimeType='application/vnd.google-apps.folder' and name='ScanCraft' and trashed=false"
        val result = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id, name)").execute()
        val existing = result.files?.firstOrNull()
        if (existing != null) {
            return existing.id
        }

        val folderMetadata = com.google.api.services.drive.model.File().apply {
            name = "ScanCraft"
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = drive.files().create(folderMetadata).setFields("id").execute()
        return folder.id
    }
}

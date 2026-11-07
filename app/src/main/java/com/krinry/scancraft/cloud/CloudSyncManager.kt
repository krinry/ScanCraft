package com.krinry.scancraft.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val currentProvider: String, val progressMessage: String) : SyncState()
    data class Completed(val message: String, val successCount: Int) : SyncState()
    data class Failed(val error: String) : SyncState()
}

/**
 * Orchestrates multi-cloud auto-sync and manual "Sync Now" across all connected providers.
 */
class CloudSyncManager(private val context: Context) {

    val googleDriveProvider = GoogleDriveProvider(context)
    val dropboxProvider = DropboxProvider(context)
    val oneDriveProvider = OneDriveProvider(context)
    val webDavProvider = WebDavProvider(context)
    val localSafProvider = LocalSafProvider(context)

    val allProviders: List<CloudProvider> = listOf(
        googleDriveProvider,
        dropboxProvider,
        oneDriveProvider,
        webDavProvider,
        localSafProvider
    )

    private val prefs = context.getSharedPreferences("scancraft_cloud_sync", Context.MODE_PRIVATE)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(
        prefs.getString("last_sync_time", "Never synced") ?: "Never synced"
    )
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    fun isAutoSyncEnabled(): Boolean = prefs.getBoolean("auto_sync_enabled", true)
    fun setAutoSyncEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_sync_enabled", enabled).apply()

    fun isWifiOnly(): Boolean = prefs.getBoolean("sync_wifi_only", true)
    fun setWifiOnly(wifiOnly: Boolean) = prefs.edit().putBoolean("sync_wifi_only", wifiOnly).apply()

    fun isSyncPdfCopies(): Boolean = prefs.getBoolean("sync_pdf_copies", true)
    fun setSyncPdfCopies(syncPdf: Boolean) = prefs.edit().putBoolean("sync_pdf_copies", syncPdf).apply()

    /**
     * Executes real synchronization to all connected cloud & local providers.
     */
    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        val connectedProviders = allProviders.filter { it.isConnected() }

        if (connectedProviders.isEmpty()) {
            _syncState.value = SyncState.Failed("No cloud or local backup providers connected.")
            return@withContext SyncResult.Error("No providers connected. Please connect Google Drive, Dropbox, OneDrive, WebDAV, or Local Folder.")
        }

        _syncState.value = SyncState.Syncing("Preparing", "Collecting scan files and creating backup package...")

        // Gather all files from scans directory
        val scansDir = File(context.filesDir, "scans")
        val scanFiles = scansDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()

        // Also create a compressed full backup package (database + scans)
        val backupZip = createDatabaseAndScansZipArchive()

        val filesToUpload = mutableListOf<File>()
        filesToUpload.addAll(scanFiles)
        if (backupZip != null && backupZip.exists()) {
            filesToUpload.add(0, backupZip)
        }

        var totalSuccessful = 0
        val results = mutableListOf<String>()

        for (provider in connectedProviders) {
            _syncState.value = SyncState.Syncing(provider.displayName, "Uploading to ${provider.displayName}...")
            val result = provider.syncScans(filesToUpload)
            when (result) {
                is SyncResult.Success -> {
                    totalSuccessful++
                    results.add("${provider.displayName}: ${result.message}")
                }
                is SyncResult.Error -> {
                    results.add("${provider.displayName} Error: ${result.message}")
                }
            }
        }

        val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
        prefs.edit().putString("last_sync_time", timestamp).apply()
        _lastSyncTime.value = timestamp

        val summary = if (totalSuccessful > 0) {
            "Synced ${filesToUpload.size} files to $totalSuccessful provider(s)."
        } else {
            "Sync encountered errors. Check provider configurations."
        }

        _syncState.value = SyncState.Completed(summary, totalSuccessful)
        SyncResult.Success(totalSuccessful, summary)
    }

    /**
     * Packages the Room database and all scan images into a clean .zip archive.
     */
    private fun createDatabaseAndScansZipArchive(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(context.cacheDir, "ScanCraft_Backup_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 1. Add Room SQLite Database file if present
                val dbFile = context.getDatabasePath("scanner_database")
                if (dbFile.exists()) {
                    zos.putNextEntry(ZipEntry("scanner_database.sqlite"))
                    FileInputStream(dbFile).use { it.copyTo(zos) }
                    zos.closeEntry()
                }

                // 2. Add scan files
                val scansDir = File(context.filesDir, "scans")
                scansDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        zos.putNextEntry(ZipEntry("scans/${file.name}"))
                        FileInputStream(file).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            zipFile
        } catch (e: Exception) {
            null
        }
    }
}

package com.krinry.scancraft.cloud

import java.io.File

/**
 * Result representing the outcome of a synchronization operation.
 */
sealed class SyncResult {
    data class Success(val uploadedCount: Int, val message: String) : SyncResult()
    data class Error(val message: String, val throwable: Throwable? = null) : SyncResult()
}

/**
 * Storage quota representation for cloud accounts.
 */
data class CloudQuota(
    val usedBytes: Long,
    val totalBytes: Long
) {
    val usedFormatted: String
        get() = formatBytes(usedBytes)

    val totalFormatted: String
        get() = formatBytes(totalBytes)

    val usagePercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}

/**
 * Common contract for all cloud synchronization providers (Google Drive, Dropbox, OneDrive, WebDAV, Local SAF).
 */
interface CloudProvider {
    val id: String
    val displayName: String

    suspend fun isConnected(): Boolean
    suspend fun disconnect()
    suspend fun getAccountIdentifier(): String
    suspend fun getQuota(): CloudQuota?
    suspend fun uploadFile(file: File, mimeType: String = "application/pdf"): Result<String>
    suspend fun syncScans(files: List<File>): SyncResult
}

package com.krinry.scancraft.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageStats(
    val scansBytes: Long,
    val thumbsBytes: Long,
    val cacheBytes: Long,
    val totalAppBytes: Long,
    val freeDeviceBytes: Long,
    val totalDeviceBytes: Long,
    val totalFilesCount: Int
) {
    val scansFormatted: String get() = formatBytes(scansBytes)
    val cacheFormatted: String get() = formatBytes(cacheBytes)
    val appUsedFormatted: String get() = formatBytes(totalAppBytes)
    val freeDeviceFormatted: String get() = formatBytes(freeDeviceBytes)
    val totalDeviceFormatted: String get() = formatBytes(totalDeviceBytes)

    val usageFraction: Float
        get() = if (totalDeviceBytes > 0) {
            (totalAppBytes.toFloat() / (1024f * 1024f * 1024f)).coerceIn(0.01f, 1f) // Relative to 1GB quota visualization or scale
        } else 0.05f

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

object StorageCalculator {

    suspend fun calculate(context: Context): StorageStats = withContext(Dispatchers.IO) {
        val scansDir = File(context.filesDir, "scans")
        val thumbsDir = File(context.filesDir, "thumbs")
        val cacheDir = context.cacheDir

        val scansBytes = calculateDirSize(scansDir)
        val thumbsBytes = calculateDirSize(thumbsDir)
        val cacheBytes = calculateDirSize(cacheDir)
        val totalAppBytes = scansBytes + thumbsBytes + cacheBytes

        val filesCount = (scansDir.listFiles()?.size ?: 0)

        val (freeDeviceBytes, totalDeviceBytes) = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            Pair(stat.availableBytes, stat.totalBytes)
        } catch (e: Exception) {
            Pair(10L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024)
        }

        StorageStats(
            scansBytes = scansBytes,
            thumbsBytes = thumbsBytes,
            cacheBytes = cacheBytes,
            totalAppBytes = totalAppBytes,
            freeDeviceBytes = freeDeviceBytes,
            totalDeviceBytes = totalDeviceBytes,
            totalFilesCount = filesCount
        )
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return size
    }
}

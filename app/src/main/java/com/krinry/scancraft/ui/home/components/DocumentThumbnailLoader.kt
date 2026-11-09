package com.krinry.scancraft.ui.home.components

import android.content.Context
import androidx.collection.LruCache
import com.krinry.scancraft.data.repository.StorageVault
import java.io.File

/**
 * Robust, zero-jank document thumbnail resolver supporting all storage variants:
 * - High-speed in-memory LruCache for 120Hz smooth scrolling without disk I/O churn
 * - Public StorageVault ("Documents/ScanCraft/thumbs")
 * - Fallback to internal storage ("filesDir/thumbs")
 * - Fallback to processed full scans: "page_<docId>_..." in scans/ folder
 */
object DocumentThumbnailLoader {

    private val thumbnailCache = LruCache<Long, File>(300)

    fun invalidateCache(docId: Long? = null) {
        if (docId != null) {
            thumbnailCache.remove(docId)
        } else {
            thumbnailCache.evictAll()
        }
    }

    fun findThumbnailFile(context: Context, docId: Long): File? {
        val cached = thumbnailCache.get(docId)
        if (cached != null && cached.exists() && cached.length() > 0) {
            return cached
        }

        // 1. Search thumbnail folders (Public Vault first, then app-internal sandbox)
        val candidateThumbDirs = listOf(
            StorageVault.getThumbsDirectory(context),
            File(context.filesDir, "thumbs")
        )

        for (thumbsDir in candidateThumbDirs) {
            if (thumbsDir.exists()) {
                val thumbFiles = thumbsDir.listFiles()
                if (!thumbFiles.isNullOrEmpty()) {
                    val match = thumbFiles.firstOrNull { file ->
                        val name = file.name
                        name.startsWith("thumb_${docId}_") ||
                        name.startsWith("thumb_page_${docId}_") ||
                        name.contains("_page_${docId}_")
                    }
                    if (match != null && match.exists() && match.length() > 0) {
                        thumbnailCache.put(docId, match)
                        return match
                    }
                }
            }
        }

        // 2. Fallback: Search scans directory for full page image (Coil downsamples on decode)
        val candidateScanDirs = listOf(
            StorageVault.getScansDirectory(context),
            File(context.filesDir, "scans")
        )

        for (scansDir in candidateScanDirs) {
            if (scansDir.exists()) {
                val scanFiles = scansDir.listFiles()
                if (!scanFiles.isNullOrEmpty()) {
                    val scanMatch = scanFiles.firstOrNull { file ->
                        val name = file.name
                        !name.startsWith("orig_") && (
                            name.startsWith("page_${docId}_") ||
                            name.startsWith("${docId}_") ||
                            name.contains("_page_${docId}_")
                        )
                    }
                    if (scanMatch != null && scanMatch.exists() && scanMatch.length() > 0) {
                        thumbnailCache.put(docId, scanMatch)
                        return scanMatch
                    }
                }
            }
        }

        return null
    }
}

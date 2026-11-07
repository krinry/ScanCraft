package com.krinry.scancraft.ui.home.components

import android.content.Context
import java.io.File

/**
 * Robust document thumbnail resolver supporting all storage variants:
 * - Direct document thumbnail: "thumb_<docId>_<timestamp>.jpg"
 * - Page-based thumbnail: "thumb_page_<docId>_<timestamp>.jpg"
 * - Substring pattern: "_page_<docId>_"
 * - Fallback to processed full scans: "page_<docId>_..." in scans/ folder
 */
object DocumentThumbnailLoader {

    fun findThumbnailFile(context: Context, docId: Long): File? {
        val thumbsDir = File(context.filesDir, "thumbs")
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
                    return match
                }
            }
        }

        // Fallback: Check scans directory for full page image (Coil will downsample on decode)
        val scansDir = File(context.filesDir, "scans")
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
                    return scanMatch
                }
            }
        }
        return null
    }
}

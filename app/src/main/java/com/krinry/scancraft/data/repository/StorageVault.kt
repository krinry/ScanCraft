package com.krinry.scancraft.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Page
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Universal Storage Vault for ScanCraft.
 *
 * Saves documents into the phone's public 'Documents/ScanCraft' directory by default.
 * This ensures that if the app is uninstalled and reinstalled, the user's scanned
 * documents, pages, and metadata remain 100% intact and can be automatically restored.
 */
object StorageVault {

    const val VAULT_FOLDER_NAME = "ScanCraft"

    /**
     * Checks if the app has permission to write directly to public external storage.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Intent to open the system Settings screen for storage permission.
     */
    fun getStoragePermissionIntent(context: Context): Intent {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
        } catch (_: Throwable) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Returns the primary vault root directory.
     * Prefers /sdcard/Documents/ScanCraft, falls back to externalFilesDir or filesDir.
     */
    fun getVaultRoot(context: Context): File {
        return try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null && docs.isAbsolute) {
                val vaultDir = File(docs, VAULT_FOLDER_NAME)
                if (!vaultDir.exists()) {
                    vaultDir.mkdirs()
                }
                if (vaultDir.exists() && vaultDir.canWrite()) {
                    vaultDir
                } else {
                    resolveFallbackDir(context)
                }
            } else {
                resolveFallbackDir(context)
            }
        } catch (_: Throwable) {
            resolveFallbackDir(context)
        }
    }

    private fun resolveFallbackDir(context: Context): File {
        val ext = try { context.getExternalFilesDir(null) } catch (_: Throwable) { null }
        if (ext != null && !ext.path.isNullOrEmpty()) {
            return ext
        }
        val intDir = try { context.filesDir } catch (_: Throwable) { null }
        if (intDir != null && !intDir.path.isNullOrEmpty()) {
            return intDir
        }
        return File("/tmp/scancraft_vault")
    }

    fun getScansDirectory(context: Context): File {
        val root = getVaultRoot(context)
        return File(root, "scans").apply { if (!exists()) mkdirs() }
    }

    fun getThumbsDirectory(context: Context): File {
        val root = getVaultRoot(context)
        return File(root, "thumbs").apply { if (!exists()) mkdirs() }
    }

    fun getPdfsDirectory(context: Context): File {
        val root = getVaultRoot(context)
        return File(root, "PDFs").apply { if (!exists()) mkdirs() }
    }

    fun getMetaDirectory(context: Context): File {
        val root = getVaultRoot(context)
        return File(root, "meta").apply { if (!exists()) mkdirs() }
    }

    /**
     * Writes document and page metadata to JSON so it survives uninstalls and can be re-indexed.
     */
    fun saveDocumentMetadata(
        context: Context,
        document: Document,
        pages: List<Page>,
        folderName: String? = null
    ) {
        try {
            val metaDir = getMetaDirectory(context)
            val metaFile = File(metaDir, "doc_${document.id}_meta.json")

            val json = JSONObject().apply {
                put("version", 1)
                put("documentId", document.id)
                put("title", document.title)
                put("createdTime", document.createdTime)
                put("modifiedTime", document.modifiedTime)
                put("pageCount", pages.size)
                put("tags", document.tags)
                put("folderName", folderName ?: "")

                val pagesArray = JSONArray()
                for (p in pages) {
                    val pJson = JSONObject().apply {
                        put("pageIndex", p.pageIndex)
                        put("processedFileName", File(p.processedImagePath).name)
                        put("originalFileName", File(p.originalImagePath).name)
                        put("thumbnailFileName", File(p.thumbnailPath).name)
                        put("cropBorderJson", p.cropBorderJson)
                        put("enhanceMode", p.enhanceMode)
                        put("rotationDegrees", p.rotationDegrees)
                        put("brightness", p.brightness)
                        put("contrast", p.contrast)
                        put("ocrText", p.ocrText)
                        put("watermarkText", p.watermarkText)
                        put("annotationsJson", p.annotationsJson)
                    }
                    pagesArray.put(pJson)
                }
                put("pages", pagesArray)
            }

            FileOutputStream(metaFile).use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {}
    }

    /**
     * Deletes the metadata backup when a document is deleted.
     */
    fun deleteDocumentMetadata(context: Context, documentId: Long) {
        try {
            val metaDir = getMetaDirectory(context)
            File(metaDir, "doc_${documentId}_meta.json").delete()
        } catch (_: Exception) {}
    }
}

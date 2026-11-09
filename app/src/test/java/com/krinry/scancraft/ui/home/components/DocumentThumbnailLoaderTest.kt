package com.krinry.scancraft.ui.home.components

import android.content.Context
import com.krinry.scancraft.data.repository.StorageVault
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocumentThumbnailLoaderTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var vault: File
    private lateinit var internal: File

    @Before
    fun setUp() {
        vault = temp.newFolder("external")
        internal = temp.newFolder("internal")
        context = mockk()
        every { context.filesDir } returns internal
        every { context.getExternalFilesDir(null) } returns vault
        DocumentThumbnailLoader.invalidateCache()
    }

    @After
    fun tearDown() {
        DocumentThumbnailLoader.invalidateCache()
    }

    private fun image(directory: File, name: String, content: String = "image"): File =
        File(directory.apply { mkdirs() }, name).apply { writeText(content) }

    @Test
    fun vaultThumbnailTakesPrecedenceOverInternalThumbnail() {
        val expected = image(StorageVault.getThumbsDirectory(context), "thumb_7_1.jpg")
        image(File(internal, "thumbs"), "thumb_7_2.jpg")
        assertEquals(expected, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
    }

    @Test
    fun internalThumbnailTakesPrecedenceOverVaultFullScan() {
        image(StorageVault.getScansDirectory(context), "page_7_1.jpg")
        val expected = image(File(internal, "thumbs"), "thumb_page_7_1.jpg")
        assertEquals(expected, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
    }

    @Test
    fun supportedThumbnailNamesResolveWithoutMatchingAnotherDocumentId() {
        for (name in listOf("thumb_7_1.jpg", "thumb_page_7_1.jpg", "custom_page_7_1.jpg")) {
            val file = image(StorageVault.getThumbsDirectory(context), name)
            image(StorageVault.getThumbsDirectory(context), "thumb_70_1.jpg")
            assertEquals(file, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
            file.delete()
            DocumentThumbnailLoader.invalidateCache()
        }
    }

    @Test
    fun scanFallbackSupportsLegacyNamesAndExcludesOriginals() {
        val scans = StorageVault.getScansDirectory(context)
        image(scans, "orig_page_7_1.jpg")
        image(scans, "page_70_1.jpg")
        assertNull(DocumentThumbnailLoader.findThumbnailFile(context, 7L))
        for (name in listOf("page_7_1.jpg", "7_1.jpg", "custom_page_7_1.jpg")) {
            val file = image(scans, name)
            assertEquals(file, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
            file.delete()
            DocumentThumbnailLoader.invalidateCache()
        }
    }

    @Test
    fun emptyVaultThumbnailFallsBackToInternalScan() {
        image(StorageVault.getThumbsDirectory(context), "thumb_7_1.jpg", "")
        val expected = image(File(internal, "scans"), "page_7_1.jpg")
        assertEquals(expected, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
    }

    @Test
    fun deletedCachedFileIsReplacedByInternalFallback() {
        val cached = image(StorageVault.getThumbsDirectory(context), "thumb_7_1.jpg")
        assertEquals(cached, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
        cached.delete()
        val replacement = image(File(internal, "thumbs"), "thumb_7_2.jpg")
        assertEquals(replacement, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
    }

    @Test
    fun truncatedCachedFileIsReplacedByScanFallback() {
        val cached = image(StorageVault.getThumbsDirectory(context), "thumb_7_1.jpg")
        assertEquals(cached, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
        cached.writeText("")
        val replacement = image(StorageVault.getScansDirectory(context), "page_7_2.jpg")
        assertEquals(replacement, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
    }

    @Test
    fun perDocumentInvalidationKeepsOtherCachedDocuments() {
        val old7 = image(File(internal, "thumbs"), "thumb_7_1.jpg")
        val old8 = image(File(internal, "thumbs"), "thumb_8_1.jpg")
        assertEquals(old7, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
        assertEquals(old8, DocumentThumbnailLoader.findThumbnailFile(context, 8L))
        val new7 = image(StorageVault.getThumbsDirectory(context), "thumb_7_2.jpg")
        image(StorageVault.getThumbsDirectory(context), "thumb_8_2.jpg")

        DocumentThumbnailLoader.invalidateCache(7L)

        assertEquals(new7, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
        assertEquals(old8, DocumentThumbnailLoader.findThumbnailFile(context, 8L))
    }

    @Test
    fun globalInvalidationRefreshesAllCachedDocuments() {
        for (id in listOf(7L, 8L)) {
            val old = image(File(internal, "thumbs"), "thumb_${id}_1.jpg")
            assertEquals(old, DocumentThumbnailLoader.findThumbnailFile(context, id))
            image(StorageVault.getThumbsDirectory(context), "thumb_${id}_2.jpg")
        }
        DocumentThumbnailLoader.invalidateCache()
        for (id in listOf(7L, 8L)) {
            assertEquals(File(vault, "thumbs/thumb_${id}_2.jpg"),
                DocumentThumbnailLoader.findThumbnailFile(context, id))
        }
    }

    @Test
    fun missingThumbnailIsNotNegativelyCached() {
        assertNull(DocumentThumbnailLoader.findThumbnailFile(context, 7L))
        val expected = image(StorageVault.getThumbsDirectory(context), "thumb_7_1.jpg")
        assertEquals(expected, DocumentThumbnailLoader.findThumbnailFile(context, 7L))
    }
}

package com.krinry.scancraft.data.repository

import android.content.Context
import com.krinry.scancraft.data.local.AppDatabase
import com.krinry.scancraft.data.local.DocumentDao
import com.krinry.scancraft.data.local.FolderDao
import com.krinry.scancraft.data.local.PageDao
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.ui.home.components.DocumentThumbnailLoader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageVaultRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempFolder.root
        every { context.getExternalFilesDir(null) } returns tempFolder.root

        database = mockk(relaxed = true)
        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)

        every { database.documentDao() } returns documentDao
        every { database.pageDao() } returns pageDao
        every { database.folderDao() } returns folderDao

        DocumentThumbnailLoader.invalidateCache()
    }

    @Test
    fun testSaveAndReadDocumentMetadata() {
        val root = StorageVault.getVaultRoot(context)
        val metaDir = StorageVault.getMetaDirectory(context)
        assertTrue(metaDir.exists())

        val doc = Document(
            id = 42L,
            title = "Test Passport",
            createdTime = 1700000000000L,
            modifiedTime = 1700000050000L,
            pageCount = 1,
            tags = "id,passport",
            folderId = -1L
        )

        val page = Page(
            id = 1L,
            documentId = 42L,
            pageIndex = 0,
            processedImagePath = "${root.absolutePath}/scans/page_42_1.jpg",
            originalImagePath = "${root.absolutePath}/scans/orig_42_1.jpg",
            thumbnailPath = "${root.absolutePath}/thumbs/thumb_42_1.jpg",
            enhanceMode = 1,
            brightness = 10,
            contrast = 5,
            ocrText = "PASSPORT REPUBLIC",
            watermarkText = "CONFIDENTIAL"
        )

        StorageVault.saveDocumentMetadata(context, doc, listOf(page), "Personal Docs")

        val metaFile = File(metaDir, "doc_42_meta.json")
        assertTrue(metaFile.exists())
        val content = metaFile.readText()
        assertTrue(content.contains("Test Passport"))
        assertTrue(content.contains("Personal Docs"))
        assertTrue(content.contains("PASSPORT REPUBLIC"))

        // Delete metadata
        StorageVault.deleteDocumentMetadata(context, 42L)
        assertFalse(metaFile.exists())
    }

    @Test
    fun testAutoRestoreFromVaultWhenDatabaseEmpty() = runTest {
        val scansDir = StorageVault.getScansDirectory(context)
        val thumbsDir = StorageVault.getThumbsDirectory(context)

        // Create dummy scanned image files
        val procFile = File(scansDir, "page_99_1.jpg").apply { writeText("dummy processed") }
        val origFile = File(scansDir, "orig_99_1.jpg").apply { writeText("dummy original") }
        val thumbFile = File(thumbsDir, "thumb_99_1.jpg").apply { writeText("dummy thumb") }

        val doc = Document(id = 99L, title = "Tax Statement 2025")
        val page = Page(
            id = 1L,
            documentId = 99L,
            pageIndex = 0,
            processedImagePath = procFile.absolutePath,
            originalImagePath = origFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath
        )

        StorageVault.saveDocumentMetadata(context, doc, listOf(page), "Taxes")

        // Setup mock DAOs: database currently empty
        coEvery { documentDao.getAllDocumentsList() } returns emptyList()
        coEvery { folderDao.getFolderByName("Taxes") } returns null
        coEvery { folderDao.insertFolder(any()) } returns 5L

        val docSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(docSlot)) } returns 99L

        val pageSlot = slot<Page>()
        coEvery { pageDao.insertPage(capture(pageSlot)) } returns 1L

        val restored = DocumentRecoveryManager.autoRestoreIfDatabaseEmpty(context, database)
        assertEquals(1, restored)

        assertTrue(docSlot.isCaptured)
        assertEquals("Tax Statement 2025", docSlot.captured.title)
        assertEquals(5L, docSlot.captured.folderId)

        assertTrue(pageSlot.isCaptured)
        assertEquals(99L, pageSlot.captured.documentId)
        assertEquals(procFile.absolutePath, pageSlot.captured.processedImagePath)
    }

    @Test
    fun testThumbnailLoaderCaching() {
        val thumbsDir = StorageVault.getThumbsDirectory(context)
        val sampleThumb = File(thumbsDir, "thumb_105_12345.jpg").apply { writeText("data") }

        // First lookup scans directory and populates LruCache
        val found = DocumentThumbnailLoader.findThumbnailFile(context, 105L)
        assertNotNull(found)
        assertEquals(sampleThumb.absolutePath, found?.absolutePath)

        // Second lookup retrieves from cache
        val fromCache = DocumentThumbnailLoader.findThumbnailFile(context, 105L)
        assertEquals(sampleThumb.absolutePath, fromCache?.absolutePath)

        // Invalidate cache
        DocumentThumbnailLoader.invalidateCache(105L)
    }
}

package com.krinry.scancraft.data.repository

import android.content.Context
import androidx.room.withTransaction
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
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.json.JSONObject
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
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionSlot = slot<suspend () -> Any?>()
        coEvery { any<AppDatabase>().withTransaction(capture(transactionSlot)) } coAnswers {
            transactionSlot.captured.invoke()
        }

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
        coEvery { documentDao.getDocumentById(any()) } returns null

        DocumentThumbnailLoader.invalidateCache()
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
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
    @Test
    fun populatedDatabaseDoesNotImportVaultDocuments() = runTest {
        StorageVault.saveDocumentMetadata(context, Document(id = 9L, title = "Backup"), emptyList())
        coEvery { documentDao.getAllDocumentsList() } returns listOf(Document(id = 1L, title = "Local"))

        assertEquals(0, DocumentRecoveryManager.autoRestoreIfDatabaseEmpty(context, database))

        coVerify(exactly = 0) { documentDao.insertDocument(any()) }
        coVerify(exactly = 0) { pageDao.insertPage(any()) }
        coVerify(exactly = 0) { folderDao.insertFolder(any()) }
    }

    @Test
    fun emptyVaultDoesNotWriteToDatabase() = runTest {
        assertEquals(0, DocumentRecoveryManager.scanAndRestoreFromVault(context, database))
        coVerify(exactly = 0) { documentDao.insertDocument(any()) }
    }

    @Test
    fun malformedMetadataDoesNotPreventValidDocumentRecovery() = runTest {
        val meta = StorageVault.getMetaDirectory(context)
        File(meta, "broken_meta.json").writeText("{invalid")
        File(meta, "ignored.json").writeText("""{"title":"Ignored"}""")
        File(meta, "directory_meta.json").mkdir()
        StorageVault.saveDocumentMetadata(context, Document(id = 8L, title = "Valid"), emptyList())

        assertEquals(1, DocumentRecoveryManager.scanAndRestoreFromVault(context, database))
        coVerify(exactly = 1) { documentDao.insertDocument(match { it.title == "Valid" && it.pageCount == 0 }) }
        coVerify(exactly = 0) { pageDao.insertPage(any()) }
    }

    @Test
    fun recoveryReusesExistingFolderAndRestoresEveryPageSetting() = runTest {
        val scans = StorageVault.getScansDirectory(context)
        val thumbs = StorageVault.getThumbsDirectory(context)
        val processed = File(scans, "processed.jpg").apply { writeText("processed") }
        val original = File(scans, "original.jpg").apply { writeText("original") }
        val thumbnail = File(thumbs, "thumbnail.jpg").apply { writeText("thumbnail") }
        val doc = Document(id = 12L, title = "Résumé 文書", createdTime = 123L,
            modifiedTime = 456L, tags = "tax,work")
        val page = Page(documentId = 12L, pageIndex = 3,
            processedImagePath = processed.path, originalImagePath = original.path,
            thumbnailPath = thumbnail.path, cropBorderJson = "[1,2,3,4]",
            enhanceMode = 2, rotationDegrees = 270, brightness = -20, contrast = 30,
            ocrText = "Line 1\n文書", watermarkText = "Draft",
            annotationsJson = """{"strokes":[]}""", createdTime = 123L)
        StorageVault.saveDocumentMetadata(context, doc, listOf(page), "  Work  ")
        coEvery { folderDao.getFolderByName("Work") } returns Folder(id = 7L, name = "Work")
        coEvery { documentDao.insertDocument(any()) } returns 500L
        val restoredPage = slot<Page>()
        coEvery { pageDao.insertPage(capture(restoredPage)) } returns 1L

        assertEquals(1, DocumentRecoveryManager.scanAndRestoreFromVault(context, database))

        coVerify(exactly = 0) { folderDao.insertFolder(any()) }
        coVerify(exactly = 1) {
            documentDao.insertDocument(match {
                it.id == doc.id && it.title == doc.title && it.createdTime == 123L &&
                    it.modifiedTime == 456L && it.tags == "tax,work" && it.folderId == 7L && it.pageCount == 1
            })
        }
        assertEquals(page.copy(documentId = 500L), restoredPage.captured)
    }

    @Test
    fun missingImageFilesRestoreAsEmptyPaths() = runTest {
        val page = Page(documentId = 2L, pageIndex = 0,
            processedImagePath = "/old/scans/missing.jpg",
            originalImagePath = "/old/scans/missing-original.jpg",
            thumbnailPath = "/old/thumbs/missing-thumb.jpg")
        StorageVault.saveDocumentMetadata(context, Document(id = 2L, title = "Missing"), listOf(page))
        val restoredPage = slot<Page>()
        coEvery { pageDao.insertPage(capture(restoredPage)) } returns 1L

        assertEquals(1, DocumentRecoveryManager.scanAndRestoreFromVault(context, database))

        assertEquals("", restoredPage.captured.processedImagePath)
        assertEquals("", restoredPage.captured.originalImagePath)
        assertEquals("", restoredPage.captured.thumbnailPath)
        coVerify(exactly = 0) { folderDao.insertFolder(any()) }
    }

    @Test
    fun minimalMetadataUsesDocumentAndPageDefaults() = runTest {
        File(StorageVault.getMetaDirectory(context), "minimal_meta.json").writeText(
            """{"createdTime":123,"pages":[{"processedFileName":"missing","originalFileName":"missing","thumbnailFileName":"missing"}]}"""
        )
        val restoredPage = slot<Page>()
        coEvery { documentDao.insertDocument(any()) } returns 77L
        coEvery { pageDao.insertPage(capture(restoredPage)) } returns 1L

        assertEquals(1, DocumentRecoveryManager.scanAndRestoreFromVault(context, database))

        coVerify { documentDao.insertDocument(match {
            it.title == "Restored Document" && it.modifiedTime == 123L && it.folderId == -1L
        }) }
        with(restoredPage.captured) {
            assertEquals(77L, documentId)
            assertEquals(0, pageIndex)
            assertEquals(1, enhanceMode)
            assertEquals(0, rotationDegrees)
            assertEquals(0, brightness)
            assertEquals(0, contrast)
            assertEquals("", ocrText)
        }
    }

    @Test
    fun metadataReplacementRemovesStalePagesAndPreservesOtherDocuments() {
        val doc = Document(id = 3L, title = "Old")
        val page = Page(documentId = 3L, pageIndex = 0, processedImagePath = "/old/page.jpg",
            originalImagePath = "/old/original.jpg")
        StorageVault.saveDocumentMetadata(context, doc, listOf(page), "Work")
        StorageVault.saveDocumentMetadata(context, Document(id = 4L, title = "Other"), emptyList())
        StorageVault.saveDocumentMetadata(context, doc.copy(title = "Updated"), emptyList())

        val meta = JSONObject(File(StorageVault.getMetaDirectory(context), "doc_3_meta.json").readText())
        assertEquals("Updated", meta.getString("title"))
        assertEquals(1, meta.getInt("version"))
        assertEquals(3L, meta.getLong("documentId"))
        assertEquals(0, meta.getInt("pageCount"))
        assertEquals(0, meta.getJSONArray("pages").length())
        assertEquals("", meta.getString("folderName"))

        StorageVault.deleteDocumentMetadata(context, 3L)
        StorageVault.deleteDocumentMetadata(context, 3L)
        assertTrue(File(StorageVault.getMetaDirectory(context), "doc_4_meta.json").exists())
    }

    @Test
    fun vaultFallsBackToInternalStorageWhenExternalStorageIsUnavailable() {
        every { context.getExternalFilesDir(null) } returns null

        assertEquals(tempFolder.root, StorageVault.getVaultRoot(context))
        assertEquals(File(tempFolder.root, "scans"), StorageVault.getScansDirectory(context))
        assertEquals(File(tempFolder.root, "thumbs"), StorageVault.getThumbsDirectory(context))
        assertEquals(File(tempFolder.root, "PDFs"), StorageVault.getPdfsDirectory(context))
        assertEquals(File(tempFolder.root, "meta"), StorageVault.getMetaDirectory(context))
    }

    @Test
    fun vaultFallsBackToInternalStorageWhenExternalLookupThrows() {
        every { context.getExternalFilesDir(null) } throws SecurityException("Denied")
        assertEquals(tempFolder.root, StorageVault.getVaultRoot(context))
    }

    @Test
    fun metadataStoresPortableFileNamesAndExplicitPageOrder() {
        val pages = listOf(
            Page(documentId = 6L, pageIndex = 1, processedImagePath = "/old/scans/second.jpg",
                originalImagePath = "/old/scans/orig-second.jpg", thumbnailPath = "/old/thumbs/second.jpg"),
            Page(documentId = 6L, pageIndex = 0, processedImagePath = "/old/scans/first.jpg",
                originalImagePath = "/old/scans/orig-first.jpg", thumbnailPath = "/old/thumbs/first.jpg")
        )
        StorageVault.saveDocumentMetadata(context, Document(id = 6L, title = "Portable", pageCount = 99), pages)

        val json = JSONObject(File(StorageVault.getMetaDirectory(context), "doc_6_meta.json").readText())
        assertEquals(2, json.getInt("pageCount"))
        val saved = json.getJSONArray("pages")
        assertEquals(1, saved.getJSONObject(0).getInt("pageIndex"))
        assertEquals(0, saved.getJSONObject(1).getInt("pageIndex"))
        assertEquals("second.jpg", saved.getJSONObject(0).getString("processedFileName"))
        assertEquals("orig-second.jpg", saved.getJSONObject(0).getString("originalFileName"))
        assertEquals("second.jpg", saved.getJSONObject(0).getString("thumbnailFileName"))
    }

    @Test
    fun recoveryResolvesIdCollisionWithoutReplacingExistingDocument() = runTest {
        val doc = Document(id = 88L, title = "Backed Up Colliding Doc")
        StorageVault.saveDocumentMetadata(context, doc, emptyList())

        // Database already contains an existing document with id 88L
        val existingDoc = Document(id = 88L, title = "Already Existing Doc")
        coEvery { documentDao.getDocumentById(88L) } returns existingDoc

        val insertedDocSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(insertedDocSlot)) } returns 200L

        val restored = DocumentRecoveryManager.scanAndRestoreFromVault(context, database)
        assertEquals(1, restored)

        // Must assign id = 0L to let Room generate a non-conflicting ID, rather than overwriting id = 88L
        assertTrue(insertedDocSlot.isCaptured)
        assertEquals(0L, insertedDocSlot.captured.id)
        assertEquals("Backed Up Colliding Doc", insertedDocSlot.captured.title)
    }
}

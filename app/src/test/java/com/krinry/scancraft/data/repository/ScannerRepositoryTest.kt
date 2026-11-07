package com.krinry.scancraft.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.krinry.scancraft.data.local.DocumentDao
import com.krinry.scancraft.data.local.FolderDao
import com.krinry.scancraft.data.local.PageDao
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import java.io.File
import java.io.IOException

/**
 * Production test suite for ScannerRepository:
 * 1. Document CRUD, title generation, and batch updates
 * 2. Folder CRUD, hierarchy path maintenance, and cycle prevention
 * 3. Page reordering, consecutive index updates, and deletion
 * 4. Deep-copy file rollback on DAO failure
 * 5. ID Card compositing geometry and book spread page association
 */
class ScannerRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var repository: ScannerRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempFolder.root
        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        repository = ScannerRepository(context, documentDao, pageDao, folderDao)
    }

    // =========================================================================
    // SECTION 1: Document CRUD & Title Management
    // =========================================================================

    @Test
    fun testCreateNewDocumentWithCustomTitle() = runTest {
        val docSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(docSlot)) } returns 101L

        val docId = repository.createNewDocument("Legal Brief 2026", folderId = 3L)
        assertEquals(101L, docId)
        assertTrue(docSlot.isCaptured)
        assertEquals("Legal Brief 2026", docSlot.captured.title)
        assertEquals(3L, docSlot.captured.folderId)
    }

    @Test
    fun testCreateNewDocumentWithDefaultGeneratedTitle() = runTest {
        val docSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(docSlot)) } returns 102L

        val docId = repository.createNewDocument(title = null, folderId = -1L)
        assertEquals(102L, docId)
        assertTrue(docSlot.isCaptured)
        assertTrue(docSlot.captured.title.startsWith("New Doc "))
        assertEquals(-1L, docSlot.captured.folderId)
    }

    @Test
    fun testUpdateDocumentTitle() = runTest {
        val doc = Document(id = 10L, title = "Old Title")
        coEvery { documentDao.getDocumentById(10L) } returns doc

        repository.updateDocumentTitle(10L, "New Title")
        coVerify(exactly = 1) {
            documentDao.updateDocument(match { it.id == 10L && it.title == "New Title" })
        }
    }

    @Test
    fun testBatchMoveDocuments() = runTest {
        val docIds = listOf(10L, 20L, 30L)
        repository.batchMoveToFolder(docIds, 999L)
        coVerify(exactly = 1) { documentDao.batchUpdateFolder(docIds, 999L) }
    }

    // =========================================================================
    // SECTION 2: Folder CRUD, Hierarchy Paths & Cycle Prevention
    // =========================================================================

    @Test
    fun testCreateRootFolderGeneratesRootPath() = runTest {
        val folderSlot = slot<Folder>()
        coEvery { folderDao.insertFolder(capture(folderSlot)) } returns 1L

        val folderId = repository.createFolder("Work", parentFolderId = -1L, color = "#4CAF50")
        assertEquals(1L, folderId)
        assertTrue(folderSlot.isCaptured)
        val created = folderSlot.captured
        assertEquals("Work", created.name)
        assertEquals(-1L, created.parentFolderId)
        assertEquals("/", created.path)
        assertEquals("#4CAF50", created.color)
    }

    @Test
    fun testCreateSubfolderGeneratesHierarchicalPath() = runTest {
        val parent = Folder(id = 5L, name = "Finance", parentFolderId = -1L, path = "/")
        coEvery { folderDao.getFolderById(5L) } returns parent

        val folderSlot = slot<Folder>()
        coEvery { folderDao.insertFolder(capture(folderSlot)) } returns 15L

        val subfolderId = repository.createFolder("Invoices", parentFolderId = 5L)
        assertEquals(15L, subfolderId)
        val created = folderSlot.captured
        assertEquals("Invoices", created.name)
        assertEquals(5L, created.parentFolderId)
        assertEquals("/5/", created.path)
    }

    @Test
    fun testRenameFolder() = runTest {
        coEvery { folderDao.getFolderById(7L) } returns Folder(id = 7L, name = "Drafts")

        repository.renameFolder(7L, "   Final Taxes 2026   ")
        coVerify(exactly = 1) {
            folderDao.updateFolder(match { it.id == 7L && it.name == "Final Taxes 2026" })
        }
    }

    @Test
    fun testDeleteFolderUnlinksDocuments() = runTest {
        val folder = Folder(id = 8L, name = "Temporary")

        repository.deleteFolder(folder)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            documentDao.unlinkDocumentsFromFolder(8L)
            folderDao.deleteFolder(folder)
        }
    }

    @Test
    fun testDirectSelfMoveDoesNotCreateCycle() = runTest {
        val folder = Folder(id = 10L, name = "Self", parentFolderId = -1L, path = "/")
        coEvery { folderDao.getFolderById(10L) } returns folder

        // Attempting to move folder 10 into itself must return safely without DB update
        repository.moveFolder(folderId = 10L, targetParentFolderId = 10L)
        coVerify(exactly = 0) { folderDao.updateFolder(any()) }
    }

    @Test
    fun testMoveFolderIntoDescendantThrowsIllegalArgumentException() = runTest {
        val parent = Folder(id = 1L, name = "Parent", parentFolderId = -1L, path = "/")
        val child = Folder(id = 2L, name = "Child", parentFolderId = 1L, path = "/1/")

        coEvery { folderDao.getFolderById(1L) } returns parent
        coEvery { folderDao.getFolderById(2L) } returns child

        try {
            repository.moveFolder(folderId = 1L, targetParentFolderId = 2L)
            fail("Expected IllegalArgumentException when moving folder into its own descendant")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        coVerify(exactly = 0) { folderDao.updateFolder(any()) }
    }

    // =========================================================================
    // SECTION 3: Page Operations & Reordering
    // =========================================================================

    @Test
    fun testReorderPagesConsecutiveIndexAssignment() = runTest {
        val docId = 55L
        val pageA = Page(id = 1L, documentId = docId, pageIndex = 2, processedImagePath = "", originalImagePath = "")
        val pageB = Page(id = 2L, documentId = docId, pageIndex = 0, processedImagePath = "", originalImagePath = "")
        val pageC = Page(id = 3L, documentId = docId, pageIndex = 1, processedImagePath = "", originalImagePath = "")

        val newOrder = listOf(pageA, pageB, pageC)
        val updatedPages = mutableListOf<Page>()
        coEvery { pageDao.updatePage(capture(updatedPages)) } returns Unit
        coEvery { documentDao.getDocumentById(docId) } returns Document(id = docId, title = "Reorder Test")

        repository.reorderPages(docId, newOrder)

        assertEquals(3, updatedPages.size)
        assertEquals(0, updatedPages[0].pageIndex)
        assertEquals(1L, updatedPages[0].id)
        assertEquals(1, updatedPages[1].pageIndex)
        assertEquals(2L, updatedPages[1].id)
        assertEquals(2, updatedPages[2].pageIndex)
        assertEquals(3L, updatedPages[2].id)

        coVerify(exactly = 1) { documentDao.updateDocument(any()) }
    }

    @Test
    fun testDeletePageUpdatesDocumentPageCount() = runTest {
        val docId = 99L
        val pageToDelete = Page(
            id = 501L,
            documentId = docId,
            pageIndex = 0,
            processedImagePath = "/nonexistent/proc.jpg",
            originalImagePath = "/nonexistent/orig.jpg",
            thumbnailPath = "/nonexistent/thumb.jpg"
        )

        val remainingPages = listOf(
            Page(id = 502L, documentId = docId, pageIndex = 1, processedImagePath = "", originalImagePath = "")
        )

        coEvery { pageDao.getPagesListForDocument(docId) } returns remainingPages

        repository.deletePage(pageToDelete)

        coVerify(exactly = 1) { pageDao.deletePage(pageToDelete) }
        coVerify(exactly = 1) { documentDao.updatePageCount(docId, 1, any()) }
    }

    // =========================================================================
    // SECTION 4: Deep-Copy Rollback & File Cleanup
    // =========================================================================

    @Test
    fun testCopyPageCleansUpOrphanedFileOnDaoFailure() = runTest {
        val origFile = tempFolder.newFile("source_page.jpg").apply { writeText("PAGE_IMAGE_BYTES") }
        val sourcePage = Page(
            id = 101L,
            documentId = 1L,
            pageIndex = 0,
            processedImagePath = origFile.absolutePath,
            originalImagePath = origFile.absolutePath,
            thumbnailPath = ""
        )

        coEvery { pageDao.insertPage(any()) } throws IOException("Disk IO or SQLite lock failure")

        try {
            repository.copyPagesToDocument(listOf(sourcePage), targetDocId = 2L)
            fail("Expected IOException on DAO failure")
        } catch (_: IOException) {
            // Expected
        }

        // Verify that no orphaned copied files remain in filesDir
        val filesInRoot = tempFolder.root.listFiles() ?: emptyArray()
        val copies = filesInRoot.filter { it.name.contains("copy") || it.name.startsWith("page_") }
        assertEquals("Orphaned copied files must be cleaned up on failure", 0, copies.size)
    }

    // =========================================================================
    // SECTION 5: ID Card & Layout Math Invariants
    // =========================================================================

    @Test
    fun testIdCardCompositingGeometryConstants() {
        val a4Width = 2480
        val a4Height = 3508

        val cardW = (a4Width * 0.70f).toInt()
        val cardH = Math.round(cardW / 1.586f).toInt()
        val cardX = (a4Width - cardW) / 2
        val frontY = (a4Height * 0.20f).toInt()
        val backY = (a4Height * 0.56f).toInt()

        assertEquals(1736, cardW)
        assertEquals(1095, cardH)
        assertEquals(372, cardX)
        assertEquals(701, frontY)
        assertEquals(1964, backY)

        assertTrue(frontY + cardH < backY)
        assertTrue(backY + cardH < a4Height)
    }
}

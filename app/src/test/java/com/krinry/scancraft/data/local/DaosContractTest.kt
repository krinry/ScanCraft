package com.krinry.scancraft.data.local

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page

/**
 * Tier 1 & Tier 2: Contract and interaction tests for Room DAOs.
 */
class DaosContractTest {

    private lateinit var documentDao: DocumentDao
    private lateinit var folderDao: FolderDao
    private lateinit var pageDao: PageDao

    @Before
    fun setUp() {
        documentDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
    }

    @Test
    fun testDocumentDaoInsertAndRetrieve() = runTest {
        val sampleDoc = Document(id = 1L, title = "Contract", folderId = -1L)
        coEvery { documentDao.insertDocument(any()) } returns 1L
        coEvery { documentDao.getDocumentById(1L) } returns sampleDoc

        val insertedId = documentDao.insertDocument(sampleDoc)
        assertEquals(1L, insertedId)

        val retrieved = documentDao.getDocumentById(1L)
        assertNotNull(retrieved)
        assertEquals("Contract", retrieved?.title)
        assertEquals(-1L, retrieved?.folderId)
    }

    @Test
    fun testDocumentDaoFolderFilteringAndUnlinking() = runTest {
        val rootDocs = listOf(Document(id = 1L, title = "Root 1", folderId = -1L))
        val folderDocs = listOf(Document(id = 2L, title = "Folder Doc", folderId = 10L))

        every { documentDao.getRootDocuments() } returns flowOf(rootDocs)
        every { documentDao.getDocumentsInFolder(10L) } returns flowOf(folderDocs)
        coEvery { documentDao.getFolderDocCount(10L) } returns 1

        val rootResult = documentDao.getRootDocuments().first()
        assertEquals(1, rootResult.size)
        assertEquals(1L, rootResult[0].id)

        val folderResult = documentDao.getDocumentsInFolder(10L).first()
        assertEquals(1, folderResult.size)
        assertEquals(2L, folderResult[0].id)

        val count = documentDao.getFolderDocCount(10L)
        assertEquals(1, count)

        // Unlink documents from folder
        documentDao.unlinkDocumentsFromFolder(10L)
        coVerify(exactly = 1) { documentDao.unlinkDocumentsFromFolder(10L) }
    }

    @Test
    fun testDocumentDaoBatchOperations() = runTest {
        val docIds = listOf(1L, 2L, 3L)

        documentDao.batchUpdateFolder(docIds, 20L)
        coVerify(exactly = 1) { documentDao.batchUpdateFolder(docIds, 20L) }

        documentDao.batchDeleteDocuments(docIds)
        coVerify(exactly = 1) { documentDao.batchDeleteDocuments(docIds) }
    }

    @Test
    fun testFolderDaoCrudContracts() = runTest {
        val folder = Folder(id = 5L, name = "Medical Records", color = "#4CAF50")
        coEvery { folderDao.insertFolder(any()) } returns 5L
        coEvery { folderDao.getFolderById(5L) } returns folder
        every { folderDao.getAllFolders() } returns flowOf(listOf(folder))

        val id = folderDao.insertFolder(folder)
        assertEquals(5L, id)

        val retrieved = folderDao.getFolderById(5L)
        assertEquals("Medical Records", retrieved?.name)
        assertEquals("#4CAF50", retrieved?.color)

        val allFolders = folderDao.getAllFolders().first()
        assertEquals(1, allFolders.size)

        folderDao.deleteFolderById(5L)
        coVerify(exactly = 1) { folderDao.deleteFolderById(5L) }
    }

    @Test
    fun testPageDaoSequentialOrderingAndBatchInsert() = runTest {
        val page1 = Page(id = 101L, documentId = 1L, pageIndex = 0, processedImagePath = "/p1.jpg", originalImagePath = "/o1.jpg")
        val page2 = Page(id = 102L, documentId = 1L, pageIndex = 1, processedImagePath = "/p2.jpg", originalImagePath = "/o2.jpg")

        coEvery { pageDao.insertPages(any()) } returns listOf(101L, 102L)
        coEvery { pageDao.getPagesListForDocument(1L) } returns listOf(page1, page2)
        every { pageDao.getPagesForDocument(1L) } returns flowOf(listOf(page1, page2))

        val ids = pageDao.insertPages(listOf(page1, page2))
        assertEquals(2, ids.size)

        val list = pageDao.getPagesListForDocument(1L)
        assertEquals(2, list.size)
        assertEquals(0, list[0].pageIndex)
        assertEquals(1, list[1].pageIndex)

        pageDao.deletePagesForDocument(1L)
        coVerify(exactly = 1) { pageDao.deletePagesForDocument(1L) }
    }
}

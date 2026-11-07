package com.krinry.scancraft.ui.detail

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository

/**
 * Production unit tests for DocumentDetailViewModel:
 * 1. Document loading by id
 * 2. Page reordering
 * 3. Document renaming
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: ScanCraftApp
    private lateinit var repository: ScannerRepository
    private lateinit var viewModel: DocumentDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        app = mockk(relaxed = true)
        viewModel = DocumentDetailViewModel(app)

        repository = mockk(relaxed = true)
        every { repository.getAllFolders() } returns flowOf(emptyList())
        every { repository.getAllDocuments() } returns flowOf(emptyList())

        val repoField = DocumentDetailViewModel::class.java.getDeclaredField("repository").apply { isAccessible = true }
        repoField.set(viewModel, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setDocument(doc: Document) {
        val field = DocumentDetailViewModel::class.java.getDeclaredField("_document").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(viewModel) as MutableStateFlow<Document?>
        stateFlow.value = doc
    }

    @Test
    fun testInitialState() {
        assertNull(viewModel.document.value)
        assertEquals(emptyList<Page>(), viewModel.pages.value)
    }

    @Test
    fun testLoadDocumentSuccess() = runTest {
        val sampleDoc = Document(id = 42L, title = "Quarterly Tax Report 2026", folderId = -1L, pageCount = 2)
        val samplePages = listOf(
            Page(id = 1L, documentId = 42L, pageIndex = 0, processedImagePath = "/path/p1.jpg", originalImagePath = "/path/orig1.jpg"),
            Page(id = 2L, documentId = 42L, pageIndex = 1, processedImagePath = "/path/p2.jpg", originalImagePath = "/path/orig2.jpg")
        )

        coEvery { repository.getDocumentById(42L) } returns sampleDoc
        every { repository.getPagesForDocument(42L) } returns flowOf(samplePages)

        viewModel.load(42L)

        assertNotNull(viewModel.document.value)
        assertEquals("Quarterly Tax Report 2026", viewModel.document.value?.title)
        assertEquals(2, viewModel.pages.value.size)
        assertEquals(1L, viewModel.pages.value[0].id)
        assertEquals(2L, viewModel.pages.value[1].id)
    }

    @Test
    fun testRenameDocument() = runTest {
        val sampleDoc = Document(id = 42L, title = "Old Title", folderId = -1L)
        setDocument(sampleDoc)

        coEvery { repository.updateDocument(any()) } returns Unit

        viewModel.renameDocument("Updated Title")

        assertEquals("Updated Title", viewModel.document.value?.title)
        coVerify(exactly = 1) {
            repository.updateDocument(match { it.id == 42L && it.title == "Updated Title" })
        }
    }

    @Test
    fun testReorderPages() = runTest {
        val docId = 42L
        val sampleDoc = Document(id = docId, title = "Reorder Doc", folderId = -1L)
        setDocument(sampleDoc)

        val pageA = Page(id = 1L, documentId = docId, pageIndex = 0, processedImagePath = "", originalImagePath = "")
        val pageB = Page(id = 2L, documentId = docId, pageIndex = 1, processedImagePath = "", originalImagePath = "")

        coEvery { repository.reorderPages(docId, any()) } returns Unit

        viewModel.reorderPages(listOf(pageB, pageA))

        coVerify(exactly = 1) {
            repository.reorderPages(docId, listOf(pageB, pageA))
        }
    }
}

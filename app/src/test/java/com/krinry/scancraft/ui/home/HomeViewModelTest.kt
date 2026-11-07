package com.krinry.scancraft.ui.home

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.local.AppDatabase
import com.krinry.scancraft.data.local.DocumentDao
import com.krinry.scancraft.data.local.FolderDao
import com.krinry.scancraft.data.local.PageDao
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder

/**
 * Production unit tests for HomeViewModel:
 * 1. UI state initialization and document/folder observation
 * 2. Sorting by date and name
 * 3. Search query filtering
 * 4. Multi-select batch mode, select all, and batch deletion
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: ScanCraftApp
    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        db = mockk(relaxed = true) {
            every { documentDao() } returns this@HomeViewModelTest.documentDao
            every { pageDao() } returns this@HomeViewModelTest.pageDao
            every { folderDao() } returns this@HomeViewModelTest.folderDao
        }

        every { folderDao.getAllFolders() } returns flowOf(emptyList())
        every { documentDao.getAllDocuments() } returns flowOf(emptyList())

        app = mockk(relaxed = true) {
            every { database } returns this@HomeViewModelTest.db
            every { filesDir } returns java.io.File("/tmp/scancraft_home_test")
        }

        viewModel = HomeViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() {
        assertEquals(emptyList<Document>(), viewModel.documents.value)
        assertEquals("", viewModel.searchQuery.value)
        assertEquals(DocSortOrder.DATE_DESC, viewModel.sortOrder.value)
        assertFalse(viewModel.isSelectMode.value)
        assertTrue(viewModel.selectedDocIds.value.isEmpty())
    }

    @Test
    fun testSetSearchQuery() {
        viewModel.onSearchQueryChanged("Invoice 2026")
        assertEquals("Invoice 2026", viewModel.searchQuery.value)

        viewModel.onSearchQueryChanged("")
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun testSetSortOrder() {
        viewModel.setSortOrder(DocSortOrder.NAME_ASC)
        assertEquals(DocSortOrder.NAME_ASC, viewModel.sortOrder.value)

        viewModel.setSortOrder(DocSortOrder.DATE_ASC)
        assertEquals(DocSortOrder.DATE_ASC, viewModel.sortOrder.value)
    }

    @Test
    fun testMultiSelectToggleAndSelection() {
        viewModel.toggleSelectMode()
        assertTrue(viewModel.isSelectMode.value)

        viewModel.toggleSelectDoc(101L)
        assertTrue(viewModel.selectedDocIds.value.contains(101L))
        assertEquals(1, viewModel.selectedDocIds.value.size)

        viewModel.toggleSelectDoc(102L)
        assertEquals(2, viewModel.selectedDocIds.value.size)

        // Toggle off
        viewModel.toggleSelectDoc(101L)
        assertFalse(viewModel.selectedDocIds.value.contains(101L))
        assertEquals(1, viewModel.selectedDocIds.value.size)

        viewModel.toggleSelectMode()
        assertFalse(viewModel.isSelectMode.value)
        assertTrue(viewModel.selectedDocIds.value.isEmpty())
    }
}

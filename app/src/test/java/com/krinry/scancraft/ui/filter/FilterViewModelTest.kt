package com.krinry.scancraft.ui.filter

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.local.AppDatabase
import com.krinry.scancraft.data.local.DocumentDao
import com.krinry.scancraft.data.local.FolderDao
import com.krinry.scancraft.data.local.PageDao
import com.krinry.scancraft.data.model.EnhanceMode

/**
 * Production unit tests for FilterViewModel:
 * 1. Default initial state (MAGIC_COLOR, zero brightness, zero contrast)
 * 2. Setting filter enhance modes
 * 3. Adjusting brightness and contrast values
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: ScanCraftApp
    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var viewModel: FilterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        db = mockk(relaxed = true) {
            every { documentDao() } returns this@FilterViewModelTest.documentDao
            every { pageDao() } returns this@FilterViewModelTest.pageDao
            every { folderDao() } returns this@FilterViewModelTest.folderDao
        }

        app = mockk(relaxed = true) {
            every { database } returns this@FilterViewModelTest.db
            every { filesDir } returns java.io.File("/tmp/scancraft_filter_test")
        }

        viewModel = FilterViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialFilterState() {
        assertEquals(EnhanceMode.MAGIC_COLOR, viewModel.selectedMode.value)
        assertEquals(0, viewModel.brightness.value)
        assertEquals(0, viewModel.contrast.value)
        assertFalse(viewModel.isSaving.value)
        assertNull(viewModel.previewBitmap.value)
    }

    @Test
    fun testSetFilterMode() {
        viewModel.setFilterMode(EnhanceMode.BW)
        assertEquals(EnhanceMode.BW, viewModel.selectedMode.value)

        viewModel.setFilterMode(EnhanceMode.ORIGINAL)
        assertEquals(EnhanceMode.ORIGINAL, viewModel.selectedMode.value)

        viewModel.setFilterMode(EnhanceMode.GRAYSCALE)
        assertEquals(EnhanceMode.GRAYSCALE, viewModel.selectedMode.value)

        viewModel.setFilterMode(EnhanceMode.MAGIC_COLOR)
        assertEquals(EnhanceMode.MAGIC_COLOR, viewModel.selectedMode.value)
    }

    @Test
    fun testSetBrightnessAndContrast() {
        viewModel.setBrightness(25)
        assertEquals(25, viewModel.brightness.value)

        viewModel.setBrightness(-15)
        assertEquals(-15, viewModel.brightness.value)

        viewModel.setContrast(40)
        assertEquals(40, viewModel.contrast.value)

        viewModel.setContrast(-30)
        assertEquals(-30, viewModel.contrast.value)
    }
}

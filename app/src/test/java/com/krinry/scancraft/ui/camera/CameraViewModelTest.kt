package com.krinry.scancraft.ui.camera

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.camera.CaptureMode
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.PointF

/**
 * Production unit tests for CameraViewModel:
 * 1. Initial camera state and capture mode transitions
 * 2. Flash mode toggle cycle
 * 3. Batch capture queue management and item rotation cycles
 */
class CameraViewModelTest {

    private lateinit var application: Application
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        mockkStatic(Bitmap::class)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        every { Bitmap.createBitmap(any<Bitmap>(), any(), any(), any(), any(), any(), any()) } returns dummyBitmap
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) } returns dummyBitmap

        application = mockk(relaxed = true)
        viewModel = CameraViewModel(application)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInitialCameraState() {
        assertEquals(CaptureMode.DOCUMENT, viewModel.currentMode.value)
        assertEquals(0, viewModel.batchCount.value)
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.flashMode.value)
        assertNull(viewModel.detectedQuad.value)
        assertEquals(Pair(0, 0), viewModel.previewDimens.value)
        assertTrue(viewModel.batchQueue.value.isEmpty())
    }

    @Test
    fun testSetModeTransitions() {
        viewModel.setMode(CaptureMode.ID_CARD)
        assertEquals(CaptureMode.ID_CARD, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.BOOK)
        assertEquals(CaptureMode.BOOK, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.QR_CODE)
        assertEquals(CaptureMode.QR_CODE, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.DOCUMENT)
        assertEquals(CaptureMode.DOCUMENT, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.TO_TEXT)
        assertEquals(CaptureMode.TO_TEXT, viewModel.currentMode.value)
    }

    @Test
    fun testFlashToggleCycle() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.flashMode.value)

        viewModel.toggleFlash()
        assertEquals(ImageCapture.FLASH_MODE_ON, viewModel.flashMode.value)

        viewModel.toggleFlash()
        assertEquals(ImageCapture.FLASH_MODE_AUTO, viewModel.flashMode.value)

        viewModel.toggleFlash()
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.flashMode.value)
    }

    @Test
    fun testBatchQueueOperationsAndRotationCycle() {
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item1 = BatchCaptureItem(id = 1L, rawFilePath = "/cache/1.jpg", thumbnailBitmap = dummyBitmap, rotationDegrees = 0)
        val item2 = BatchCaptureItem(id = 2L, rawFilePath = "/cache/2.jpg", thumbnailBitmap = dummyBitmap, rotationDegrees = 0)

        viewModel.addBatchItem(item1)
        viewModel.addBatchItem(item2)

        assertEquals(2, viewModel.batchCount.value)
        assertEquals(2, viewModel.batchQueue.value.size)

        // Rotate item1
        viewModel.rotateBatchItem(item1.id)
        val rotatedItem1 = viewModel.batchQueue.value.first { it.id == 1L }
        assertEquals(90, rotatedItem1.rotationDegrees)

        // Remove item1
        viewModel.removeBatchItem(rotatedItem1.id)
        assertEquals(1, viewModel.batchCount.value)
        assertEquals(2L, viewModel.batchQueue.value[0].id)

        // Clear queue
        viewModel.clearBatch()
        assertEquals(0, viewModel.batchCount.value)
        assertTrue(viewModel.batchQueue.value.isEmpty())
    }

    @Test
    fun testOnDocumentDetectedUpdatesQuadAndDimens() {
        val quad = DocumentQuadrilateral(
            topLeft = PointF(10f, 10f),
            topRight = PointF(90f, 10f),
            bottomRight = PointF(90f, 90f),
            bottomLeft = PointF(10f, 90f)
        )
        viewModel.onDocumentDetected(quad, 1080, 1920)
        assertNotNull(viewModel.detectedQuad.value)
    }
}

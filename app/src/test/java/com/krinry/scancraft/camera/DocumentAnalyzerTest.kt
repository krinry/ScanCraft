package com.krinry.scancraft.camera

import androidx.camera.core.ImageProxy
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.NativeScannerEngine
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class DocumentAnalyzerTest {
    private val detected = mockk<DocumentQuadrilateral>()

    @Before
    fun setUp() {
        mockkObject(NativeScannerEngine)
        every { NativeScannerEngine.detectDocumentBounds(any<ByteArray>(), any(), any()) } returns detected
    }

    @After
    fun tearDown() {
        unmockkObject(NativeScannerEngine)
    }

    private fun frame(bytes: ByteArray, width: Int, height: Int, rowStride: Int,
                      pixelStride: Int, rotation: Int): ImageProxy {
        val plane = mockk<ImageProxy.PlaneProxy>()
        val buffer = ByteBuffer.wrap(bytes)
        // Packed extraction must rewind a buffer whose position was previously advanced.
        buffer.position(1)
        every { plane.buffer } returns buffer
        every { plane.rowStride } returns rowStride
        every { plane.pixelStride } returns pixelStride
        return mockk<ImageProxy>(relaxed = true).also {
            every { it.planes } returns arrayOf(plane)
            every { it.width } returns width
            every { it.height } returns height
            every { it.imageInfo.rotationDegrees } returns rotation
        }
    }

    private fun assertAnalyzed(bytes: ByteArray, rowStride: Int, pixelStride: Int,
                               rotation: Int, expected: ByteArray, width: Int, height: Int) {
        val image = frame(bytes, 3, 2, rowStride, pixelStride, rotation)
        val callback = mockk<(DocumentQuadrilateral, Int, Int) -> Unit>(relaxed = true)
        val delivered = slot<ByteArray>()
        every { NativeScannerEngine.detectDocumentBounds(capture(delivered), width, height) } returns detected

        DocumentAnalyzer(callback).analyze(image)

        assertArrayEquals(expected, delivered.captured)
        verify(exactly = 1) { callback(detected, width, height) }
        verify(exactly = 1) { image.close() }
    }

    @Test
    fun packedPlaneIsCopiedFromStartWithoutRotation() {
        assertAnalyzed(byteArrayOf(1, 2, 3, 4, 5, 6), 3, 1, 0,
            byteArrayOf(1, 2, 3, 4, 5, 6), 3, 2)
    }

    @Test
    fun clockwiseRotationSwapsNonSquareDimensions() {
        assertAnalyzed(byteArrayOf(1, 2, 3, 4, 5, 6), 3, 1, 90,
            byteArrayOf(4, 1, 5, 2, 6, 3), 2, 3)
    }

    @Test
    fun halfTurnReversesPixels() {
        assertAnalyzed(byteArrayOf(1, 2, 3, 4, 5, 6), 3, 1, 180,
            byteArrayOf(6, 5, 4, 3, 2, 1), 3, 2)
    }

    @Test
    fun counterClockwiseRotationSwapsNonSquareDimensions() {
        assertAnalyzed(byteArrayOf(1, 2, 3, 4, 5, 6), 3, 1, 270,
            byteArrayOf(3, 6, 2, 5, 1, 4), 2, 3)
    }

    @Test
    fun rowPaddingIsExcludedBeforeRotation() {
        assertAnalyzed(byteArrayOf(1, 2, 3, 99, 99, 4, 5, 6), 5, 1, 90,
            byteArrayOf(4, 1, 5, 2, 6, 3), 2, 3)
    }

    @Test
    fun pixelStrideAndRowPaddingAreExcluded() {
        assertAnalyzed(byteArrayOf(1, 99, 2, 99, 3, 99, 99, 4, 99, 5, 99, 6),
            7, 2, 270, byteArrayOf(3, 6, 2, 5, 1, 4), 2, 3)
    }

    @Test
    fun frameWithoutPlanesIsClosedWithoutDetection() {
        val image = mockk<ImageProxy>(relaxed = true)
        every { image.planes } returns emptyArray()
        val callback = mockk<(DocumentQuadrilateral, Int, Int) -> Unit>(relaxed = true)

        DocumentAnalyzer(callback).analyze(image)

        verify(exactly = 1) { image.close() }
        verify(exactly = 0) { NativeScannerEngine.detectDocumentBounds(any<ByteArray>(), any(), any()) }
        verify(exactly = 0) { callback(any(), any(), any()) }
    }
}

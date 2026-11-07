package com.krinry.scancraft.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.NativeScannerEngine
import java.nio.ByteBuffer

class DocumentAnalyzer(
    private val onDocumentDetected: (DocumentQuadrilateral, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Throttle to ~25 fps for responsive real-time tracking
        if (currentTimestamp - lastAnalyzedTimestamp < 40) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val planes = image.planes
        if (planes.isEmpty()) {
            image.close()
            return
        }

        val yPlane = planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = image.width
        val height = image.height
        val rotation = image.imageInfo.rotationDegrees

        // Extract clean, unpadded Y-plane byte array (width x height) without rowStride shearing
        val cleanBytes = ByteArray(width * height)
        if (rowStride == width && pixelStride == 1) {
            yBuffer.rewind()
            yBuffer.get(cleanBytes, 0, width * height)
        } else {
            var destPos = 0
            for (row in 0 until height) {
                if (pixelStride == 1) {
                    yBuffer.position(row * rowStride)
                    yBuffer.get(cleanBytes, destPos, width)
                    destPos += width
                } else {
                    var srcPos = row * rowStride
                    for (col in 0 until width) {
                        cleanBytes[destPos++] = yBuffer.get(srcPos)
                        srcPos += pixelStride
                    }
                }
            }
        }

        // Rotate Y-plane to match upright display orientation
        val (rotatedBytes, rotW, rotH) = rotateYPlane(cleanBytes, width, height, rotation)

        val quad = NativeScannerEngine.detectDocumentBounds(rotatedBytes, rotW, rotH)

        onDocumentDetected(quad, rotW, rotH)
        image.close()
    }

    companion object {
        fun rotateYPlane(src: ByteArray, width: Int, height: Int, rotation: Int): Triple<ByteArray, Int, Int> {
            return when (rotation) {
                90 -> {
                    val dst = ByteArray(width * height)
                    var dstIdx = 0
                    for (x in 0 until width) {
                        for (y in height - 1 downTo 0) {
                            dst[dstIdx++] = src[y * width + x]
                        }
                    }
                    Triple(dst, height, width)
                }
                180 -> {
                    val dst = ByteArray(width * height)
                    val total = width * height
                    for (i in 0 until total) {
                        dst[i] = src[total - 1 - i]
                    }
                    Triple(dst, width, height)
                }
                270 -> {
                    val dst = ByteArray(width * height)
                    var dstIdx = 0
                    for (x in width - 1 downTo 0) {
                        for (y in 0 until height) {
                            dst[dstIdx++] = src[y * width + x]
                        }
                    }
                    Triple(dst, height, width)
                }
                else -> Triple(src, width, height)
            }
        }
    }
}

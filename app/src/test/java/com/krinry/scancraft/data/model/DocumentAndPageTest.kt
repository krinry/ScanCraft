package com.krinry.scancraft.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 & Tier 2: Tests for Document, Page, Folder entities and EnhanceMode enum.
 */
class DocumentAndPageTest {

    @Test
    fun testDocumentDefaultValues() {
        val now = System.currentTimeMillis()
        val doc = Document(title = "Tax Invoice 2026")

        assertEquals(0L, doc.id)
        assertEquals("Tax Invoice 2026", doc.title)
        assertTrue(doc.createdTime <= System.currentTimeMillis() && doc.createdTime >= now - 1000)
        assertTrue(doc.modifiedTime <= System.currentTimeMillis() && doc.modifiedTime >= now - 1000)
        assertEquals(0, doc.pageCount)
        assertEquals("", doc.tags)
        assertEquals(-1L, doc.folderId)
    }

    @Test
    fun testDocumentCopyMutation() {
        val doc1 = Document(id = 10, title = "Original Doc", folderId = -1L, pageCount = 2)
        val doc2 = doc1.copy(title = "Renamed Doc", folderId = 5L, pageCount = 3)

        assertEquals(10L, doc2.id)
        assertEquals("Renamed Doc", doc2.title)
        assertEquals(5L, doc2.folderId)
        assertEquals(3, doc2.pageCount)
    }

    @Test
    fun testPageDefaultValues() {
        val page = Page(
            documentId = 42L,
            pageIndex = 0,
            processedImagePath = "/data/files/scans/page_42_1.jpg",
            originalImagePath = "/data/files/scans/orig_42_1.jpg"
        )

        assertEquals(0L, page.id)
        assertEquals(42L, page.documentId)
        assertEquals(0, page.pageIndex)
        assertEquals("/data/files/scans/page_42_1.jpg", page.processedImagePath)
        assertEquals("/data/files/scans/orig_42_1.jpg", page.originalImagePath)
        assertEquals("", page.thumbnailPath)
        assertEquals("", page.cropBorderJson)
        assertEquals(1, page.enhanceMode) // Default Magic Color
        assertEquals(0, page.rotationDegrees)
        assertEquals(0, page.brightness)
        assertEquals(0, page.contrast)
        assertEquals("", page.ocrText)
        assertEquals("", page.watermarkText)
    }

    @Test
    fun testFolderEntityAndCountWrapper() {
        val folder = Folder(id = 7L, name = "Receipts")
        assertEquals(7L, folder.id)
        assertEquals("Receipts", folder.name)
        assertEquals("#EEAA3B", folder.color)
        assertEquals(0, folder.orderIndex)

        val folderWithCount = FolderWithCount(folder = folder, docCount = 12)
        assertEquals(folder, folderWithCount.folder)
        assertEquals(12, folderWithCount.docCount)
    }

    @Test
    fun testEnhanceModeMappings() {
        assertEquals(EnhanceMode.ORIGINAL, EnhanceMode.fromId(0))
        assertEquals(EnhanceMode.MAGIC_COLOR, EnhanceMode.fromId(1))
        assertEquals(EnhanceMode.BW, EnhanceMode.fromId(2))
        assertEquals(EnhanceMode.BW2, EnhanceMode.fromId(3))
        assertEquals(EnhanceMode.GRAYSCALE, EnhanceMode.fromId(4))
        assertEquals(EnhanceMode.LIGHTEN, EnhanceMode.fromId(5))
        assertEquals(EnhanceMode.NO_SHADOW, EnhanceMode.fromId(6))
        assertEquals(EnhanceMode.DEBLUR, EnhanceMode.fromId(7))
        assertEquals(EnhanceMode.COLOR_DOC, EnhanceMode.fromId(8))

        // Unknown / fallback ID must default to MAGIC_COLOR
        assertEquals(EnhanceMode.MAGIC_COLOR, EnhanceMode.fromId(999))
        assertEquals(EnhanceMode.MAGIC_COLOR, EnhanceMode.fromId(-1))
    }
}

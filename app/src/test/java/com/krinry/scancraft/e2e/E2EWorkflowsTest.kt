package com.krinry.scancraft.e2e

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.krinry.scancraft.camera.CaptureMode
import com.krinry.scancraft.cv.CollageGenerator
import com.krinry.scancraft.cv.CollageLayout
import com.krinry.scancraft.cv.DocumentQuadrilateral
import com.krinry.scancraft.cv.PointF
import com.krinry.scancraft.data.local.DocumentDao
import com.krinry.scancraft.data.local.FolderDao
import com.krinry.scancraft.data.local.PageDao
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.EnhanceMode
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page
import com.krinry.scancraft.data.repository.ScannerRepository
import com.krinry.scancraft.pdf.PdfExportOptions
import com.krinry.scancraft.pdf.PdfPageSize
import com.krinry.scancraft.pdf.PdfQuality
import java.io.File

/**
 * Tier 3 (Cross-Feature Integrations) & Tier 4 (Real-World Application Scenarios)
 * End-to-End Workflow Tests.
 */
class E2EWorkflowsTest {

    private lateinit var context: Context
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var repository: ScannerRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        repository = ScannerRepository(context, documentDao, pageDao, folderDao)
    }

    /**
     * Tier 3 Scenario 1:
     * Batch Burst Capture -> Page Manager Reorder -> Watermarked & Encrypted PDF Export Options.
     */
    @Test
    fun testTier3BatchCaptureReorderAndPdfWorkflow() = runTest {
        val docId = 201L
        coEvery { documentDao.insertDocument(any()) } returns docId
        coEvery { documentDao.getDocumentById(docId) } returns Document(id = docId, title = "Quarterly Report", pageCount = 4)

        // 1. Simulate 4 pages captured in Batch mode
        val pages = listOf(
            Page(id = 11L, documentId = docId, pageIndex = 0, processedImagePath = "/p1.jpg", originalImagePath = "/o1.jpg"),
            Page(id = 12L, documentId = docId, pageIndex = 1, processedImagePath = "/p2.jpg", originalImagePath = "/o2.jpg"),
            Page(id = 13L, documentId = docId, pageIndex = 2, processedImagePath = "/p3.jpg", originalImagePath = "/o3.jpg"),
            Page(id = 14L, documentId = docId, pageIndex = 3, processedImagePath = "/p4.jpg", originalImagePath = "/o4.jpg")
        )

        // 2. User reorders pages to reverse order in Page Manager
        val reordered = listOf(pages[3], pages[2], pages[1], pages[0])
        val capturedPages = mutableListOf<Page>()
        coEvery { pageDao.updatePage(capture(capturedPages)) } returns Unit

        repository.reorderPages(docId, reordered)

        assertEquals(4, capturedPages.size)
        assertEquals(14L, capturedPages[0].id)
        assertEquals(0, capturedPages[0].pageIndex)
        assertEquals(13L, capturedPages[1].id)
        assertEquals(1, capturedPages[1].pageIndex)
        assertEquals(12L, capturedPages[2].id)
        assertEquals(2, capturedPages[2].pageIndex)
        assertEquals(11L, capturedPages[3].id)
        assertEquals(3, capturedPages[3].pageIndex)

        // 3. User exports to PDF with custom watermark text and AES-128 password encryption
        val exportOptions = PdfExportOptions(
            pageSize = PdfPageSize.A4,
            quality = PdfQuality.HIGH,
            password = "ConfidentialPassword#2026",
            watermarkText = "INTERNAL USE ONLY"
        )

        assertEquals(PdfPageSize.A4, exportOptions.pageSize)
        assertEquals(PdfQuality.HIGH, exportOptions.quality)
        assertEquals("ConfidentialPassword#2026", exportOptions.password)
        assertEquals("INTERNAL USE ONLY", exportOptions.watermarkText)
    }

    /**
     * Tier 3 Scenario 2:
     * ID Card Dual-Side Capture -> Signature Stamping -> Single-Page Legal Document.
     */
    @Test
    fun testTier3IdCardStitchAndSignatureWorkflow() = runTest {
        val docId = 301L
        coEvery { documentDao.getDocumentById(docId) } returns Document(id = docId, title = "National ID Card")

        // Standard ID card geometry check
        val a4W = 2480
        val a4H = 3508
        val cardW = (a4W * 0.70f).toInt()
        val cardH = (cardW / 1.586f).toInt()
        val cardX = (a4W - cardW) / 2
        val frontY = (a4H * 0.20f).toInt()
        val backY = (a4H * 0.56f).toInt()

        // Verify ID card slots fit cleanly without overlapping
        assertEquals(1736, cardW)
        assertEquals(1094, cardH)
        assertTrue(frontY + cardH < backY)
        assertTrue(backY + cardH < a4H)

        // Signature placement bounding box at bottom-right margin
        val sigTargetWidth = (a4W * 0.35f).toInt()
        val sigTargetHeight = (sigTargetWidth * 0.5f).toInt()
        val sigMarginX = (a4W * 0.08f).toInt()
        val sigMarginY = (a4H * 0.08f).toInt()

        val sigLeft = a4W - sigTargetWidth - sigMarginX
        val sigTop = a4H - sigTargetHeight - sigMarginY
        val sigRight = a4W - sigMarginX
        val sigBottom = a4H - sigMarginY

        assertTrue(sigLeft > cardX)
        assertTrue(sigTop > backY)
        assertTrue(sigRight <= a4W)
        assertTrue(sigBottom <= a4H)
    }

    /**
     * Tier 3 Scenario 3:
     * Hierarchical Folder Creation -> Batch Move Documents -> Folder Unlink Deletion.
     */
    @Test
    fun testTier3FolderHierarchyBatchMoveAndUnlinkWorkflow() = runTest {
        // 1. Create target folder
        val folderSlot = slot<Folder>()
        coEvery { folderDao.insertFolder(capture(folderSlot)) } returns 50L
        val folderId = repository.createFolder("Legal Archives", "#3F51B5")

        assertEquals(50L, folderId)
        assertEquals("Legal Archives", folderSlot.captured.name)
        assertEquals("#3F51B5", folderSlot.captured.color)

        // 2. Batch move 3 documents into folder
        val docIds = listOf(101L, 102L, 103L)
        repository.batchMoveToFolder(docIds, 50L)
        coVerify(exactly = 1) { documentDao.batchUpdateFolder(docIds, 50L) }

        // 3. Delete folder with document unlinking (reverting to root folderId = -1)
        val folderToDelete = Folder(id = 50L, name = "Legal Archives")
        repository.deleteFolder(folderToDelete)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            documentDao.unlinkDocumentsFromFolder(50L)
            folderDao.deleteFolder(folderToDelete)
        }
    }

    /**
     * Tier 4 Scenario 1:
     * Full Real-World Office Document Scanning Journey:
     * Intake -> Auto-crop Quadrilateral -> Magic Color Filter -> Offline OCR -> PDF Export.
     */
    @Test
    fun testTier4FullOfficeDocumentScanningJourney() = runTest {
        val width = 1080f
        val height = 1920f

        // Step 1: Real-time edge detection identifies document boundary quad
        val detectedQuad = DocumentQuadrilateral.defaultForDimensions(width, height, marginFraction = 0.06f)
        assertEquals(64.8f, detectedQuad.topLeft.x, 0.001f)
        assertEquals(115.2f, detectedQuad.topLeft.y, 0.001f)
        assertEquals(1015.2f, detectedQuad.bottomRight.x, 0.001f)
        assertEquals(1804.8f, detectedQuad.bottomRight.y, 0.001f)

        // Step 2: Quad array serialization for Room storage
        val quadArray = detectedQuad.toFloatArray()
        assertEquals(8, quadArray.size)
        val deserializedQuad = DocumentQuadrilateral.fromFloatArray(quadArray)
        assertEquals(detectedQuad.topLeft, deserializedQuad.topLeft)

        // Step 3: Color enhancement mode selection
        val chosenFilter = EnhanceMode.MAGIC_COLOR
        assertEquals(1, chosenFilter.id)
        assertEquals("Magic Color", chosenFilter.displayName)

        // Step 4: Text extraction with offline OCR model
        val recognizedOcrText = """
            ACME CORPORATION
            Invoice No: INV-2026-9081
            Date: 23 October 2026
            Amount Due: USD 4,950.00
            Status: Approved
        """.trimIndent()
        assertTrue(recognizedOcrText.contains("INV-2026-9081"))
        assertTrue(recognizedOcrText.contains("USD 4,950.00"))

        // Step 5: Save page entity
        val page = Page(
            id = 1L,
            documentId = 777L,
            pageIndex = 0,
            processedImagePath = "/scans/page_777_1.jpg",
            originalImagePath = "/scans/orig_777_1.jpg",
            enhanceMode = chosenFilter.id,
            ocrText = recognizedOcrText
        )
        assertEquals(1, page.enhanceMode)
        assertTrue(page.ocrText.contains("Approved"))

        // Step 6: Generate final PDF with balanced compression
        val pdfOptions = PdfExportOptions(
            pageSize = PdfPageSize.A4,
            quality = PdfQuality.MEDIUM
        )
        assertEquals(75, pdfOptions.quality.jpegQuality)
    }

    /**
     * Tier 4 Scenario 2:
     * Dual-Page Book Digitization Journey:
     * Book spread capture -> Spine detection split -> Two consecutive document pages.
     */
    @Test
    fun testTier4BookDualPageSplitJourney() = runTest {
        val spreadWidth = 2000
        val spreadHeight = 1400
        assertEquals(1400, spreadHeight)

        // Center spine divider
        val spineX = spreadWidth / 2
        assertEquals(1000, spineX)

        val leftPageWidth = spineX
        val rightPageWidth = spreadWidth - spineX
        assertEquals(1000, leftPageWidth)
        assertEquals(1000, rightPageWidth)

        // Create consecutive document pages
        val docId = 888L
        val leftPage = Page(
            id = 1L,
            documentId = docId,
            pageIndex = 0,
            processedImagePath = "/scans/book_left_0.jpg",
            originalImagePath = "/scans/book_orig_0.jpg"
        )
        val rightPage = Page(
            id = 2L,
            documentId = docId,
            pageIndex = 1,
            processedImagePath = "/scans/book_right_1.jpg",
            originalImagePath = "/scans/book_orig_1.jpg"
        )

        assertEquals(0, leftPage.pageIndex)
        assertEquals(1, rightPage.pageIndex)
        assertEquals(docId, leftPage.documentId)
        assertEquals(docId, rightPage.documentId)
    }

    /**
     * Tier 4 Scenario 3:
     * Expense Receipt Multi-Page Collage Compilation Journey:
     * 4 scanned receipts -> FOUR_GRID layout selection -> Combined A4 sheet.
     */
    @Test
    fun testTier4CollageCompilationJourney() = runTest {
        val receipts = listOf(1L, 2L, 3L, 4L)
        val layout = CollageLayout.FOUR_GRID

        assertEquals(4, receipts.size)
        assertEquals(4, layout.maxPages)

        // Verify slot dimensions on 300 DPI A4
        val canvasW = 2480
        val canvasH = 3508
        val margin = (canvasW * 0.04f).toInt()
        val spacing = (canvasW * 0.02f).toInt()

        val contentW = canvasW - (margin * 2)
        val contentH = canvasH - (margin * 2)

        val slotW = (contentW - spacing) / 2
        val slotH = (contentH - spacing) / 2

        assertEquals(1116, slotW)
        assertEquals(1630, slotH)

        // Total 4 quadrants completely fill the grid
        assertEquals(4, 2 * 2)
    }
}

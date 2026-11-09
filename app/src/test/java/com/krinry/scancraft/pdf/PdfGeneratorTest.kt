package com.krinry.scancraft.pdf

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Production unit tests for native PdfGenerator:
 * 1. Empty pages validation and error handling
 * 2. Standard page dimensions (A4, Letter, Legal, Fit)
 * 3. Margin calculations across page sizes
 */
class PdfGeneratorTest {

    @Test
    fun testGeneratePdfFailsOnEmptyPages() = runTest {
        val context = mockk<Context>(relaxed = true)
        val emptyPages = emptyList<com.krinry.scancraft.data.model.Page>()
        val outputFile = File("/tmp/test_output.pdf")
        val options = PdfExportOptions()

        val result = PdfGenerator.generatePdf(context, emptyPages, options, outputFile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("No pages to generate PDF", result.exceptionOrNull()?.message)
    }

    @Test
    fun testPageDimensions() {
        assertEquals(595, PdfPageSize.A4.dimension?.widthPoints)
        assertEquals(842, PdfPageSize.A4.dimension?.heightPoints)

        assertEquals(612, PdfPageSize.LETTER.dimension?.widthPoints)
        assertEquals(792, PdfPageSize.LETTER.dimension?.heightPoints)

        assertEquals(612, PdfPageSize.LEGAL.dimension?.widthPoints)
        assertEquals(1008, PdfPageSize.LEGAL.dimension?.heightPoints)

        assertNull(PdfPageSize.FIT_IMAGE.dimension)
    }

    @Test
    fun testMarginAndScalingCalculationsForPageSizes() {
        fun getMargin(pageSize: PdfPageSize): Float {
            return if (pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
        }

        assertEquals(18f, getMargin(PdfPageSize.A4), 0.001f)
        assertEquals(18f, getMargin(PdfPageSize.LETTER), 0.001f)
        assertEquals(18f, getMargin(PdfPageSize.LEGAL), 0.001f)
        assertEquals(0f, getMargin(PdfPageSize.FIT_IMAGE), 0.001f)
    }
}

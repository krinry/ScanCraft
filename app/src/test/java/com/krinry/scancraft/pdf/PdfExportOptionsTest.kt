package com.krinry.scancraft.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Production unit tests for PDF options, page sizes, and export configurations.
 */
class PdfExportOptionsTest {

    @Test
    fun testPdfPageSizeDefinitions() {
        assertEquals("A4 (210 x 297 mm)", PdfPageSize.A4.title)
        assertNotNull(PdfPageSize.A4.dimension)
        assertEquals(595, PdfPageSize.A4.dimension?.widthPoints)
        assertEquals(842, PdfPageSize.A4.dimension?.heightPoints)

        assertEquals("US Letter (8.5 x 11 in)", PdfPageSize.LETTER.title)
        assertNotNull(PdfPageSize.LETTER.dimension)
        assertEquals(612, PdfPageSize.LETTER.dimension?.widthPoints)
        assertEquals(792, PdfPageSize.LETTER.dimension?.heightPoints)

        assertEquals("US Legal (8.5 x 14 in)", PdfPageSize.LEGAL.title)
        assertNotNull(PdfPageSize.LEGAL.dimension)
        assertEquals(612, PdfPageSize.LEGAL.dimension?.widthPoints)
        assertEquals(1008, PdfPageSize.LEGAL.dimension?.heightPoints)

        assertEquals("Fit to Image Size", PdfPageSize.FIT_IMAGE.title)
        assertNull(PdfPageSize.FIT_IMAGE.dimension)
    }

    @Test
    fun testPdfQualityPresets() {
        assertEquals(92, PdfQuality.HIGH.jpegQuality)
        assertEquals("High Quality (Original)", PdfQuality.HIGH.title)

        assertEquals(75, PdfQuality.MEDIUM.jpegQuality)
        assertEquals("Medium (Recommended)", PdfQuality.MEDIUM.title)

        assertEquals(50, PdfQuality.LOW.jpegQuality)
        assertEquals("Small File Size", PdfQuality.LOW.title)
    }

    @Test
    fun testPdfExportOptionsDefaultsAndCustomizations() {
        val defaults = PdfExportOptions()
        assertEquals(PdfPageSize.A4, defaults.pageSize)
        assertEquals(PdfQuality.MEDIUM, defaults.quality)
        assertNull(defaults.password)
        assertNull(defaults.watermarkText)

        val custom = PdfExportOptions(
            pageSize = PdfPageSize.LETTER,
            quality = PdfQuality.HIGH,
            password = "SecretPassword",
            watermarkText = "CONFIDENTIAL"
        )
        assertEquals(PdfPageSize.LETTER, custom.pageSize)
        assertEquals(PdfQuality.HIGH, custom.quality)
        assertEquals("SecretPassword", custom.password)
        assertEquals("CONFIDENTIAL", custom.watermarkText)
    }
}

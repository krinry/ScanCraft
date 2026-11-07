package com.krinry.scancraft.pdf

import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Production unit tests for PDF options, page sizes, and export configurations.
 */
class PdfExportOptionsTest {

    @Test
    fun testPdfPageSizeDefinitions() {
        assertEquals("A4 (210 x 297 mm)", PdfPageSize.A4.title)
        assertEquals(PDRectangle.A4, PdfPageSize.A4.rectangle)

        assertEquals("US Letter (8.5 x 11 in)", PdfPageSize.LETTER.title)
        assertEquals(PDRectangle.LETTER, PdfPageSize.LETTER.rectangle)

        assertEquals("US Legal", PdfPageSize.LEGAL.title)
        assertEquals(PDRectangle.LEGAL, PdfPageSize.LEGAL.rectangle)

        assertEquals("Fit to Image Size", PdfPageSize.FIT_IMAGE.title)
        assertNull(PdfPageSize.FIT_IMAGE.rectangle)
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
            password = "SecretPass123!@#",
            watermarkText = "CONFIDENTIAL"
        )
        assertEquals(PdfPageSize.LETTER, custom.pageSize)
        assertEquals(PdfQuality.HIGH, custom.quality)
        assertEquals("SecretPass123!@#", custom.password)
        assertEquals("CONFIDENTIAL", custom.watermarkText)
    }
}

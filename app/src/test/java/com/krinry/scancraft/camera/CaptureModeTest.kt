package com.krinry.scancraft.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 & Tier 2: Tests for camera capture modes.
 */
class CaptureModeTest {

    @Test
    fun testAllCaptureModesExist() {
        val modes = CaptureMode.values()
        assertEquals(5, modes.size)
        assertTrue(modes.contains(CaptureMode.DOCUMENT))
        assertTrue(modes.contains(CaptureMode.ID_CARD))
        assertTrue(modes.contains(CaptureMode.QR_CODE))
        assertTrue(modes.contains(CaptureMode.BOOK))
        assertTrue(modes.contains(CaptureMode.TO_TEXT))
    }

    @Test
    fun testCaptureModeTitlesAndDescriptions() {
        assertEquals("Document", CaptureMode.DOCUMENT.title)
        assertEquals("Continuous scanning for single or multi-page documents", CaptureMode.DOCUMENT.description)

        assertEquals("ID Card", CaptureMode.ID_CARD.title)
        assertEquals("Front & Back card sides combined onto one page", CaptureMode.ID_CARD.description)

        assertEquals("QR Code", CaptureMode.QR_CODE.title)
        assertEquals("Fast QR and Barcode scanner", CaptureMode.QR_CODE.description)

        assertEquals("Book", CaptureMode.BOOK.title)
        assertEquals("Two-page spread with curve correction", CaptureMode.BOOK.description)

        assertEquals("To Text", CaptureMode.TO_TEXT.title)
        assertEquals("Instant offline OCR text extraction", CaptureMode.TO_TEXT.description)
    }

    @Test
    fun testCaptureModeValueOf() {
        assertEquals(CaptureMode.DOCUMENT, CaptureMode.valueOf("DOCUMENT"))
        assertEquals(CaptureMode.ID_CARD, CaptureMode.valueOf("ID_CARD"))
        assertEquals(CaptureMode.QR_CODE, CaptureMode.valueOf("QR_CODE"))
        assertEquals(CaptureMode.BOOK, CaptureMode.valueOf("BOOK"))
        assertEquals(CaptureMode.TO_TEXT, CaptureMode.valueOf("TO_TEXT"))
    }
}

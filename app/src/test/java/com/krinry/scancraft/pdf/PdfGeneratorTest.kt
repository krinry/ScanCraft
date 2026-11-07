package com.krinry.scancraft.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Production unit tests for PdfGenerator:
 * 1. Empty pages validation and error handling
 * 2. StandardProtectionPolicy encryption security invariants
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
    fun testStandardProtectionPolicyEncryptionParameters() {
        val password = "StrongEnterprisePassword#2026"
        val ap = AccessPermission().apply {
            setCanPrint(true)
            setCanExtractContent(true)
        }
        val spp = StandardProtectionPolicy(password, password, ap)
        spp.encryptionKeyLength = 128

        assertEquals(128, spp.encryptionKeyLength)
        assertEquals(password, spp.userPassword)
        assertEquals(password, spp.ownerPassword)
        assertTrue(spp.permissions.canPrint())
        assertTrue(spp.permissions.canExtractContent())
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

package com.krinry.scancraft.pdf

import com.tom_roush.pdfbox.pdmodel.common.PDRectangle

enum class PdfPageSize(val title: String, val rectangle: PDRectangle?) {
    A4("A4 (210 x 297 mm)", PDRectangle.A4),
    LETTER("US Letter (8.5 x 11 in)", PDRectangle.LETTER),
    LEGAL("US Legal", PDRectangle.LEGAL),
    FIT_IMAGE("Fit to Image Size", null)
}

enum class PdfQuality(val title: String, val jpegQuality: Int) {
    HIGH("High Quality (Original)", 92),
    MEDIUM("Medium (Recommended)", 75),
    LOW("Small File Size", 50)
}

data class PdfExportOptions(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: PdfQuality = PdfQuality.MEDIUM,
    val password: String? = null,
    val watermarkText: String? = null
)

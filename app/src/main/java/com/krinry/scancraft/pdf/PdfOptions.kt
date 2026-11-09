package com.krinry.scancraft.pdf

/**
 * Standard PDF document dimensions in PostScript points (72 points/inch).
 */
data class PdfDimension(val widthPoints: Int, val heightPoints: Int)

enum class PdfPageSize(val title: String, val dimension: PdfDimension?) {
    A4("A4 (210 x 297 mm)", PdfDimension(595, 842)),
    LETTER("US Letter (8.5 x 11 in)", PdfDimension(612, 792)),
    LEGAL("US Legal (8.5 x 14 in)", PdfDimension(612, 1008)),
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

package com.krinry.scancraft.camera

enum class CaptureMode(val title: String, val description: String) {
    BOOK("Book", "Two-page spread with curve correction"),
    TO_TEXT("To Text", "Instant offline OCR text extraction"),
    DOCUMENT("Document", "Continuous scanning for single or multi-page documents"),
    ID_CARD("ID Card", "Front & Back card sides combined onto one page"),
    QR_CODE("QR Code", "Fast QR and Barcode scanner");

    val displayTitle: String
        get() = if (this == DOCUMENT) "Docs" else title
}

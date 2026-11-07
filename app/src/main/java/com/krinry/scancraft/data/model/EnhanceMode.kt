package com.krinry.scancraft.data.model

enum class EnhanceMode(val id: Int, val displayName: String, val shortCode: String) {
    ORIGINAL(0, "Original", "ORIG"),
    MAGIC_COLOR(1, "Magic Color", "MAGIC"),
    NO_SHADOW(6, "No Shadow", "SHADOW"),
    LIGHTEN(5, "Lighten", "LIGHT"),
    DEBLUR(7, "Deblur", "SHARP"),
    COLOR_DOC(8, "Vivid", "VIVID"),
    BW(2, "B&W", "B&W"),
    BW2(3, "B&W 2", "B&W 2"),
    GRAYSCALE(4, "Grayscale", "GRAY"),
    BLUEPRINT(9, "Blueprint", "DARK"),
    ECO_TEXT(10, "Eco Text", "ECO"),
    STAMP(11, "Stamp", "SEAL"),
    WARM(12, "Warm Paper", "WARM");

    companion object {
        fun fromId(id: Int): EnhanceMode = entries.firstOrNull { it.id == id } ?: MAGIC_COLOR
    }
}

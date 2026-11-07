package com.krinry.scancraft.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdTime: Long = System.currentTimeMillis(),
    val modifiedTime: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val tags: String = "",
    val folderId: Long = -1L
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("documentId")]
)
data class Page(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val processedImagePath: String,
    val originalImagePath: String,
    val thumbnailPath: String = "",
    val cropBorderJson: String = "", // JSON representation of 4-points
    val enhanceMode: Int = 1,        // Default Magic Color
    val rotationDegrees: Int = 0,    // 0, 90, 180, 270
    val brightness: Int = 0,
    val contrast: Int = 0,
    val ocrText: String = "",
    val watermarkText: String = "",
    val annotationsJson: String = "",
    val createdTime: Long = System.currentTimeMillis()
)

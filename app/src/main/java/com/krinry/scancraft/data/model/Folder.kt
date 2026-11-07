package com.krinry.scancraft.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index("parentFolderId")
    ]
)
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdTime: Long = System.currentTimeMillis(),
    val color: String = "#EEAA3B",
    val orderIndex: Int = 0,
    val parentFolderId: Long = -1L,
    val path: String = "/"
)

data class FolderWithCount(
    val folder: Folder,
    val docCount: Int
)

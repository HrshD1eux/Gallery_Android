package com.HrshD1eux.Gallery.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_metadata",
    indices = [
        androidx.room.Index(value = ["isFavorite"]),
        androidx.room.Index(value = ["isHidden"]),
        androidx.room.Index(value = ["isTrashed"]),
        androidx.room.Index(value = ["dateTaken"]),
        androidx.room.Index(value = ["bucketId"])
    ]
)
data class MediaMetadataEntity(
    @PrimaryKey val mediaId: Long,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isTrashed: Boolean = false,
    val trashTime: Long = 0L,
    val originalPath: String = "",
    val vaultPath: String = "",
    val mimeType: String = "",
    val dateTaken: Long = 0L,
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: Long = 0L,
    val bucketName: String = "",
    val durationMs: Long = 0L
)

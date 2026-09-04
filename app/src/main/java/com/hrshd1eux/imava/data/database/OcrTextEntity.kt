package com.hrshd1eux.imava.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_ocr")
data class OcrTextEntity(
    @PrimaryKey val mediaId: Long,
    val ocrText: String
)

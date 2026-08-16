package com.HrshD1eux.Gallery.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MediaMetadataEntity::class], version = 3, exportSchema = true)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao
}

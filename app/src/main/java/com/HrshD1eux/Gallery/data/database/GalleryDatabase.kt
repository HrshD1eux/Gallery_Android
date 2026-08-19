package com.HrshD1eux.Gallery.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.HrshD1eux.Gallery.core.di.DatabaseModule

@Database(entities = [MediaMetadataEntity::class], version = 3, exportSchema = true)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "private_gallery.db"
                )
                .addMigrations(DatabaseModule.MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}

package com.hrshd1eux.imava.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hrshd1eux.imava.core.di.DatabaseModule

@Database(entities = [MediaMetadataEntity::class, OcrTextEntity::class], version = 5, exportSchema = true)
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
                .addMigrations(DatabaseModule.MIGRATION_2_3, DatabaseModule.MIGRATION_3_4, DatabaseModule.MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}

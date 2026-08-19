package com.HrshD1eux.Gallery.core.di

import android.content.Context
import androidx.room.Room
import com.HrshD1eux.Gallery.data.database.GalleryDatabase
import com.HrshD1eux.Gallery.data.database.MetadataDao
import com.HrshD1eux.Gallery.data.repository.MediaRepository
import com.HrshD1eux.Gallery.data.repository.MediaRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_isFavorite ON media_metadata(isFavorite)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_isHidden ON media_metadata(isHidden)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_isTrashed ON media_metadata(isTrashed)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_dateTaken ON media_metadata(dateTaken)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_bucketId ON media_metadata(bucketId)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GalleryDatabase {
        return GalleryDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMetadataDao(db: GalleryDatabase): MetadataDao {
        return db.metadataDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    @Singleton
    fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository
}

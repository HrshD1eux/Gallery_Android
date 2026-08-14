package com.HrshD1eux.Gallery.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.HrshD1eux.Gallery.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    fun observeMediaStore(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // Observe external file modifications
        contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            observer
        )
        
        // Emit initial value to fetch baseline
        trySend(Unit)
        
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    suspend fun fetchMedia(
        limit: Int,
        offset: Int,
        bucketId: Long? = null,
        includeTrashed: Boolean = false
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        val collection = MediaStore.Files.getContentUri("external")
        
        val projectionList = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projectionList.add("is_trashed")
        }
        val projection = projectionList.toTypedArray()

        val selection = if (bucketId != null) {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
        } else {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        }

        val selectionArgs = if (bucketId != null) {
            arrayOf(bucketId.toString())
        } else {
            null
        }

        val queryArgs = Bundle().apply {
            if (limit > 0) {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Files.FileColumns.DATE_TAKEN, MediaStore.Files.FileColumns.DATE_ADDED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (includeTrashed) MediaStore.MATCH_INCLUDE else MediaStore.MATCH_DEFAULT
                )
            }
        }

        val cursor = try {
            contentResolver.query(collection, projection, queryArgs, null)
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val addedCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val widthCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val mediaTypeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val path = it.getString(dataCol) ?: ""
                val mimeType = it.getString(mimeCol) ?: "image/jpeg"
                
                // Fallback to DATE_ADDED * 1000 (DATE_ADDED is in seconds) if DATE_TAKEN is missing
                var dateTaken = it.getLong(dateCol)
                if (dateTaken <= 0) {
                    dateTaken = it.getLong(addedCol) * 1000
                }
                
                val size = it.getLong(sizeCol)
                val width = it.getInt(widthCol)
                val height = it.getInt(heightCol)
                val bucketIdVal = it.getLong(bucketIdCol)
                val bucketName = it.getString(bucketNameCol) ?: "Unknown"
                val mediaType = it.getInt(mediaTypeCol)

                val isTrashedSystem = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val trashedCol = it.getColumnIndex("is_trashed")
                    if (trashedCol != -1) it.getInt(trashedCol) == 1 else false
                } else {
                    false
                }

                val uri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }

                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    val duration = it.getLong(durCol)
                    mediaList.add(
                        MediaItem.Video(
                            id = id,
                            uri = uri,
                            path = path,
                            mimeType = mimeType,
                            dateTaken = dateTaken,
                            size = size,
                            width = width,
                            height = height,
                            durationMs = duration,
                            bucketId = bucketIdVal,
                            bucketName = bucketName,
                            isTrashed = isTrashedSystem
                        )
                    )
                } else {
                    mediaList.add(
                        MediaItem.Photo(
                            id = id,
                            uri = uri,
                            path = path,
                            mimeType = mimeType,
                            dateTaken = dateTaken,
                            size = size,
                            width = width,
                            height = height,
                            bucketId = bucketIdVal,
                            bucketName = bucketName,
                            isTrashed = isTrashedSystem
                        )
                    )
                }
            }
        }
        mediaList
    }

    /**
     * Fetches only media IDs from MediaStore. Lightweight — no full MediaItem allocation.
     * Used for orphan cleanup where we only need to know which IDs are still active.
     */
    suspend fun fetchMediaIds(): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val collection = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

        val cursor = try {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID),
                selection,
                null,
                null
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (it.moveToNext()) {
                ids.add(it.getLong(idCol))
            }
        }
        ids
    }

    /**
     * Fetches full MediaItem objects for a specific set of IDs.
     * Uses _ID IN (...) query — efficient for small sets (favorites, trashed).
     * Chunks large ID sets into batches of 500 to stay within SQLite variable limits.
     */
    suspend fun fetchMediaByIds(ids: Set<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        
        val mediaList = mutableListOf<MediaItem>()
        val collection = MediaStore.Files.getContentUri("external")
        
        val projectionList = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projectionList.add("is_trashed")
        }
        val projection = projectionList.toTypedArray()

        // Chunk IDs to stay within SQLite variable limits
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders) AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()

            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Files.FileColumns.DATE_TAKEN, MediaStore.Files.FileColumns.DATE_ADDED)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    putInt(
                        MediaStore.QUERY_ARG_MATCH_TRASHED,
                        MediaStore.MATCH_INCLUDE
                    )
                }
            }

            val cursor = try {
                contentResolver.query(collection, projection, queryArgs, null)
            } catch (e: Exception) {
                null
            }

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val addedCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val widthCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val mediaTypeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val path = it.getString(dataCol) ?: ""
                    val mimeType = it.getString(mimeCol) ?: "image/jpeg"
                    var dateTaken = it.getLong(dateCol)
                    if (dateTaken <= 0) {
                        dateTaken = it.getLong(addedCol) * 1000
                    }
                    val size = it.getLong(sizeCol)
                    val width = it.getInt(widthCol)
                    val height = it.getInt(heightCol)
                    val bucketIdVal = it.getLong(bucketIdCol)
                    val bucketName = it.getString(bucketNameCol) ?: "Unknown"
                    val mediaType = it.getInt(mediaTypeCol)

                    val isTrashedSystem = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val trashedCol = it.getColumnIndex("is_trashed")
                        if (trashedCol != -1) it.getInt(trashedCol) == 1 else false
                    } else {
                        false
                    }

                    val uri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        val duration = it.getLong(durCol)
                        mediaList.add(
                            MediaItem.Video(
                                id = id, uri = uri, path = path, mimeType = mimeType,
                                dateTaken = dateTaken, size = size, width = width, height = height,
                                durationMs = duration, bucketId = bucketIdVal, bucketName = bucketName,
                                isTrashed = isTrashedSystem
                            )
                        )
                    } else {
                        mediaList.add(
                            MediaItem.Photo(
                                id = id, uri = uri, path = path, mimeType = mimeType,
                                dateTaken = dateTaken, size = size, width = width, height = height,
                                bucketId = bucketIdVal, bucketName = bucketName,
                                isTrashed = isTrashedSystem
                            )
                        )
                    }
                }
            }
        }
        mediaList
    }

    suspend fun fetchBuckets(): List<BucketInfo> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            "COUNT(*) AS bucket_count"
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) GROUP BY (${MediaStore.Files.FileColumns.BUCKET_ID})"

        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
        }

        val bucketCounts = mutableMapOf<Long, Int>()
        val bucketNames = mutableMapOf<Long, String>()

        val cursor = try {
            contentResolver.query(collection, projection, queryArgs, null)
        } catch (e: Exception) {
            // Fallback query for OEM platforms restricting raw GROUP BY in selection
            try {
                val fallbackSelection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
                contentResolver.query(
                    collection,
                    arrayOf(
                        MediaStore.Files.FileColumns.BUCKET_ID,
                        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
                    ),
                    fallbackSelection,
                    null,
                    null
                )
            } catch (ex: Exception) {
                null
            }
        }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val nameCol = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val countCol = it.getColumnIndex("bucket_count")

            if (idCol != -1 && nameCol != -1) {
                while (it.moveToNext()) {
                    val bucketId = it.getLong(idCol)
                    val bucketName = it.getString(nameCol) ?: "Unknown"
                    if (countCol != -1) {
                        bucketCounts[bucketId] = it.getInt(countCol)
                    } else {
                        bucketCounts[bucketId] = (bucketCounts[bucketId] ?: 0) + 1
                    }
                    bucketNames[bucketId] = bucketName
                }
            }
        }

        val buckets = mutableListOf<BucketInfo>()
        bucketCounts.forEach { (id, count) ->
            buckets.add(BucketInfo(id, bucketNames[id] ?: "Unknown", count))
        }
        buckets.sortedByDescending { it.count }
    }
}

data class BucketInfo(
    val id: Long,
    val name: String,
    val count: Int
)

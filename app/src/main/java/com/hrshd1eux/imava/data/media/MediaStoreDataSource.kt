package com.hrshd1eux.imava.data.media

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.hrshd1eux.imava.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val QUERY_ARG_MATCH_NOMEDIA = "android:query-arg-match-nomedia"

enum class MediaTypeFilter {
    ALL,
    IMAGES,
    VIDEOS
}

class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    fun observeMediaStore(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        try {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (_: Exception) {}
        try {
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (_: Exception) {}
        try {
            contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        } catch (_: Exception) {}

        trySend(Unit)

        awaitClose {
            try {
                contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {}
        }
    }

    suspend fun getTotalMediaCount(
        bucketId: Long? = null,
        mediaType: MediaTypeFilter = MediaTypeFilter.ALL
    ): Int = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val mediaTypeCondition = when (mediaType) {
            MediaTypeFilter.ALL -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            MediaTypeFilter.IMAGES -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            MediaTypeFilter.VIDEOS -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
        }
        val selection = if (bucketId != null) {
            "$mediaTypeCondition AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
        } else {
            mediaTypeCondition
        }
        val selectionArgs = if (bucketId != null) arrayOf(bucketId.toString()) else null

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                contentResolver.query(collection, projection, selection, selectionArgs, null)
            }
            cursor?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun fetchMedia(
        limit: Int,
        offset: Int,
        bucketId: Long? = null,
        includeTrashed: Boolean = false,
        isAscending: Boolean = false,
        mediaType: MediaTypeFilter = MediaTypeFilter.ALL
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add("is_trashed")
        }
        val projection = projectionList.toTypedArray()

        val mediaTypeCondition = when (mediaType) {
            MediaTypeFilter.ALL -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            MediaTypeFilter.IMAGES -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            MediaTypeFilter.VIDEOS -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
        }
        val selection = if (bucketId != null) {
            "$mediaTypeCondition AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
        } else {
            mediaTypeCondition
        }

        val selectionArgs = if (bucketId != null) {
            arrayOf(bucketId.toString())
        } else {
            null
        }

        val cursor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    if (limit > 0) {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                    }
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.DATE_TAKEN)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        if (isAscending) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(
                        MediaStore.QUERY_ARG_MATCH_TRASHED,
                        if (includeTrashed) MediaStore.MATCH_INCLUDE else MediaStore.MATCH_DEFAULT
                    )
                    putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                val dir = if (isAscending) "ASC" else "DESC"
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} $dir, ${MediaStore.Files.FileColumns.DATE_TAKEN} $dir${if (limit > 0) " LIMIT $limit OFFSET $offset" else ""}"
                contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            }
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val indices = MediaCursorIndices(it)
            while (it.moveToNext()) {
                mediaList.add(it.extractMediaItem(indices))
            }
        }
        mediaList
    }

    suspend fun fetchTrashedMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext emptyList()
        }
        val mediaList = mutableListOf<MediaItem>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
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
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            "is_trashed"
        )

        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Files.FileColumns.DATE_ADDED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
        }

        val cursor = try {
            contentResolver.query(collection, projection, queryArgs, null)
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val indices = MediaCursorIndices(it)
            while (it.moveToNext()) {
                mediaList.add(it.extractMediaItem(indices))
            }
        }
        mediaList
    }

    suspend fun fetchActiveMediaIds(): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

        val cursor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Files.FileColumns.DATE_ADDED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                contentResolver.query(collection, projection, selection, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")
            }
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
            if (idCol != -1) {
                while (it.moveToNext()) {
                    ids.add(it.getLong(idCol))
                }
            }
        }
        ids
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add("is_trashed")
        }
        val projection = projectionList.toTypedArray()

        // Chunk IDs to stay well within SQLite variable limits
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders) AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()

            val cursor = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                    }
                    contentResolver.query(collection, projection, queryArgs, null)
                } else {
                    val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                    contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
                }
            } catch (e: Exception) {
                null
            }

            cursor?.use {
                val indices = MediaCursorIndices(it)
                while (it.moveToNext()) {
                    mediaList.add(it.extractMediaItem(indices))
                }
            }
        }
        mediaList
    }

    suspend fun fetchBuckets(): List<BucketInfo> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

        val bucketCounts = mutableMapOf<Long, Int>()
        val bucketNames = mutableMapOf<Long, String>()

        val cursor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                contentResolver.query(collection, projection, selection, null, null)
            }
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val nameCol = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            if (idCol != -1 && nameCol != -1) {
                while (it.moveToNext()) {
                    val bucketId = it.getLong(idCol)
                    val bucketName = it.getString(nameCol) ?: "Unknown"
                    bucketCounts[bucketId] = (bucketCounts[bucketId] ?: 0) + 1
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

    suspend fun searchMedia(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add("is_trashed")
        }
        val projection = projectionList.toTypedArray()

        val cleanQuery = "%${query.trim()}%"
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND (${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?)"
        val selectionArgs = arrayOf(cleanQuery, cleanQuery, cleanQuery)

        val cursor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_DEFAULT)
                    putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            }
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val indices = MediaCursorIndices(it)
            while (it.moveToNext()) {
                mediaList.add(it.extractMediaItem(indices))
            }
        }
        mediaList
    }

    suspend fun scanSecondaryMediaDirectories(): Int = withContext(Dispatchers.IO) {
        var scannedCount = 0
        try {
            val externalStorage = android.os.Environment.getExternalStorageDirectory() ?: return@withContext 0
            val validExtensions = setOf(
                "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng",
                "mp4", "mkv", "mov", "avi", "webm", "3gp", "ts", "flv", "m4v"
            )

            val secondaryPaths = listOf(
                java.io.File(externalStorage, "Android/media/com.whatsapp/WhatsApp/Media"),
                java.io.File(externalStorage, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"),
                java.io.File(externalStorage, "Android/media/org.telegram.messenger/Telegram"),
                java.io.File(externalStorage, "Android/media/org.telegram.messenger.web/Telegram"),
                java.io.File(externalStorage, "WhatsApp/Media"),
                java.io.File(externalStorage, "Telegram")
            )

            val unindexedFiles = mutableListOf<String>()
            secondaryPaths.filter { it.exists() && it.isDirectory }.forEach { dir ->
                dir.walkTopDown().maxDepth(4).take(200).forEach { file ->
                    if (file.isFile && validExtensions.contains(file.extension.lowercase(java.util.Locale.getDefault()))) {
                        unindexedFiles.add(file.absolutePath)
                    }
                }
            }

            if (unindexedFiles.isNotEmpty()) {
                scannedCount = unindexedFiles.size
                unindexedFiles.chunked(500).forEach { chunk ->
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        chunk.toTypedArray(),
                        null,
                        null
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scannedCount
    }

    suspend fun getDatePositionIndex(bucketId: Long? = null, isAscending: Boolean = false): List<com.hrshd1eux.imava.data.repository.DatePositionHeader> = withContext(Dispatchers.IO) {
        val result = mutableListOf<com.hrshd1eux.imava.data.repository.DatePositionHeader>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection = if (bucketId != null) {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
        } else {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        }
        val selectionArgs = if (bucketId != null) arrayOf(bucketId.toString()) else null

        val cursor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.DATE_TAKEN)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        if (isAscending) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(QUERY_ARG_MATCH_NOMEDIA, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(collection, projection, queryArgs, null)
            } else {
                val dir = if (isAscending) "ASC" else "DESC"
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} $dir, ${MediaStore.Files.FileColumns.DATE_TAKEN} $dir"
                contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            }
        } catch (e: Exception) {
            null
        }

        val zoneId = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val sameYearFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault())
        val otherYearFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", java.util.Locale.getDefault())

        fun getHeaderTitle(dateMs: Long): String {
            val ms = if (dateMs > 0) dateMs else System.currentTimeMillis()
            val localDate = java.time.Instant.ofEpochMilli(ms).atZone(zoneId).toLocalDate()
            return when (localDate) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> if (localDate.year == today.year) {
                    localDate.format(sameYearFormatter)
                } else {
                    localDate.format(otherYearFormatter)
                }
            }
        }

        cursor?.use {
            val count = it.count
            if (count == 0) return@withContext emptyList()

            val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
            val addedCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
            var currentHeader = ""

            val sampleStep = (count / 50).coerceAtLeast(1)
            var pos = 0
            while (pos < count) {
                if (it.moveToPosition(pos)) {
                    val rawDateTaken = if (dateCol != -1) it.getLong(dateCol) else 0L
                    val addedSecs = if (addedCol != -1) it.getLong(addedCol) else 0L
                    val addedMs = if (addedSecs > 0) addedSecs * 1000L else 0L
                    val dateTaken = if (rawDateTaken > 100000000000L) rawDateTaken else addedMs

                    val headerTitle = getHeaderTitle(dateTaken)
                    if (headerTitle != currentHeader) {
                        currentHeader = headerTitle
                        result.add(com.hrshd1eux.imava.data.repository.DatePositionHeader(headerTitle, pos))
                    }
                }
                pos += sampleStep
            }
        }
        result
    }
}

data class BucketInfo(
    val id: Long,
    val name: String,
    val count: Int
)

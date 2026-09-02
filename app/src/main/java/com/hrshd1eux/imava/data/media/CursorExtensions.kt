package com.hrshd1eux.imava.data.media

import android.content.ContentUris
import android.database.Cursor
import android.provider.MediaStore
import com.hrshd1eux.imava.data.model.MediaItem

class MediaCursorIndices(cursor: Cursor) {
    val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
    val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
    val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
    val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
    val addedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
    val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
    val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
    val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
    val durCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
    val bucketIdCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
    val bucketNameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
    val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
    val trashedCol = cursor.getColumnIndex("is_trashed")
    val expiresCol = cursor.getColumnIndex("date_expires")
}

fun Cursor.extractMediaItem(indices: MediaCursorIndices): MediaItem {
    val id = if (indices.idCol != -1) getLong(indices.idCol) else 0L
    val path = if (indices.dataCol != -1) (getString(indices.dataCol) ?: "") else ""
    val mimeType = if (indices.mimeCol != -1) (getString(indices.mimeCol) ?: "image/jpeg") else "image/jpeg"

    val rawDateTaken = if (indices.dateCol != -1) getLong(indices.dateCol) else 0L
    val addedSecs = if (indices.addedCol != -1) getLong(indices.addedCol) else 0L
    val addedMs = if (addedSecs > 0) addedSecs * 1000L else 0L
    val dateTaken = if (rawDateTaken > 100000000000L) rawDateTaken else if (addedMs > 0) addedMs else System.currentTimeMillis()

    val size = if (indices.sizeCol != -1) getLong(indices.sizeCol) else 0L
    val width = if (indices.widthCol != -1) getInt(indices.widthCol) else 0
    val height = if (indices.heightCol != -1) getInt(indices.heightCol) else 0
    val bucketIdVal = if (indices.bucketIdCol != -1) getLong(indices.bucketIdCol) else 0L
    val bucketName = if (indices.bucketNameCol != -1) (getString(indices.bucketNameCol) ?: "Unknown") else "Unknown"
    val mediaType = if (indices.mediaTypeCol != -1) getInt(indices.mediaTypeCol) else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE

    val isTrashedSystem = if (indices.trashedCol != -1) getInt(indices.trashedCol) == 1 else false
    val expiresSec = if (indices.expiresCol != -1) getLong(indices.expiresCol) else 0L
    val expiresMs = expiresSec * 1000L
    val computedTrashTime = if (expiresMs > 0L) {
        (expiresMs - 30L * 24L * 60L * 60L * 1000L).coerceAtLeast(0L)
    } else if (isTrashedSystem) {
        System.currentTimeMillis()
    } else {
        0L
    }

    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO || mimeType.startsWith("video/")
    val uri = if (isVideo) {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    } else {
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    return if (isVideo) {
        val duration = if (indices.durCol != -1) getLong(indices.durCol) else 0L
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
            isTrashed = isTrashedSystem,
            trashTime = computedTrashTime
        )
    } else {
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
            isTrashed = isTrashedSystem,
            trashTime = computedTrashTime
        )
    }
}

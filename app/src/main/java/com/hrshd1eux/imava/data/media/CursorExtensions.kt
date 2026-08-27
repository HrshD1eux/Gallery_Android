package com.hrshd1eux.imava.data.media

import android.content.ContentUris
import android.database.Cursor
import android.provider.MediaStore
import com.hrshd1eux.imava.data.model.MediaItem

class MediaCursorIndices(cursor: Cursor) {
    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
    val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
    val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
    val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
    val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
    val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
    val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
    val trashedCol = cursor.getColumnIndex("is_trashed")
}

fun Cursor.extractMediaItem(indices: MediaCursorIndices): MediaItem {
    val id = getLong(indices.idCol)
    val path = getString(indices.dataCol) ?: ""
    val mimeType = getString(indices.mimeCol) ?: "image/jpeg"

    val rawDateTaken = getLong(indices.dateCol)
    val addedSecs = getLong(indices.addedCol)
    val addedMs = if (addedSecs > 0) addedSecs * 1000L else 0L
    val dateTaken = if (rawDateTaken > 100000000000L) rawDateTaken else addedMs

    val size = getLong(indices.sizeCol)
    val width = getInt(indices.widthCol)
    val height = getInt(indices.heightCol)
    val bucketIdVal = getLong(indices.bucketIdCol)
    val bucketName = getString(indices.bucketNameCol) ?: "Unknown"
    val mediaType = getInt(indices.mediaTypeCol)

    val isTrashedSystem = if (indices.trashedCol != -1) getInt(indices.trashedCol) == 1 else false

    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
    val uri = if (isVideo) {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    } else {
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    return if (isVideo) {
        val duration = getLong(indices.durCol)
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
            isTrashed = isTrashedSystem
        )
    }
}

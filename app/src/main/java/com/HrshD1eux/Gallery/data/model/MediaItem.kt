package com.HrshD1eux.Gallery.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
sealed interface MediaItem {
    val id: Long
    val uri: Uri
    val path: String
    val mimeType: String
    val dateTaken: Long
    val size: Long
    val width: Int
    val height: Int
    val isFavorite: Boolean
    val isHidden: Boolean
    val isTrashed: Boolean
    val bucketId: Long
    val bucketName: String

    @Immutable
    data class Photo(
        override val id: Long,
        override val uri: Uri,
        override val path: String,
        override val mimeType: String,
        override val dateTaken: Long,
        override val size: Long,
        override val width: Int,
        override val height: Int,
        override val isFavorite: Boolean = false,
        override val isHidden: Boolean = false,
        override val isTrashed: Boolean = false,
        override val bucketId: Long,
        override val bucketName: String
    ) : MediaItem

    @Immutable
    data class Video(
        override val id: Long,
        override val uri: Uri,
        override val path: String,
        override val mimeType: String,
        override val dateTaken: Long,
        override val size: Long,
        override val width: Int,
        override val height: Int,
        val durationMs: Long,
        override val isFavorite: Boolean = false,
        override val isHidden: Boolean = false,
        override val isTrashed: Boolean = false,
        override val bucketId: Long,
        override val bucketName: String
    ) : MediaItem
}

val MediaItem.isVideo: Boolean
    get() = this is MediaItem.Video

val MediaItem.formattedDuration: String
    get() = if (this is MediaItem.Video) {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        String.format("%d:%02d", minutes, remainingSeconds)
    } else {
        ""
    }

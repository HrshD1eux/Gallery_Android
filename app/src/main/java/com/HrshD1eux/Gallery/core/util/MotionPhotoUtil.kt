package com.HrshD1eux.Gallery.core.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

data class MotionPhotoInfo(
    val isMotionPhoto: Boolean,
    val videoOffsetFromEnd: Long = 0L,
    val videoLength: Long = 0L
)

object MotionPhotoUtil {

    private val MP4_FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())

    /**
     * Checks whether the given image is a Motion Photo (Google Pixel, Samsung, etc.).
     */
    suspend fun checkMotionPhoto(context: Context, uri: Uri): MotionPhotoInfo = withContext(Dispatchers.IO) {
        try {
            // 1. Check EXIF / XMP for GCamera:MicroVideo
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val isMicroVideo = exif.getAttribute("GCamera:MicroVideo") == "1" ||
                        exif.getAttribute("MicroVideo") == "1"
                val offsetStr = exif.getAttribute("GCamera:MicroVideoOffset")
                    ?: exif.getAttribute("MicroVideoOffset")

                if (isMicroVideo && offsetStr != null) {
                    val offset = offsetStr.toLongOrNull() ?: 0L
                    if (offset > 0) {
                        return@withContext MotionPhotoInfo(isMotionPhoto = true, videoOffsetFromEnd = offset)
                    }
                }
            }

            // 2. Fallback: Binary scan for embedded MP4 ftyp marker from the end of the file
            val fileLength = getStreamLength(context, uri)
            if (fileLength > 64 * 1024) { // Only check files > 64 KB
                val offsetFromEnd = scanForMp4FromEnd(context, uri, fileLength)
                if (offsetFromEnd > 0) {
                    return@withContext MotionPhotoInfo(
                        isMotionPhoto = true,
                        videoOffsetFromEnd = offsetFromEnd,
                        videoLength = offsetFromEnd
                    )
                }
            }

            MotionPhotoInfo(isMotionPhoto = false)
        } catch (e: Exception) {
            MotionPhotoInfo(isMotionPhoto = false)
        }
    }

    /**
     * Extracts the embedded MP4 video from a Motion Photo into a cache file for playback.
     */
    suspend fun extractMotionVideo(context: Context, uri: Uri, info: MotionPhotoInfo): File? = withContext(Dispatchers.IO) {
        if (!info.isMotionPhoto) return@withContext null

        val cacheDir = File(context.cacheDir, "motion_photos").apply { mkdirs() }
        val targetFile = File(cacheDir, "motion_${uri.hashCode()}_${info.videoOffsetFromEnd}.mp4")
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        try {
            val totalLength = getStreamLength(context, uri)
            if (totalLength <= info.videoOffsetFromEnd) return@withContext null

            val startByte = totalLength - info.videoOffsetFromEnd
            context.contentResolver.openInputStream(uri)?.use { input ->
                skipFully(input, startByte)
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.length() > 1024) {
                targetFile
            } else {
                targetFile.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            targetFile.delete()
            null
        }
    }

    private fun getStreamLength(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun scanForMp4FromEnd(context: Context, uri: Uri, totalLength: Long): Long {
        // Read the last 30 MB maximum to find the MP4 signature
        val scanSize = minOf(totalLength, 30L * 1024L * 1024L).toInt()
        val buffer = ByteArray(scanSize)
        val startOffset = totalLength - scanSize

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                skipFully(input, startOffset)
                var bytesRead = 0
                while (bytesRead < scanSize) {
                    val read = input.read(buffer, bytesRead, scanSize - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }
            }

            // Search for "ftyp" marker in buffer
            for (i in 4 until buffer.size - 4) {
                if (buffer[i] == MP4_FTYP[0] &&
                    buffer[i + 1] == MP4_FTYP[1] &&
                    buffer[i + 2] == MP4_FTYP[2] &&
                    buffer[i + 3] == MP4_FTYP[3]
                ) {
                    // MP4 atom starts 4 bytes before 'ftyp' (length prefix)
                    val mp4StartInBuffer = i - 4
                    val videoLength = scanSize - mp4StartInBuffer
                    if (videoLength > 4096) {
                        return videoLength.toLong()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }
}

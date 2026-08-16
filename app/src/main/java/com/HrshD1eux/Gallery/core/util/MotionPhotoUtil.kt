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
            val extracted = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    java.io.FileInputStream(pfd.fileDescriptor).channel.use { inChannel ->
                        inChannel.position(startByte)
                        FileOutputStream(targetFile).channel.use { outChannel ->
                            outChannel.transferFrom(inChannel, 0, info.videoOffsetFromEnd)
                        }
                    }
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }

            if (!extracted) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    skipFully(input, startByte)
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
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
        // Scan at most 30 MB backwards in 64 KB streaming chunks to eliminate large heap spikes
        val maxScanSize = minOf(totalLength, 30L * 1024L * 1024L)
        val startOffset = totalLength - maxScanSize
        val chunkSize = 64 * 1024
        val buffer = ByteArray(chunkSize)

        try {
            val scannedLength = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                        var currentPos = startOffset
                        var overlap = ByteArray(0)
                        var foundLength = 0L

                        while (currentPos < totalLength) {
                            channel.position(currentPos)
                            val toRead = minOf(chunkSize.toLong(), totalLength - currentPos).toInt()
                            val byteBuf = java.nio.ByteBuffer.wrap(buffer, 0, toRead)
                            val bytesRead = channel.read(byteBuf)
                            if (bytesRead <= 0) break

                            val combinedSize = overlap.size + bytesRead
                            val combined = if (overlap.isNotEmpty()) {
                                val arr = ByteArray(combinedSize)
                                System.arraycopy(overlap, 0, arr, 0, overlap.size)
                                System.arraycopy(buffer, 0, arr, overlap.size, bytesRead)
                                arr
                            } else {
                                buffer
                            }

                            for (i in 4 until combinedSize - 4) {
                                if (combined[i] == MP4_FTYP[0] &&
                                    combined[i + 1] == MP4_FTYP[1] &&
                                    combined[i + 2] == MP4_FTYP[2] &&
                                    combined[i + 3] == MP4_FTYP[3]
                                ) {
                                    val offsetInBlock = if (overlap.isNotEmpty()) i - overlap.size else i
                                    val atomStartGlobal = currentPos + offsetInBlock - 4
                                    val videoLength = totalLength - atomStartGlobal
                                    if (videoLength in 4096..maxScanSize) {
                                        foundLength = videoLength
                                        break
                                    }
                                }
                            }
                            if (foundLength > 0L) break

                            val overlapSize = minOf(3, bytesRead)
                            overlap = ByteArray(overlapSize)
                            System.arraycopy(buffer, bytesRead - overlapSize, overlap, 0, overlapSize)

                            currentPos += bytesRead
                        }
                        foundLength
                    }
                } ?: 0L
            } catch (_: Exception) {
                0L
            }

            if (scannedLength > 0L) return scannedLength

            // Fallback stream scanner using 64 KB chunking
            context.contentResolver.openInputStream(uri)?.use { input ->
                skipFully(input, startOffset)
                var currentPos = startOffset
                var overlap = ByteArray(0)

                while (currentPos < totalLength) {
                    val toRead = minOf(chunkSize.toLong(), totalLength - currentPos).toInt()
                    val bytesRead = input.read(buffer, 0, toRead)
                    if (bytesRead <= 0) break

                    val combinedSize = overlap.size + bytesRead
                    val combined = if (overlap.isNotEmpty()) {
                        val arr = ByteArray(combinedSize)
                        System.arraycopy(overlap, 0, arr, 0, overlap.size)
                        System.arraycopy(buffer, 0, arr, overlap.size, bytesRead)
                        arr
                    } else {
                        buffer
                    }

                    for (i in 4 until combinedSize - 4) {
                        if (combined[i] == MP4_FTYP[0] &&
                            combined[i + 1] == MP4_FTYP[1] &&
                            combined[i + 2] == MP4_FTYP[2] &&
                            combined[i + 3] == MP4_FTYP[3]
                        ) {
                            val offsetInBlock = if (overlap.isNotEmpty()) i - overlap.size else i
                            val atomStartGlobal = currentPos + offsetInBlock - 4
                            val videoLength = totalLength - atomStartGlobal
                            if (videoLength in 4096..maxScanSize) {
                                return videoLength
                            }
                        }
                    }

                    val overlapSize = minOf(3, bytesRead)
                    overlap = ByteArray(overlapSize)
                    System.arraycopy(buffer, bytesRead - overlapSize, overlap, 0, overlapSize)

                    currentPos += bytesRead
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

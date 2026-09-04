package com.hrshd1eux.imava.core.util

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

    suspend fun checkMotionPhoto(context: Context, uri: Uri): MotionPhotoInfo = withContext(Dispatchers.IO) {
        try {
            // Check EXIF / XMP for GCamera:MicroVideo
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

            // binary scan fallback
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
        // backwards scan in 64KB chunks
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

    suspend fun getVideoDurationUs(videoFile: File): Long = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            (durStr?.toLongOrNull() ?: 0L) * 1000L
        } catch (_: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun extractFrameAt(videoFile: File, timeUs: Long): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun saveExtractedFrame(context: Context, bitmap: android.graphics.Bitmap, baseName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${baseName}_frame_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/MotionPhotos")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportMotionVideo(context: Context, videoFile: File, baseName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${baseName}_clip_${System.currentTimeMillis()}.mp4"
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(android.provider.MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MOVIES}/MotionPhotos")
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(videoFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportMotionToGif(context: Context, videoFile: File, baseName: String, fps: Int = 10): Uri? = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        val tempGifFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.gif")
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durStr?.toLongOrNull() ?: 2000L
            val totalFrames = ((durationMs / 1000f) * fps).toInt().coerceIn(6, 40)
            val intervalUs = (durationMs * 1000L) / totalFrames

            val encoder = GifEncoder()
            val fos = java.io.FileOutputStream(tempGifFile)
            encoder.setDelay(1000 / fps)
            encoder.setRepeat(0)
            encoder.start(fos)

            for (i in 0 until totalFrames) {
                val timeUs = i * intervalUs
                val rawBitmap = retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (rawBitmap != null) {
                    // Scale down to max 640px wide for optimal GIF performance and file size
                    val maxDim = 640
                    val scale = if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
                        maxDim.toFloat() / maxOf(rawBitmap.width, rawBitmap.height)
                    } else {
                        1f
                    }
                    val scaled = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            rawBitmap,
                            (rawBitmap.width * scale).toInt(),
                            (rawBitmap.height * scale).toInt(),
                            true
                        )
                    } else {
                        rawBitmap
                    }
                    encoder.addFrame(scaled)
                    if (scaled != rawBitmap) scaled.recycle()
                    rawBitmap.recycle()
                }
            }
            encoder.finish()
            fos.close()

            // Save GIF to MediaStore
            val fileName = "${baseName}_motion_${System.currentTimeMillis()}.gif"
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(android.provider.MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/MotionPhotos")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    java.io.FileInputStream(tempGifFile).use { input ->
                        input.copyTo(out)
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            tempGifFile.delete()
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempGifFile.exists()) tempGifFile.delete()
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}

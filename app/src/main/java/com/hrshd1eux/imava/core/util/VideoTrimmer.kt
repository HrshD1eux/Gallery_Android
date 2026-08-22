package com.hrshd1eux.imava.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object VideoTrimmer {

    private const val BUFFER_SIZE = 1024 * 1024 // 1 MB buffer

    /**
     * Losslessly trims a video from startMs to endMs using MediaExtractor and MediaMuxer.
     * Returns the Uri of the saved trimmed video.
     */
    suspend fun trimVideo(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long
    ): Uri? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val tempOutputFile = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")

        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext null
            extractor.setDataSource(pfd.fileDescriptor)

            val trackCount = extractor.trackCount
            muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = HashMap<Int, Int>()
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndexMap[i] = dstIndex
                }
            }

            if (trackIndexMap.isEmpty()) {
                pfd.close()
                return@withContext null
            }

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            // Seek to start keyframe
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val dstTrackIndex = trackIndexMap[trackIndex]
                if (dstTrackIndex != null) {
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    if (bufferInfo.presentationTimeUs > endUs) {
                        break
                    }

                    if (bufferInfo.presentationTimeUs >= startUs) {
                        bufferInfo.flags = extractor.sampleFlags
                        bufferInfo.offset = 0
                        muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                    }
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()
            pfd.close()

            // Save to public storage (Movies/Trimmed)
            val finalUri = saveVideoToMediaStore(context, tempOutputFile)
            tempOutputFile.delete()
            return@withContext finalUri
        } catch (e: Exception) {
            e.printStackTrace()
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            tempOutputFile.delete()
            return@withContext null
        }
    }

    /**
     * Converts a section of a video into an Animated GIF.
     */
    suspend fun convertVideoToGif(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        fps: Int = 10,
        targetWidth: Int = 360
    ): Uri? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val tempGifFile = File(context.cacheDir, "gif_${System.currentTimeMillis()}.gif")

        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext null
            retriever.setDataSource(pfd.fileDescriptor)

            val durationMs = (endMs - startMs).coerceAtLeast(100L)
            val frameIntervalMs = (1000L / fps).coerceAtLeast(50L)
            val frameCount = (durationMs / frameIntervalMs).toInt().coerceIn(1, 100)

            val frames = ArrayList<Bitmap>()
            for (i in 0 until frameCount) {
                val timeUs = (startMs + (i * frameIntervalMs)) * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = if (frame.width > targetWidth) {
                        val ratio = targetWidth.toFloat() / frame.width.toFloat()
                        val targetHeight = (frame.height * ratio).toInt()
                        Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)
                    } else {
                        frame
                    }
                    frames.add(scaled)
                }
            }
            retriever.release()
            pfd.close()

            if (frames.isEmpty()) return@withContext null

            // Encode frames to GIF
            FileOutputStream(tempGifFile).use { output ->
                encodeGif(frames, output, frameIntervalMs.toInt())
            }

            // Recycle bitmaps
            frames.forEach { it.recycle() }

            // Save to MediaStore
            val finalUri = saveGifToMediaStore(context, tempGifFile)
            tempGifFile.delete()
            return@withContext finalUri
        } catch (e: Exception) {
            e.printStackTrace()
            try { retriever.release() } catch (_: Exception) {}
            tempGifFile.delete()
            return@withContext null
        }
    }

    private fun saveVideoToMediaStore(context: Context, file: File): Uri? {
        val fileName = "Trimmed_${System.currentTimeMillis()}.mp4"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Trimmed")
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
            }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Trimmed").apply { mkdirs() }
            val target = File(dir, fileName)
            file.copyTo(target, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }
    }

    private fun saveGifToMediaStore(context: Context, file: File): Uri? {
        val fileName = "GIF_${System.currentTimeMillis()}.gif"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/gif")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GIFs")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
            }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GIFs").apply { mkdirs() }
            val target = File(dir, fileName)
            file.copyTo(target, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }
    }

    /**
     * Standard GIF89a encoder for writing animated GIFs.
     */
    private fun encodeGif(bitmaps: List<Bitmap>, output: FileOutputStream, delayMs: Int) {
        if (bitmaps.isEmpty()) return
        val first = bitmaps.first()
        val width = first.width
        val height = first.height

        // Header: "GIF89a"
        output.write("GIF89a".toByteArray())

        // Logical Screen Descriptor
        writeShort(output, width)
        writeShort(output, height)
        output.write(0xF7) // GCT flag: 256 colors
        output.write(0)    // Background Color Index
        output.write(0)    // Pixel Aspect Ratio

        // Global Color Table (Standard 256 color palette)
        val palette = generatePalette()
        output.write(palette)

        // Netscape Application Extension for looping animation
        output.write(0x21) // Extension Introducer
        output.write(0xFF) // Application Extension Label
        output.write(11)   // Block Size
        output.write("NETSCAPE2.0".toByteArray())
        output.write(3)    // Sub-block Length
        output.write(1)    // Loop Sub-block ID
        writeShort(output, 0) // Loop Count (0 = infinite)
        output.write(0)    // Block Terminator

        val delayHundredths = (delayMs / 10).coerceAtLeast(2)

        for (bitmap in bitmaps) {
            // Graphic Control Extension
            output.write(0x21) // Extension Introducer
            output.write(0xF9) // Graphic Control Label
            output.write(4)    // Block Size
            output.write(0)    // Packed fields (no disposal, no transparency)
            writeShort(output, delayHundredths)
            output.write(0)    // Transparent Color Index
            output.write(0)    // Block Terminator

            // Image Descriptor
            output.write(0x2C) // Image Separator
            writeShort(output, 0) // Left
            writeShort(output, 0) // Top
            writeShort(output, width)
            writeShort(output, height)
            output.write(0)    // Local Color Table Flag (use global)

            // Quantize bitmap pixels to palette indices & write uncompressed/LZW sub-blocks
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val indexedPixels = ByteArray(pixels.size)
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                // Map 24-bit RGB (332) to 8-bit palette index
                indexedPixels[i] = (((r and 0xE0) or ((g and 0xE0) shr 3) or ((b and 0xC0) shr 6))).toByte()
            }

            // Write LZW data
            writeLzwData(output, indexedPixels)
        }

        // GIF Trailer
        output.write(0x3B)
    }

    private fun generatePalette(): ByteArray {
        val palette = ByteArray(256 * 3)
        for (i in 0 until 256) {
            val r = (i and 0xE0)
            val g = (i and 0x1C) shl 3
            val b = (i and 0x03) shl 6
            palette[i * 3] = r.toByte()
            palette[i * 3 + 1] = g.toByte()
            palette[i * 3 + 2] = b.toByte()
        }
        return palette
    }

    private fun writeLzwData(output: FileOutputStream, pixels: ByteArray) {
        val minCodeSize = 8
        output.write(minCodeSize)

        // Write raw uncompressed sub-blocks with clear/end codes
        val clearCode = 1 shl minCodeSize // 256
        val eoiCode = clearCode + 1       // 257

        var offset = 0
        while (offset < pixels.size) {
            val chunkSize = minOf(254, pixels.size - offset)
            output.write(chunkSize + 1)
            output.write(clearCode and 0xFF)
            output.write(pixels, offset, chunkSize)
            offset += chunkSize
        }
        output.write(1)
        output.write(eoiCode and 0xFF)
        output.write(0) // Block Terminator
    }

    private fun writeShort(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }
}

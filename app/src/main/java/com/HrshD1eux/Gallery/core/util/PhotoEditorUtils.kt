package com.HrshD1eux.Gallery.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF

object PhotoEditorUtils {

    /**
     * Applies rotation, flip, crop, and brightness adjustments to a source bitmap in a single operation.
     */
    fun transformBitmap(
        source: Bitmap,
        rotationDegrees: Float = 0f,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        cropRect: RectF? = null,
        brightnessOffset: Float = 0f // Range: -100 to 100
    ): Bitmap {
        val matrix = Matrix()

        // 1. Flip
        val sx = if (flipHorizontal) -1f else 1f
        val sy = if (flipVertical) -1f else 1f
        if (flipHorizontal || flipVertical) {
            matrix.postScale(sx, sy)
        }

        // 2. Rotate
        if (rotationDegrees % 360f != 0f) {
            matrix.postRotate(rotationDegrees)
        }

        // Apply matrix transformation to source bitmap
        var intermediate = if (!matrix.isIdentity) {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true
            )
        } else {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }

        // 3. Crop
        if (cropRect != null && cropRect.width() > 0 && cropRect.height() > 0) {
            val left = (cropRect.left * intermediate.width).toInt().coerceIn(0, intermediate.width - 1)
            val top = (cropRect.top * intermediate.height).toInt().coerceIn(0, intermediate.height - 1)
            val right = (cropRect.right * intermediate.width).toInt().coerceIn(left + 1, intermediate.width)
            val bottom = (cropRect.bottom * intermediate.height).toInt().coerceIn(top + 1, intermediate.height)
            
            val cropWidth = right - left
            val cropHeight = bottom - top

            if (cropWidth > 0 && cropHeight > 0) {
                val cropped = Bitmap.createBitmap(intermediate, left, top, cropWidth, cropHeight)
                intermediate = cropped
            }
        }

        // 4. Brightness adjustment
        if (brightnessOffset != 0f) {
            val result = Bitmap.createBitmap(intermediate.width, intermediate.height, intermediate.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Brightness color matrix
            val colorMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, brightnessOffset,
                    0f, 1f, 0f, 0f, brightnessOffset,
                    0f, 0f, 1f, 0f, brightnessOffset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(intermediate, 0f, 0f, paint)

            return result
        }

        return intermediate
    }

    /**
     * Decodes a sub-sampled preview bitmap from Uri based on requested width and height to prevent OOM.
     */
    fun decodeSubSampledBitmapFromUri(
        context: android.content.Context,
        uri: android.net.Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes full-resolution bitmap from Uri for export/save processing with OOM safety fallback bounds.
     */
    fun decodeFullBitmapFromUri(
        context: android.content.Context,
        uri: android.net.Uri,
        maxDimension: Int = 4096
    ): Bitmap? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            try {
                val emergencyOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 4
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, emergencyOptions)
                }
            } catch (ex: Throwable) {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculates optimal inSampleSize power-of-two factor.
     */
    fun calculateInSampleSize(
        options: android.graphics.BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Copies EXIF metadata attributes (camera info, timestamps, exposure, GPS) from source Uri to target Uri.
     */
    fun copyExifAttributes(
        context: android.content.Context,
        sourceUri: android.net.Uri,
        targetUri: android.net.Uri
    ) {
        try {
            val sourceExif = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                androidx.exifinterface.media.ExifInterface(stream)
            } ?: return

            context.contentResolver.openFileDescriptor(targetUri, "rw")?.use { pfd ->
                val targetExif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)

                val attributes = arrayOf(
                    androidx.exifinterface.media.ExifInterface.TAG_MAKE,
                    androidx.exifinterface.media.ExifInterface.TAG_MODEL,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED,
                    androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME,
                    androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                    androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                    androidx.exifinterface.media.ExifInterface.TAG_SUBSEC_TIME,
                    androidx.exifinterface.media.ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                    androidx.exifinterface.media.ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                    androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,
                    androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER,
                    androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH,
                    androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                    androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE,
                    androidx.exifinterface.media.ExifInterface.TAG_FLASH,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE,
                    androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT,
                    androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT
                )

                for (attr in attributes) {
                    val value = sourceExif.getAttribute(attr)
                    if (value != null) {
                        targetExif.setAttribute(attr, value)
                    }
                }

                // Set orientation to NORMAL (1) since rotation/flip edits are already burned into bitmap pixels
                targetExif.setAttribute(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL.toString()
                )
                targetExif.saveAttributes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

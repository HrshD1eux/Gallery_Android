package com.hrshd1eux.imava.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.print.PrintHelper
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PrintUtil {

    suspend fun printPhoto(context: Context, item: MediaItem) = withContext(Dispatchers.IO) {
        val uri = item.uri
        val bitmap = decodeRotatedBitmap(context, uri) ?: return@withContext

        withContext(Dispatchers.Main) {
            val printHelper = PrintHelper(context).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
                colorMode = PrintHelper.COLOR_MODE_COLOR
            }
            val fileName = item.path.substringAfterLast('/').ifEmpty { "Photo" }
            val jobName = "Imava_Print_$fileName"
            printHelper.printBitmap(jobName, bitmap)
        }
    }

    private fun decodeRotatedBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            var rotationDegrees = 0
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                rotationDegrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }

            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (_: Exception) {
            null
        }
    }
}

package com.hrshd1eux.imava.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SharingUtils {

    suspend fun shareMedia(
        context: Context,
        items: List<MediaItem>,
        stripMetadata: Boolean
    ) = withContext(Dispatchers.IO) {
        val parentFolder = File(context.cacheDir, "shared_images").apply { mkdirs() }
        try {
            parentFolder.listFiles()?.forEach { file ->
                if (file.isDirectory && (System.currentTimeMillis() - file.lastModified() > 1800000)) {
                    file.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val sharedFolder = File(parentFolder, "share_${System.currentTimeMillis()}").apply { mkdirs() }

        val uris = items.mapNotNull { item ->
            try {
                val fileName = "${item.id}_${item.path.substringAfterLast('/', "shared_media")}"
                val tempFile = File(sharedFolder, fileName)
                
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        if (item.isHidden) {
                            try {
                                VaultCrypto.decrypt(input, output)
                            } catch (_: Exception) {
                                input.copyTo(output)
                            }
                        } else {
                            input.copyTo(output)
                        }
                    }
                } ?: return@mapNotNull null

                if (stripMetadata && item.mimeType.contains("image", ignoreCase = true)) {
                    stripExif(tempFile)
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (uris.isNotEmpty()) {
            val shareIntent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = items.first().mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            val chooser = Intent.createChooser(shareIntent, "Share media").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    private fun stripExif(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            
            // Critical privacy-leak EXIF tags to remove
            val tagsToRemove = listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_PROCESSING_METHOD,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_MAKE,       // Manufacturer
                ExifInterface.TAG_MODEL,      // Camera model
                ExifInterface.TAG_SOFTWARE,   // Software used
                ExifInterface.TAG_CAMERA_OWNER_NAME, // Photographer/Device Owner
                ExifInterface.TAG_IMAGE_DESCRIPTION
            )
            
            for (tag in tagsToRemove) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

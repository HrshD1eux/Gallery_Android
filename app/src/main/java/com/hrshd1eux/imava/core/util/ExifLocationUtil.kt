package com.hrshd1eux.imava.core.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ExifLocationUtil {

    data class GeoLocation(
        val latitude: Double,
        val longitude: Double
    )

    suspend fun getGeotag(context: Context, uri: Uri, path: String? = null): GeoLocation? = withContext(Dispatchers.IO) {
        try {
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val exif = ExifInterface(file.absolutePath)
                    val coords = exif.latLong
                    if (coords != null && coords.size >= 2) {
                        return@withContext GeoLocation(coords[0], coords[1])
                    }
                }
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val coords = exif.latLong
                if (coords != null && coords.size >= 2) {
                    return@withContext GeoLocation(coords[0], coords[1])
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun removeGeotag(context: Context, uri: Uri, path: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try file path directly
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists() && file.canWrite()) {
                    val exif = ExifInterface(file.absolutePath)
                    stripGpsFromExif(exif)
                    exif.saveAttributes()
                    return@withContext true
                }
            }

            // Fallback to ParcelFileDescriptor "rw"
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                stripGpsFromExif(exif)
                exif.saveAttributes()
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun setGeotag(
        context: Context,
        uri: Uri,
        path: String? = null,
        latitude: Double,
        longitude: Double
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists() && file.canWrite()) {
                    val exif = ExifInterface(file.absolutePath)
                    exif.setLatLong(latitude, longitude)
                    exif.saveAttributes()
                    return@withContext true
                }
            }

            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setLatLong(latitude, longitude)
                exif.saveAttributes()
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun stripGpsFromExif(exif: ExifInterface) {
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
    }
}

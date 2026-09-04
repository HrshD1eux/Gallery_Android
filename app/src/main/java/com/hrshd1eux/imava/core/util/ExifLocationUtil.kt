package com.hrshd1eux.imava.core.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import android.location.Geocoder
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object ExifLocationUtil {

    data class GeoLocation(
        val latitude: Double,
        val longitude: Double
    )

    data class GeoResult(
        val latitude: Double,
        val longitude: Double,
        val displayName: String
    )

    fun parseCoordinates(query: String): Pair<Double, Double>? {
        val clean = query.trim()
        val parts = clean.split(',', ';', ' ', '\t').filter { it.isNotBlank() }
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return Pair(lat, lng)
            }
        }
        return null
    }

    suspend fun geocode(context: Context, query: String): GeoResult? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        val coords = parseCoordinates(trimmed)
        if (coords != null) {
            val placeName = reverseGeocode(context, coords.first, coords.second)
            val display = if (!placeName.isNullOrBlank()) placeName else "${coords.first}, ${coords.second}"
            return@withContext GeoResult(coords.first, coords.second, display)
        }

        // Try Android Geocoder first
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(trimmed, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val display = addr.getAddressLine(0) ?: trimmed
                    return@withContext GeoResult(addr.latitude, addr.longitude, display)
                }
            }
        } catch (_: Exception) {
        }

        // Fallback: OpenStreetMap Nominatim
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "ImavaGalleryApp/1.0")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    val lat = obj.getDouble("lat")
                    val lon = obj.getDouble("lon")
                    val name = obj.optString("display_name", trimmed)
                    return@withContext GeoResult(lat, lon, name)
                }
            }
        } catch (_: Exception) {
        }

        null
    }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val line = addr.getAddressLine(0)
                    if (!line.isNullOrBlank()) return@withContext line
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    val country = addr.countryName
                    if (city != null && country != null) return@withContext "$city, $country"
                    if (country != null) return@withContext country
                }
            }
        } catch (_: Exception) {
        }

        try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "ImavaGalleryApp/1.0")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val name = obj.optString("display_name").takeIf { it.isNotBlank() }
                if (!name.isNullOrBlank()) return@withContext name
            }
        } catch (_: Exception) {
        }

        null
    }

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

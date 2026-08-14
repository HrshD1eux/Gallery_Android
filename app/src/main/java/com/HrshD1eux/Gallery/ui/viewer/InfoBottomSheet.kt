package com.HrshD1eux.Gallery.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExifDetails(
    val dateTaken: String?,
    val cameraModel: String?,
    val resolution: String?,
    val fileSize: String?,
    val location: String?,
    val hasGps: Boolean,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    item: MediaItem,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var details by remember(item) { mutableStateOf<ExifDetails?>(null) }

    LaunchedEffect(item) {
        withContext(Dispatchers.IO) {
            details = readExifDetails(item)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            details?.let { info ->
                // Date Section
                InfoRow(
                    icon = Icons.Default.Info,
                    title = "Date Taken",
                    subtitle = info.dateTaken ?: SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault()).format(Date(item.dateTaken))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Camera model section
                InfoRow(
                    icon = Icons.Default.PhotoCamera,
                    title = "Camera Model",
                    subtitle = info.cameraModel ?: "No EXIF camera info"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // File metadata details
                InfoRow(
                    icon = Icons.Default.Info,
                    title = "Properties",
                    subtitle = "${info.resolution}  ·  ${info.fileSize}"
                )

                if (info.hasGps) {
                    Spacer(modifier = Modifier.height(12.dp))

                    InfoRow(
                        icon = Icons.Default.LocationOn,
                        title = "Location",
                        subtitle = "GPS coordinates: ${info.location}"
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${info.latitude},${info.longitude}?q=${info.latitude},${info.longitude}(Photo Location)"))
                                mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(mapIntent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in Maps")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } ?: Text(
                text = "Loading metadata...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun readExifDetails(item: MediaItem): ExifDetails {
    val file = File(item.path)
    if (!file.exists()) {
        return ExifDetails(
            dateTaken = null,
            cameraModel = null,
            resolution = "${item.width} × ${item.height}",
            fileSize = String.format(Locale.getDefault(), "%.2f MB", item.size.toFloat() / (1024 * 1024)),
            location = null,
            hasGps = false
        )
    }

    return try {
        val exif = ExifInterface(file.absolutePath)
        val latLong = exif.latLong
        val hasGps = latLong != null && latLong.size >= 2
        
        val cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)
        val cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)
        val modelText = if (cameraModel != null) "${cameraMake ?: ""} $cameraModel".trim() else null

        val resolutionText = "${item.width} × ${item.height}"
        val sizeText = String.format(Locale.getDefault(), "%.2f MB", item.size.toFloat() / (1024 * 1024))

        ExifDetails(
            dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME),
            cameraModel = modelText,
            resolution = resolutionText,
            fileSize = sizeText,
            location = if (hasGps && latLong != null) String.format(Locale.getDefault(), "%.4f, %.4f", latLong[0], latLong[1]) else null,
            hasGps = hasGps,
            latitude = if (hasGps && latLong != null) latLong[0] else 0.0,
            longitude = if (hasGps && latLong != null) latLong[1] else 0.0
        )
    } catch (e: Exception) {
        ExifDetails(null, null, "${item.width} × ${item.height}", "${item.size / 1024} KB", null, false)
    }
}

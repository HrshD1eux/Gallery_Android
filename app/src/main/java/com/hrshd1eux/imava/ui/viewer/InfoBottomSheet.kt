package com.hrshd1eux.imava.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExifDetails(
    val fileName: String,
    val filePath: String,
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
    onUpdateDateTaken: ((Long) -> Unit)? = null,
    onUpdateTags: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var details by remember(item) { mutableStateOf<ExifDetails?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var tagsList by remember(item.id, item.tags) { mutableStateOf(item.tags) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    LaunchedEffect(item) {
        withContext(Dispatchers.IO) {
            details = readExifDetails(context, item)
        }
    }

    if (showDatePicker) {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = if (item.dateTaken > 0) item.dateTaken else System.currentTimeMillis()
        }
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val currentMonth = calendar.get(java.util.Calendar.MONTH)
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)

        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timePickerDialog = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val newCal = java.util.Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute)
                        }
                        val newTimestamp = newCal.timeInMillis
                        onUpdateDateTaken?.invoke(newTimestamp)
                        details = details?.copy(dateTaken = formatDateTaken(null, newTimestamp))
                        showDatePicker = false
                    },
                    currentHour,
                    currentMinute,
                    false
                )
                timePickerDialog.show()
            },
            currentYear,
            currentMonth,
            currentDay
        )
        datePickerDialog.setOnDismissListener { showDatePicker = false }
        datePickerDialog.show()
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
                // File Name
                InfoRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    title = "File Name",
                    subtitle = info.fileName
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Path
                InfoRow(
                    icon = Icons.Default.Folder,
                    title = "Path",
                    subtitle = info.filePath
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Date Section with Edit Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoRow(
                            icon = Icons.Default.Info,
                            title = "Date Taken",
                            subtitle = info.dateTaken ?: SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault()).format(Date(item.dateTaken))
                        )
                    }
                    if (onUpdateDateTaken != null) {
                        androidx.compose.material3.IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Date & Time",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

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

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Offline Tags Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tags & Hashtags 🏷️",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { showAddTagDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Tag",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    item {
                        InputChip(
                            selected = false,
                            onClick = { showAddTagDialog = true },
                            label = { Text("+ Add Tag") }
                        )
                    }
                    if (tagsList.isEmpty()) {
                        item {
                            Text(
                                text = "No custom tags added yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )
                        }
                    } else {
                        items(tagsList) { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Tag",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                val updated = tagsList - tag
                                                tagsList = updated
                                                onUpdateTags?.invoke(updated)
                                            }
                                    )
                                }
                            )
                        }
                    }
                }

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
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: android.content.ActivityNotFoundException) {
                                }
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

        if (showAddTagDialog) {
            AlertDialog(
                onDismissRequest = { showAddTagDialog = false },
                title = { Text("Add Tag / Hashtag") },
                text = {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        label = { Text("Tag name") },
                        placeholder = { Text("e.g. vacation, sunset") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        enabled = newTagInput.isNotBlank(),
                        onClick = {
                            val clean = newTagInput.trim().removePrefix("#").lowercase()
                            if (clean.isNotEmpty() && !tagsList.contains(clean)) {
                                val updated = tagsList + clean
                                tagsList = updated
                                onUpdateTags?.invoke(updated)
                            }
                            newTagInput = ""
                            showAddTagDialog = false
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTagDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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

private fun formatDateTaken(rawExifDate: String?, timestampMs: Long): String {
    val outputFormatter = SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault())
    if (!rawExifDate.isNullOrEmpty()) {
        val formatsToTry = arrayOf(
            "yyyy:MM:dd HH:mm:ss",
            "yyyy:MM:dd HH:mm",
            "yyyy:MM:dd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (format in formatsToTry) {
            try {
                val parser = SimpleDateFormat(format, Locale.getDefault())
                val parsed = parser.parse(rawExifDate)
                if (parsed != null) {
                    return outputFormatter.format(parsed)
                }
            } catch (_: Exception) { }
        }
    }
    val safeMs = if (timestampMs > 0) timestampMs else System.currentTimeMillis()
    return outputFormatter.format(Date(safeMs))
}

private fun readExifDetails(context: Context, item: MediaItem): ExifDetails {
    val file = File(item.path)
    val fileName = file.name.ifEmpty { item.uri.lastPathSegment ?: "Unknown" }
    val filePath = if (file.exists()) file.absolutePath else item.path

    var exif: ExifInterface? = null
    if (file.exists() && file.canRead()) {
        try {
            exif = ExifInterface(file.absolutePath)
        } catch (_: Exception) {}
    }

    if (exif == null) {
        try {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                exif = ExifInterface(stream)
            }
        } catch (_: Exception) {}
    }

    if (exif == null) {
        return ExifDetails(
            fileName = fileName,
            filePath = filePath,
            dateTaken = formatDateTaken(null, item.dateTaken),
            cameraModel = null,
            resolution = "${item.width} × ${item.height}",
            fileSize = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(item.size),
            location = null,
            hasGps = false
        )
    }

    return try {
        val nonNullExif = exif!!
        val latLong = nonNullExif.latLong
        val hasGps = latLong != null && latLong.size >= 2
        
        val cameraModel = nonNullExif.getAttribute(ExifInterface.TAG_MODEL)
        val cameraMake = nonNullExif.getAttribute(ExifInterface.TAG_MAKE)
        val modelText = if (cameraModel != null) "${cameraMake ?: ""} $cameraModel".trim() else null

        val resolutionText = "${item.width} × ${item.height}"
        val sizeText = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(item.size)
        val rawDate = nonNullExif.getAttribute(ExifInterface.TAG_DATETIME) ?: nonNullExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        val formattedDate = formatDateTaken(rawDate, item.dateTaken)

        ExifDetails(
            fileName = fileName,
            filePath = filePath,
            dateTaken = formattedDate,
            cameraModel = modelText,
            resolution = resolutionText,
            fileSize = sizeText,
            location = if (hasGps && latLong != null) String.format(Locale.getDefault(), "%.4f, %.4f", latLong[0], latLong[1]) else null,
            hasGps = hasGps,
            latitude = if (hasGps && latLong != null) latLong[0] else 0.0,
            longitude = if (hasGps && latLong != null) latLong[1] else 0.0
        )
    } catch (e: Exception) {
        ExifDetails(
            fileName = fileName,
            filePath = filePath,
            dateTaken = formatDateTaken(null, item.dateTaken),
            cameraModel = null,
            resolution = "${item.width} × ${item.height}",
            fileSize = com.hrshd1eux.imava.core.util.FormatUtils.formatFileSize(item.size),
            location = null,
            hasGps = false
        )
    }
}

package com.hrshd1eux.imava.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrshd1eux.imava.ui.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appTheme = viewModel.appTheme

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {

        item {
            SettingsCategoryHeader(title = "Appearance & Theme", icon = Icons.Default.Palette)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Choose App Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    ThemeOptionRow(
                        title = "System Default",
                        selected = appTheme == "system",
                        onClick = { viewModel.appTheme = "system" }
                    )
                    ThemeOptionRow(
                        title = "Dark Theme",
                        selected = appTheme == "dark",
                        onClick = { viewModel.appTheme = "dark" }
                    )
                    ThemeOptionRow(
                        title = "AMOLED Dark (Pure Black)",
                        selected = appTheme == "amoled",
                        onClick = { viewModel.appTheme = "amoled" }
                    )
                    ThemeOptionRow(
                        title = "Light Theme",
                        selected = appTheme == "light",
                        onClick = { viewModel.appTheme = "light" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }


        item {
            SettingsCategoryHeader(title = "Grid & Display Size", icon = Icons.Default.GridView)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val currentCols = viewModel.gridColumnCount
                    val label = when (currentCols) {
                        1 -> "1 Column (Extra Large)"
                        2 -> "2 Columns (Large)"
                        3 -> "3 Columns (Standard)"
                        4 -> "4 Columns (Compact)"
                        5 -> "5 Columns (Dense)"
                        6 -> "6 Columns (Overview)"
                        else -> "$currentCols Columns"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Timeline Columns",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust the default size of photos in the timeline grid. You can also pinch-to-zoom on the timeline to resize at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = currentCols.toFloat(),
                        onValueChange = { newValue ->
                            val cols = newValue.toInt().coerceIn(1, 6)
                            if (cols != currentCols) {
                                viewModel.setGridColumns(cols)
                                com.hrshd1eux.imava.core.util.HapticUtil.performSelection(context)
                            }
                        },
                        valueRange = 1f..6f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "1 (Big)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "3 (Normal)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "6 (Dense)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }


        item {
            val galleryPrefs = remember(context) { context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE) }
            var hapticsEnabled by remember { mutableStateOf(galleryPrefs.getBoolean("haptics_enabled", true)) }

            SettingsCategoryHeader(title = "Touch & Haptics", icon = Icons.Default.Vibration)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Haptic Vibrations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tactile feedback when selecting photos, unlocking vault, editing, and deleting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { enabled ->
                            hapticsEnabled = enabled
                            galleryPrefs.edit().putBoolean("haptics_enabled", enabled).apply()
                            if (enabled) {
                                com.hrshd1eux.imava.core.util.HapticUtil.performSuccess(context)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }


        item {
            val excludedSet by viewModel.excludedBucketIds.collectAsState()
            val allBuckets by viewModel.buckets.collectAsState()
            var showManageExcludedDialog by remember { mutableStateOf(false) }

            SettingsCategoryHeader(title = "Privacy & Folders", icon = Icons.Default.Folder)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManageExcludedDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Excluded / Hidden Folders",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (excludedSet.isEmpty()) "No folders hidden" else "${excludedSet.size} folder(s) excluded from gallery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showManageExcludedDialog) {
                AlertDialog(
                    onDismissRequest = { showManageExcludedDialog = false },
                    title = { Text("Excluded Folders") },
                    text = {
                        if (excludedSet.isEmpty()) {
                            Text("You have not excluded any folders yet. Long-press any album in the Albums tab to hide it.")
                        } else {
                            Column {
                                Text("These folders are hidden from your gallery view:")
                                Spacer(modifier = Modifier.height(8.dp))
                                excludedSet.toList().forEach { bucketId ->
                                    val folderName = allBuckets.find { it.id.toString() == bucketId }?.name ?: "Folder #$bucketId"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(folderName, style = MaterialTheme.typography.bodyMedium)
                                        TextButton(
                                            onClick = {
                                                viewModel.restoreExcludedBucket(bucketId)
                                            }
                                        ) {
                                            Text("Restore")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showManageExcludedDialog = false }) {
                            Text("Done")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }


        item {
            SettingsCategoryHeader(title = "About", icon = Icons.Default.Info)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Imava",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    val appVersionName = remember(context) {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
                            } else {
                                @Suppress("DEPRECATION")
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            } ?: "1.1.3"
                        } catch (_: Exception) {
                            "1.1.3"
                        }
                    }
                    Text(
                        text = "Version $appVersionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "A fast, private, and secure gallery for your photos and videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val settingsPrefs = remember(context) { context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE) }
                    var autoCheckUpdates by remember { mutableStateOf(settingsPrefs.getBoolean("auto_check_updates", true)) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Auto-Check for Updates", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Check GitHub releases in background daily",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCheckUpdates,
                            onCheckedChange = { enabled ->
                                autoCheckUpdates = enabled
                                settingsPrefs.edit().putBoolean("auto_check_updates", enabled).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    var isDownloadingUpdate by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableIntStateOf(0) }
                    var updateInfoResult by remember { mutableStateOf<com.hrshd1eux.imava.core.util.UpdateInfo?>(null) }
                    var showNoUpdateDialog by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                val info = com.hrshd1eux.imava.core.util.AppUpdateManager.checkForUpdates(context)
                                isCheckingUpdate = false
                                updateInfoResult = info
                                if (!info.hasUpdate) {
                                    showNoUpdateDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking GitHub...")
                        } else {
                            Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }


                    val currentInfo = updateInfoResult
                    if (currentInfo != null && currentInfo.hasUpdate) {
                        AlertDialog(
                            onDismissRequest = { updateInfoResult = null },
                            title = { Text("New Version Available: v${currentInfo.latestVersion}") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    if (com.hrshd1eux.imava.core.util.AppUpdateManager.isVersionOlderThan(currentInfo.currentVersion, "1.1.8")) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                text = "⚠️ Migration Note: Because of our transition to the new package ID (com.hrshd1eux.imava), please download the APK, install it, and delete the older version manually.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    Text("Installed version: v${currentInfo.currentVersion}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Release Highlights:", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val cleanedNotes = remember(currentInfo.releaseNotes) {
                                        com.hrshd1eux.imava.core.util.AppUpdateManager.formatCleanReleaseNotes(currentInfo.releaseNotes)
                                    }
                                    Text(
                                        text = cleanedNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val apkUrl = currentInfo.apkDownloadUrl
                                        if (apkUrl != null) {
                                            isDownloadingUpdate = true
                                            scope.launch {
                                                val success = com.hrshd1eux.imava.core.util.AppUpdateManager.downloadAndInstallApk(
                                                    context = context,
                                                    downloadUrl = apkUrl,
                                                    onProgress = { progress -> downloadProgress = progress }
                                                )
                                                isDownloadingUpdate = false
                                                updateInfoResult = null
                                            }
                                        }
                                    }
                                ) {
                                    Text("Download & Install")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateInfoResult = null }) {
                                    Text("Later")
                                }
                            }
                        )
                    }


                    if (isDownloadingUpdate) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Downloading Update...") },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("$downloadProgress%", style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                            confirmButton = {}
                        )
                    }


                    if (showNoUpdateDialog && currentInfo != null && !currentInfo.hasUpdate) {
                        AlertDialog(
                            onDismissRequest = { showNoUpdateDialog = false },
                            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = { Text("App Up-to-Date (v${currentInfo.currentVersion})") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text("You are using the latest version of Imava! 🎉")
                                    if (currentInfo.releaseNotes.isNotBlank() && !currentInfo.releaseNotes.startsWith("Could not check") && !currentInfo.releaseNotes.startsWith("Error")) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Current Release Highlights:", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val cleanedNotes = remember(currentInfo.releaseNotes) {
                                            com.hrshd1eux.imava.core.util.AppUpdateManager.formatCleanReleaseNotes(currentInfo.releaseNotes)
                                        }
                                        Text(
                                            text = cleanedNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showNoUpdateDialog = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

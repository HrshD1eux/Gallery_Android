package com.HrshD1eux.Gallery.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.HrshD1eux.Gallery.ui.MainViewModel

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
        // --- Appearance & Theme Section ---
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
                        title = "Light Theme",
                        selected = appTheme == "light",
                        onClick = { viewModel.appTheme = "light" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Privacy & Security Section ---


        // --- About Section ---
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
                        text = "Gallery App",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0",
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
                    Spacer(modifier = Modifier.height(20.dp))

                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    var isDownloadingUpdate by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableIntStateOf(0) }
                    var updateInfoResult by remember { mutableStateOf<com.HrshD1eux.Gallery.core.util.UpdateInfo?>(null) }
                    var showNoUpdateDialog by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                val info = com.HrshD1eux.Gallery.core.util.AppUpdateManager.checkForUpdates(context)
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

                    // Update Available Dialog
                    val currentInfo = updateInfoResult
                    if (currentInfo != null && currentInfo.hasUpdate) {
                        AlertDialog(
                            onDismissRequest = { updateInfoResult = null },
                            title = { Text("New Version Available: v${currentInfo.latestVersion}") },
                            text = {
                                Column {
                                    Text("Installed version: v${currentInfo.currentVersion}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Release Notes:", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = currentInfo.releaseNotes,
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
                                                val success = com.HrshD1eux.Gallery.core.util.AppUpdateManager.downloadAndInstallApk(
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

                    // Download Progress Dialog
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

                    // Already Up-to-Date Dialog
                    if (showNoUpdateDialog && (currentInfo == null || !currentInfo.hasUpdate)) {
                        AlertDialog(
                            onDismissRequest = { showNoUpdateDialog = false },
                            title = { Text("App Up-to-Date") },
                            text = {
                                Text("You are using the latest version (${currentInfo?.currentVersion ?: "v1.0.0"}). No updates available right now.")
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

package com.HrshD1eux.Gallery.ui.albums

import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import com.HrshD1eux.Gallery.ui.MainViewModel

@Composable
fun AlbumsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val buckets by viewModel.buckets.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val trashed by viewModel.trashed.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val videosCount by viewModel.videosCount.collectAsState()

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vault_prefs", android.content.Context.MODE_PRIVATE) }
    var storedPinHash by remember { mutableStateOf(prefs.getString("vault_pin_hash", null)) }
    var storedSaltBase64 by remember { mutableStateOf(prefs.getString("vault_salt", null)) }
    var legacyPin by remember { mutableStateOf(prefs.getString("vault_pin", null)) }
    val isPinConfigured = storedPinHash != null || legacyPin != null

    var showHiddenLockedDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var isSettingPinMode by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
        // Search Bar at top of Albums
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .clickable { viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Search }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Search photos, videos, albums...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Smart Albums header
        item {
            Text(
                text = "Smart Categories",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Smart Album: Favorites
        item {
            AlbumRowItem(
                icon = Icons.Default.Favorite,
                title = "Favorites",
                count = favorites.size,
                onClick = {
                    viewModel.currentBucketId = null
                    viewModel.currentBucketName = null
                    viewModel.currentCategoryName = "Favorites"
                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                }
            )
        }

        // Smart Album: Videos
        item {
            AlbumRowItem(
                icon = Icons.Default.PlayCircle,
                title = "Videos",
                count = videosCount,
                onClick = {
                    viewModel.currentBucketId = null
                    viewModel.currentBucketName = null
                    viewModel.currentCategoryName = "Videos"
                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                }
            )
        }

        // Smart Album: Hidden
        item {
            val isBiometricEnabled = remember(context) {
                context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE).getBoolean("vault_biometric_enabled", false)
            }
            AlbumRowItem(
                icon = Icons.Default.Lock,
                title = "Hidden Vault",
                count = hidden.size,
                onClick = {
                    val activity = context as? android.app.Activity
                    if (isBiometricEnabled && activity != null) {
                        com.HrshD1eux.Gallery.core.util.BiometricAuthHelper.authenticate(
                            activity = activity,
                            title = "Unlock Hidden Vault",
                            subtitle = "Use fingerprint or face unlock to access your hidden photos",
                            onSuccess = {
                                viewModel.unlockVault()
                                viewModel.currentBucketId = null
                                viewModel.currentBucketName = null
                                viewModel.currentCategoryName = "Hidden Vault"
                                viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                            },
                            onError = { _ ->
                                showHiddenLockedDialog = true
                            }
                        )
                    } else {
                        showHiddenLockedDialog = true
                    }
                }
            )
        }

        // Smart Album: Trash
        item {
            AlbumRowItem(
                icon = Icons.Default.Delete,
                title = "Trash",
                count = trashed.size,
                onClick = {
                    viewModel.currentBucketId = null
                    viewModel.currentBucketName = null
                    viewModel.currentCategoryName = "Trash"
                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Device Folders",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (buckets.isEmpty()) {
            item {
                Text(
                    text = "No device folders found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(buckets) { bucket ->
                AlbumRowItem(
                    icon = Icons.Default.Folder,
                    title = bucket.name,
                    count = bucket.count,
                    onClick = {
                        viewModel.selectBucket(bucket.id, bucket.name)
                    }
                )
            }
        }
    }

    FloatingActionButton(
            onClick = { showCreateAlbumDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 80.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Create Album")
        }

        if (showCreateAlbumDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showCreateAlbumDialog = false 
                    newAlbumName = ""
                },
                title = { Text("Create New Album") },
                text = {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        label = { Text("Album Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAlbumName.isNotBlank()) {
                                viewModel.createEmptyAlbum(context, newAlbumName.trim())
                                showCreateAlbumDialog = false
                                newAlbumName = ""
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showCreateAlbumDialog = false 
                            newAlbumName = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showHiddenLockedDialog) {
        AlertDialog(
            onDismissRequest = { 
                showHiddenLockedDialog = false
                pinInput = ""
                pinConfirmInput = ""
                pinErrorMessage = null
                isSettingPinMode = false
            },
            title = {
                Text(
                    text = if (!isPinConfigured) {
                        if (isSettingPinMode) "Confirm Vault PIN" else "Set Vault PIN"
                    } else {
                        "Enter Vault PIN"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (!isPinConfigured) {
                            if (isSettingPinMode) "Re-enter your 4-digit PIN to confirm" else "Create a 4-digit PIN to secure your Hidden Vault"
                        } else {
                            "Enter security PIN to access the Hidden Vault"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                                pinInput = newValue
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinErrorMessage != null) {
                        Text(
                            text = pinErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pinErrorMessage = null
                        if (pinInput.length != 4) {
                            pinErrorMessage = "PIN must be exactly 4 digits"
                            return@Button
                        }

                        if (!isPinConfigured) {
                            if (!isSettingPinMode) {
                                // Transition to confirmation stage
                                pinConfirmInput = pinInput
                                pinInput = ""
                                isSettingPinMode = true
                            } else {
                                if (pinInput == pinConfirmInput) {
                                    val salt = com.HrshD1eux.Gallery.core.util.VaultCrypto.generateSalt()
                                    val saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
                                    val rawPinHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.hashPin(pinInput, salt)
                                    val encryptedPinHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.encryptString(rawPinHash)
                                    prefs.edit()
                                        .putString("vault_pin_hash", encryptedPinHash)
                                        .putString("vault_salt", saltBase64)
                                        .remove("vault_pin")
                                        .apply()
                                    storedPinHash = encryptedPinHash
                                    storedSaltBase64 = saltBase64
                                    legacyPin = null
                                    pinInput = ""
                                    isSettingPinMode = false
                                    
                                    // Grant access and navigate inside
                                    viewModel.unlockVault()
                                    showHiddenLockedDialog = false
                                    viewModel.currentBucketId = null
                                    viewModel.currentBucketName = null
                                    viewModel.currentCategoryName = "Hidden Vault"
                                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                                } else {
                                    pinErrorMessage = "PINs do not match. Try again."
                                    pinInput = ""
                                    isSettingPinMode = false
                                }
                            }
                        } else {
                            var isValid = false
                            if (storedPinHash != null && storedSaltBase64 != null) {
                                val salt = android.util.Base64.decode(storedSaltBase64, android.util.Base64.NO_WRAP)
                                val inputHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.hashPin(pinInput, salt)
                                val decryptedHash = try {
                                    com.HrshD1eux.Gallery.core.util.VaultCrypto.decryptString(storedPinHash!!)
                                } catch (_: Exception) {
                                    storedPinHash
                                }
                                isValid = (inputHash == decryptedHash || inputHash == storedPinHash)
                            } else if (legacyPin != null && pinInput == legacyPin) {
                                // Automatically upgrade legacy plaintext PIN to KeyStore encrypted hash
                                val salt = com.HrshD1eux.Gallery.core.util.VaultCrypto.generateSalt()
                                val saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
                                val rawPinHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.hashPin(pinInput, salt)
                                val encryptedPinHash = com.HrshD1eux.Gallery.core.util.VaultCrypto.encryptString(rawPinHash)
                                prefs.edit()
                                    .putString("vault_pin_hash", encryptedPinHash)
                                    .putString("vault_salt", saltBase64)
                                    .remove("vault_pin")
                                    .apply()
                                storedPinHash = encryptedPinHash
                                storedSaltBase64 = saltBase64
                                legacyPin = null
                                isValid = true
                            }

                            if (isValid) {
                                viewModel.unlockVault()
                                showHiddenLockedDialog = false
                                pinInput = ""
                                viewModel.currentBucketId = null
                                viewModel.currentBucketName = null
                                viewModel.currentCategoryName = "Hidden Vault"
                                viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                            } else {
                                pinErrorMessage = "Incorrect PIN"
                                pinInput = ""
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (!isPinConfigured) {
                            if (isSettingPinMode) "Confirm" else "Next"
                        } else {
                            "Unlock"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHiddenLockedDialog = false
                        pinInput = ""
                        pinConfirmInput = ""
                        pinErrorMessage = null
                        isSettingPinMode = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
}

@Composable
fun AlbumRowItem(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small
                )
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

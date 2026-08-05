package com.HrshD1eux.Gallery.ui.albums

import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
    val favorites by viewModel.favorites.collectAsState()
    val trashed by viewModel.trashed.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val videosCount by viewModel.videosCount.collectAsState()

    val context = LocalContext.current
    var showHiddenLockedDialog by remember { mutableStateOf(false) }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    val systemFolderNames = remember { setOf("Camera", "Screenshots", "Download", "WhatsApp Images", "WhatsApp Video", "Pictures", "Movies", "DCIM", "Instagram", "Snapchat", "Telegram") }
    var activeOptionsBucket by remember { mutableStateOf<com.HrshD1eux.Gallery.data.media.BucketInfo?>(null) }
    var showDeleteAlbumConfirm by remember { mutableStateOf(false) }

    val visibleBuckets by viewModel.visibleBuckets.collectAsState()
    val pinnedBucketIds by viewModel.pinnedBucketIds.collectAsState()
    val vaultConfigVersion by viewModel.vaultConfigVersion.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()

    val pinnedBuckets = remember(visibleBuckets, pinnedBucketIds) {
        visibleBuckets.filter { pinnedBucketIds.contains(it.id.toString()) }
    }
    val unpinnedBuckets = remember(visibleBuckets, pinnedBucketIds) {
        visibleBuckets.filter { !pinnedBucketIds.contains(it.id.toString()) }
    }

    val vaultPrefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
    val isStealthMode = remember(vaultConfigVersion, isVaultUnlocked) { vaultPrefs.getBoolean("vault_stealth_mode", false) }
    val isVaultDisabled = remember(vaultConfigVersion, isVaultUnlocked) { vaultPrefs.getBoolean("vault_disabled", false) }
    val isPinConfigured = remember(vaultConfigVersion, isVaultUnlocked) {
        vaultPrefs.getString("vault_pin_hash", null) != null || vaultPrefs.getString("vault_pin", null) != null
    }

    val storageStats by viewModel.storageBreakdown.collectAsState()
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

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

        // Storage Overview Card
        item {
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage Overview 📊",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (trashed.isNotEmpty()) {
                            TextButton(
                                onClick = { showEmptyTrashConfirm = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Empty Trash", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(com.HrshD1eux.Gallery.core.util.FormatUtils.formatFileSize(storageStats.photosBytes), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Videos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(com.HrshD1eux.Gallery.core.util.FormatUtils.formatFileSize(storageStats.videosBytes), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Vault", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(com.HrshD1eux.Gallery.core.util.FormatUtils.formatFileSize(storageStats.vaultBytes), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Trash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(com.HrshD1eux.Gallery.core.util.FormatUtils.formatFileSize(storageStats.trashBytes), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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

        // Smart Album: Hidden (Only visible if Stealth Mode is disabled)
        if (!isStealthMode) {
            item {
                AlbumRowItem(
                    icon = if (isVaultDisabled || !isPinConfigured) Icons.Default.LockOpen else Icons.Default.Lock,
                    title = "Hidden Vault",
                    count = hidden.size,
                    onClick = {
                        if (isVaultDisabled || !isPinConfigured) {
                            showHiddenLockedDialog = true
                            return@AlbumRowItem
                        }

                        val activity = context as? android.app.Activity
                        val isBiometricEnabled = vaultPrefs.getBoolean("vault_biometric_enabled", false)
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

        // Smart Album: Find Duplicates
        item {
            AlbumRowItem(
                icon = Icons.Default.CleaningServices,
                title = "Find Duplicates 🧹",
                count = 0,
                subtitle = "Scan & clean duplicate photos",
                onClick = {
                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.DuplicateFinder
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
        }



        // Pinned Albums section
        if (pinnedBuckets.isNotEmpty()) {
            item {
                Text(
                    text = "Pinned Albums 📌",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(pinnedBuckets) { bucket ->
                AlbumRowItem(
                    icon = Icons.Default.Folder,
                    title = "${bucket.name} 📌",
                    count = bucket.count,
                    onClick = { viewModel.selectBucket(bucket.id, bucket.name) },
                    onLongClick = {
                        viewModel.togglePinBucket(bucket.id)
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            Text(
                text = "Device Folders",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (visibleBuckets.isEmpty()) {
            item {
                Text(
                    text = "No device folders found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(unpinnedBuckets) { bucket ->
                AlbumRowItem(
                    icon = Icons.Default.Folder,
                    title = bucket.name,
                    count = bucket.count,
                    onClick = {
                        viewModel.selectBucket(bucket.id, bucket.name)
                    },
                    onLongClick = {
                        activeOptionsBucket = bucket
                    }
                )
            }
        }
    }

    if (activeOptionsBucket != null) {
        val bucket = activeOptionsBucket!!
        val isCustomAlbum = !systemFolderNames.contains(bucket.name)
        val isPinned = pinnedBucketIds.contains(bucket.id.toString())

        AlertDialog(
            onDismissRequest = { activeOptionsBucket = null },
            title = { Text(bucket.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.togglePinBucket(bucket.id)
                            activeOptionsBucket = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isPinned) "Unpin Album 📌" else "Pin Album to Top 📌")
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.excludeBucket(bucket.id)
                            activeOptionsBucket = null
                            android.widget.Toast.makeText(context, "Album hidden from gallery", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exclude / Hide Album 🚫")
                        }
                    }

                    if (isCustomAlbum) {
                        TextButton(
                            onClick = {
                                activeOptionsBucket = null
                                viewModel.selectBucket(bucket.id, bucket.name)
                                viewModel.shareSelectedMedia(context, stripMetadata = true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Share Album 📤")
                            }
                        }

                        TextButton(
                            onClick = {
                                showDeleteAlbumConfirm = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Delete Custom Album 🗑️", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeOptionsBucket = null }) { Text("Close") }
            }
        )

        if (showEmptyTrashConfirm) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashConfirm = false },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Empty Trash?") },
                text = { Text("All items in Trash will be permanently deleted. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showEmptyTrashConfirm = false
                            viewModel.emptyTrash(context)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Empty Trash")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashConfirm = false }) { Text("Cancel") }
                }
            )
        }

        if (showDeleteAlbumConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAlbumConfirm = false },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Album '${bucket.name}'?") },
                text = { Text("All photos and videos inside '${bucket.name}' will be moved to Trash.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteAlbumConfirm = false
                            viewModel.deleteAlbum(context, bucket.id, bucket.name)
                            activeOptionsBucket = null
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Folder")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAlbumConfirm = false }) { Text("Cancel") }
                }
            )
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
            com.HrshD1eux.Gallery.ui.vault.VaultUnlockDialog(
                onDismiss = { showHiddenLockedDialog = false },
                onUnlockSuccess = {
                    viewModel.unlockVault()
                    showHiddenLockedDialog = false
                    viewModel.currentBucketId = null
                    viewModel.currentBucketName = null
                    viewModel.currentCategoryName = "Hidden Vault"
                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumRowItem(
    icon: ImageVector,
    title: String,
    count: Int,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (count > 0 || subtitle == null) {
            Text(
                text = if (subtitle != null && count == 0) "Scan" else count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

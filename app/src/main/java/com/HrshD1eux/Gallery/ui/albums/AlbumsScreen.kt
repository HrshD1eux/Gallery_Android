package com.HrshD1eux.Gallery.ui.albums

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.HrshD1eux.Gallery.data.media.BucketInfo
import com.HrshD1eux.Gallery.ui.MainViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsState()
    val trashed by viewModel.trashed.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val videosCount by viewModel.videosCount.collectAsState()
    val allMedia by viewModel.visibleMediaItems.collectAsState()

    val context = LocalContext.current
    var showHiddenLockedDialog by remember { mutableStateOf(false) }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    val systemFolderNames = remember { setOf("Camera", "Screenshots", "Download", "WhatsApp Images", "WhatsApp Video", "Pictures", "Movies", "DCIM", "Instagram", "Snapchat", "Telegram") }
    var activeOptionsBucket by remember { mutableStateOf<BucketInfo?>(null) }
    var showDeleteAlbumConfirm by remember { mutableStateOf(false) }

    val visibleBuckets by viewModel.visibleBuckets.collectAsState()
    val pinnedBucketIds by viewModel.pinnedBucketIds.collectAsState()
    val customAlbumCovers by viewModel.customAlbumCovers.collectAsState()
    val albumSortOrder by viewModel.albumSortOrder.collectAsState()
    val albumLayoutMode by viewModel.albumLayoutMode.collectAsState()
    val vaultConfigVersion by viewModel.vaultConfigVersion.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }

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

    val numCols = when (albumLayoutMode) {
        MainViewModel.AlbumLayoutMode.LIST -> 1
        MainViewModel.AlbumLayoutMode.GRID_2 -> 2
        MainViewModel.AlbumLayoutMode.GRID_3 -> 3
        MainViewModel.AlbumLayoutMode.GRID_4 -> 4
    }

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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage Overview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total: ${storageStats.formattedTotal}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Photos (${storageStats.photosCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = storageStats.formattedPhotos,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column {
                                Text(
                                    text = "Videos (${storageStats.videosCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = storageStats.formattedVideos,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column {
                                Text(
                                    text = "Vault (${storageStats.vaultCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = storageStats.formattedVault,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column {
                                Text(
                                    text = "Trash (${storageStats.trashCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = storageStats.formattedTrash,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
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
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Albums Header with Sort & Layout Switcher Controls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Albums (${visibleBuckets.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Layout Mode Dropdown
                        Box {
                            IconButton(onClick = { showLayoutMenu = true }) {
                                Icon(
                                    imageVector = if (numCols == 1) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Change Layout",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showLayoutMenu,
                                onDismissRequest = { showLayoutMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("List View") },
                                    onClick = {
                                        viewModel.setAlbumLayoutMode(MainViewModel.AlbumLayoutMode.LIST)
                                        showLayoutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Large Grid (2 Columns)") },
                                    onClick = {
                                        viewModel.setAlbumLayoutMode(MainViewModel.AlbumLayoutMode.GRID_2)
                                        showLayoutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Medium Grid (3 Columns)") },
                                    onClick = {
                                        viewModel.setAlbumLayoutMode(MainViewModel.AlbumLayoutMode.GRID_3)
                                        showLayoutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Small Grid (4 Columns)") },
                                    onClick = {
                                        viewModel.setAlbumLayoutMode(MainViewModel.AlbumLayoutMode.GRID_4)
                                        showLayoutMenu = false
                                    }
                                )
                            }
                        }

                        // Sort Order Dropdown
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort Albums",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (albumSortOrder == MainViewModel.AlbumSortOrder.NAME_ASC) "✓ Name (A to Z)" else "Name (A to Z)") },
                                    onClick = {
                                        viewModel.setAlbumSortOrder(MainViewModel.AlbumSortOrder.NAME_ASC)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (albumSortOrder == MainViewModel.AlbumSortOrder.COUNT_DESC) "✓ Item Count (Largest)" else "Item Count (Largest)") },
                                    onClick = {
                                        viewModel.setAlbumSortOrder(MainViewModel.AlbumSortOrder.COUNT_DESC)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (albumSortOrder == MainViewModel.AlbumSortOrder.RECENT) "✓ Recently Updated" else "Recently Updated") },
                                    onClick = {
                                        viewModel.setAlbumSortOrder(MainViewModel.AlbumSortOrder.RECENT)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Pinned Albums section
            if (pinnedBuckets.isNotEmpty()) {
                item {
                    Text(
                        text = "Pinned 📌",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (numCols == 1) {
                    items(pinnedBuckets) { bucket ->
                        val customMediaId = customAlbumCovers[bucket.id]
                        val coverUri = (if (customMediaId != null) allMedia.find { it.id == customMediaId }?.uri else null)
                            ?: allMedia.firstOrNull { it.bucketId == bucket.id }?.uri
                        AlbumRowItem(
                            icon = Icons.Default.Folder,
                            coverUri = coverUri,
                            title = "${bucket.name} 📌",
                            count = bucket.count,
                            onClick = { viewModel.selectBucket(bucket.id, bucket.name) },
                            onLongClick = { viewModel.togglePinBucket(bucket.id) }
                        )
                    }
                } else {
                    val chunkedPinned = pinnedBuckets.chunked(numCols)
                    items(chunkedPinned) { rowBuckets ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowBuckets.forEach { bucket ->
                                val customMediaId = customAlbumCovers[bucket.id]
                                val coverUri = (if (customMediaId != null) allMedia.find { it.id == customMediaId }?.uri else null)
                                    ?: allMedia.firstOrNull { it.bucketId == bucket.id }?.uri
                                Box(modifier = Modifier.weight(1f)) {
                                    AlbumGridCard(
                                        bucket = bucket,
                                        coverUri = coverUri,
                                        isPinned = true,
                                        onClick = { viewModel.selectBucket(bucket.id, bucket.name) },
                                        onLongClick = { viewModel.togglePinBucket(bucket.id) },
                                        layoutMode = albumLayoutMode
                                    )
                                }
                            }
                            for (i in rowBuckets.size until numCols) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (visibleBuckets.isEmpty()) {
                item {
                    Text(
                        text = "No albums found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (numCols == 1) {
                    items(unpinnedBuckets) { bucket ->
                        val customMediaId = customAlbumCovers[bucket.id]
                        val coverUri = (if (customMediaId != null) allMedia.find { it.id == customMediaId }?.uri else null)
                            ?: allMedia.firstOrNull { it.bucketId == bucket.id }?.uri
                        AlbumRowItem(
                            icon = Icons.Default.Folder,
                            coverUri = coverUri,
                            title = bucket.name,
                            count = bucket.count,
                            onClick = { viewModel.selectBucket(bucket.id, bucket.name) },
                            onLongClick = { activeOptionsBucket = bucket }
                        )
                    }
                } else {
                    val chunkedUnpinned = unpinnedBuckets.chunked(numCols)
                    items(chunkedUnpinned) { rowBuckets ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowBuckets.forEach { bucket ->
                                val customMediaId = customAlbumCovers[bucket.id]
                                val coverUri = (if (customMediaId != null) allMedia.find { it.id == customMediaId }?.uri else null)
                                    ?: allMedia.firstOrNull { it.bucketId == bucket.id }?.uri
                                Box(modifier = Modifier.weight(1f)) {
                                    AlbumGridCard(
                                        bucket = bucket,
                                        coverUri = coverUri,
                                        isPinned = false,
                                        onClick = { viewModel.selectBucket(bucket.id, bucket.name) },
                                        onLongClick = { activeOptionsBucket = bucket },
                                        layoutMode = albumLayoutMode
                                    )
                                }
                            }
                            for (i in rowBuckets.size until numCols) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGridCard(
    bucket: BucketInfo,
    coverUri: Uri?,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    layoutMode: MainViewModel.AlbumLayoutMode
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverUri != null) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = bucket.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (isPinned) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📌", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = bucket.name,
                    style = if (layoutMode == MainViewModel.AlbumLayoutMode.GRID_4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${bucket.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumRowItem(
    icon: ImageVector,
    coverUri: Uri? = null,
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
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
                    .padding(10.dp)
            )
        }
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

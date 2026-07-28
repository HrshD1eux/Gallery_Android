package com.HrshD1eux.Gallery.ui.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.HrshD1eux.Gallery.data.model.MediaItem
import com.HrshD1eux.Gallery.data.model.formattedDuration
import com.HrshD1eux.Gallery.data.model.isVideo
import com.HrshD1eux.Gallery.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {}
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Albums }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search photos, folders, dates...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                var showSecretVaultUnlockDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val prefs = remember(context) { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }
                val secretTrigger = remember(prefs) { prefs.getString("vault_secret_trigger", "#vault") ?: "#vault" }

                LaunchedEffect(searchQuery) {
                    val query = searchQuery.trim()
                    if (query.isNotEmpty() && query.equals(secretTrigger.trim(), ignoreCase = true)) {
                        val activity = context as? android.app.Activity
                        val isBiometricEnabled = prefs.getBoolean("vault_biometric_enabled", false)
                        if (isBiometricEnabled && activity != null) {
                            com.HrshD1eux.Gallery.core.util.BiometricAuthHelper.authenticate(
                                activity = activity,
                                title = "Unlock Hidden Vault",
                                subtitle = "Stealth Passphrase Matched",
                                onSuccess = {
                                    viewModel.setSearchQuery("")
                                    viewModel.unlockVault()
                                    viewModel.currentBucketId = null
                                    viewModel.currentBucketName = null
                                    viewModel.currentCategoryName = "Hidden Vault"
                                    viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                                },
                                onError = { _ ->
                                    showSecretVaultUnlockDialog = true
                                }
                            )
                        } else {
                            showSecretVaultUnlockDialog = true
                        }
                    }
                }

                if (showSecretVaultUnlockDialog) {
                    com.HrshD1eux.Gallery.ui.vault.VaultUnlockDialog(
                        onDismiss = {
                            showSecretVaultUnlockDialog = false
                            viewModel.setSearchQuery("")
                        },
                        onUnlockSuccess = {
                            showSecretVaultUnlockDialog = false
                            viewModel.setSearchQuery("")
                            viewModel.unlockVault()
                            viewModel.currentBucketId = null
                            viewModel.currentBucketName = null
                            viewModel.currentCategoryName = "Hidden Vault"
                            viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Photos
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type a filename, folder, or format to search",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                Text(
                    text = "Found ${searchResults.size} result${if (searchResults.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching media found for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            SearchResultGridCell(
                                item = item,
                                onClick = { viewModel.activeMediaItem = item }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultGridCell(
    item: MediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri) {
        coil.request.ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .size(280, 280)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .precision(coil.size.Precision.INEXACT)
            .error(android.R.drawable.ic_menu_report_image)
            .fallback(android.R.drawable.ic_menu_report_image)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (item is MediaItem.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.height(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = item.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

package com.HrshD1eux.Gallery.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.HrshD1eux.Gallery.ui.albums.AlbumsScreen
import com.HrshD1eux.Gallery.ui.search.SearchScreen
import com.HrshD1eux.Gallery.ui.theme.GalleryTheme
import com.HrshD1eux.Gallery.ui.timeline.TimelineScreen
import com.HrshD1eux.Gallery.ui.viewer.PhotoViewerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
    }

    private var hasPermissionsState = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val readGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasFullImages = results[Manifest.permission.READ_MEDIA_IMAGES] == true
            val hasFullVideos = results[Manifest.permission.READ_MEDIA_VIDEO] == true
            val hasPartial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                results[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
            } else false
            (hasFullImages && hasFullVideos) || hasPartial
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        hasPermissionsState.value = readGranted
        if (readGranted) {
            viewModel.loadMediaStream()
            viewModel.loadBuckets()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            val appTheme = viewModel.appTheme
            val isDarkTheme = when (appTheme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            GalleryTheme(darkTheme = isDarkTheme) {
                val hasPermissions by hasPermissionsState

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasPermissions) {
                        MainScreenLayout(viewModel)
                        
                        val editingItem = viewModel.editingMediaItem
                        if (editingItem != null) {
                            com.HrshD1eux.Gallery.ui.editor.PhotoEditorScreen(
                                viewModel = viewModel,
                                mediaItem = editingItem,
                                onDismiss = { viewModel.editingMediaItem = null }
                            )
                        }
                    } else {
                        PermissionFallbackScreen(
                            onRequestPermissions = {
                                requestPermissionLauncher.launch(requiredPermissions)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        if (hasPermissionsState.value) {
            viewModel.refreshAll()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activeItem = viewModel.activeMediaItem
            if (activeItem is com.HrshD1eux.Gallery.data.model.MediaItem.Video) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (_: Exception) {}
            }
        }
    }

    private fun checkPermissions() {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasFullImages = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasFullVideos = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasPartial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            } else false
            (hasFullImages && hasFullVideos) || hasPartial
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        hasPermissionsState.value = isGranted
        if (isGranted) {
            viewModel.refreshAll()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.handleActivityResult(requestCode, resultCode, this)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreenLayout(viewModel: MainViewModel) {
    val selectionState = viewModel.selectionState
    val currentScreen = viewModel.currentScreen
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSelectionShareDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var stripMetadataOnShare by remember { androidx.compose.runtime.mutableStateOf(true) }
    var showMoveToAlbumDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val isVaultActive = (isVaultUnlocked && viewModel.currentCategoryName == "Hidden Vault") ||
            viewModel.activeMediaItem?.isHidden == true
    val activity = context as? android.app.Activity

    DisposableEffect(isVaultActive) {
        if (isVaultActive) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                viewModel.lockVault(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val screens = remember { listOf(Screen.Photos, Screen.Albums, Screen.Settings) }
    
    val backEnabled = viewModel.activeMediaItem != null ||
            viewModel.currentCategoryName != null ||
            viewModel.currentBucketId != null ||
            currentScreen != Screen.Photos

    BackHandler(enabled = backEnabled) {
        if (viewModel.activeMediaItem != null) {
            viewModel.activeMediaItem = null
        } else if (viewModel.currentCategoryName != null) {
            if (viewModel.currentCategoryName == "Hidden Vault") {
                viewModel.lockVault(context)
            } else {
                viewModel.currentCategoryName = null
                viewModel.currentScreen = Screen.Albums
            }
        } else if (viewModel.currentBucketId != null) {
            viewModel.selectBucket(null, null)
            viewModel.currentScreen = Screen.Albums
        } else if (currentScreen != Screen.Photos) {
            viewModel.currentScreen = Screen.Photos
        }
    }
    val mainPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { screens.size }
    )

    // Sync from screen change to pager page (instant smooth snap)
    LaunchedEffect(currentScreen) {
        if (currentScreen != Screen.Search) {
            val page = screens.indexOf(currentScreen)
            if (page >= 0 && mainPagerState.currentPage != page) {
                mainPagerState.scrollToPage(page)
            }
        }
    }

    // Sync from user swipe gesture to screen change when settled
    LaunchedEffect(mainPagerState.settledPage) {
        if (currentScreen != Screen.Search) {
            val targetScreen = screens.getOrNull(mainPagerState.settledPage)
            if (targetScreen != null && viewModel.currentScreen != targetScreen) {
                if (viewModel.currentCategoryName == "Hidden Vault" && targetScreen != Screen.Photos) {
                    viewModel.lockVault(context)
                }
                viewModel.currentScreen = targetScreen
            }
        }
    }

    Scaffold(
        topBar = {
            if (viewModel.activeMediaItem == null && currentScreen != Screen.DuplicateFinder && currentScreen != Screen.Search) {
                TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.currentCategoryName != null && currentScreen == Screen.Photos) {
                            viewModel.currentCategoryName!!
                        } else if (viewModel.currentBucketName != null && currentScreen == Screen.Photos) {
                            viewModel.currentBucketName!!
                        } else {
                            currentScreen.name
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (viewModel.currentCategoryName != null && currentScreen == Screen.Photos) {
                        IconButton(onClick = { 
                            if (viewModel.currentCategoryName == "Hidden Vault") {
                                viewModel.lockVault(context)
                            } else {
                                viewModel.currentCategoryName = null 
                                viewModel.currentScreen = Screen.Albums
                            }
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All Media")
                        }
                    } else if (viewModel.currentBucketId != null && currentScreen == Screen.Photos) {
                        IconButton(onClick = { 
                            viewModel.selectBucket(null, null) 
                            viewModel.currentScreen = Screen.Albums
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All Media")
                        }
                    }
                },
                actions = {
                    if (currentScreen == Screen.Photos) {
                        IconButton(onClick = { viewModel.toggleGridStyle() }) {
                            Icon(
                                imageVector = if (viewModel.gridStyle == GridStyle.SQUARE) {
                                    Icons.Default.CropSquare
                                } else {
                                    Icons.Default.GridView
                                },
                                contentDescription = "Toggle Grid Style"
                            )
                        }

                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.SwapVert,
                                contentDescription = if (viewModel.sortOrder == SortOrder.NEWEST_FIRST) "Newest First" else "Oldest First",
                                tint = if (viewModel.sortOrder == SortOrder.OLDEST_FIRST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { viewModel.toggleSortMode() }) {
                            Icon(
                                imageVector = if (viewModel.sortMode == TimelineSortMode.DATE_GROUPED) {
                                    Icons.Default.DateRange
                                } else {
                                    Icons.Default.ViewStream
                                },
                                contentDescription = "Toggle Sort Mode"
                            )
                        }

                        val categoryName by viewModel.currentCategoryNameFlow.collectAsState()
                        if (categoryName == "Trash") {
                            var showEmptyTrashConfirm by remember { mutableStateOf(false) }

                            IconButton(onClick = { showEmptyTrashConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Empty Trash",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            if (showEmptyTrashConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showEmptyTrashConfirm = false },
                                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    title = { Text("Empty Trash?") },
                                    text = { Text("All items in Trash will be permanently deleted from your device storage. This action cannot be undone.") },
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
                                        TextButton(onClick = { showEmptyTrashConfirm = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }

                        if (categoryName == "Hidden Vault") {
                            var showVaultMenu by remember { mutableStateOf(false) }
                            var showVaultSecurityDialog by remember { mutableStateOf(false) }

                            IconButton(onClick = { showVaultMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Vault Security Options"
                                )
                            }

                            DropdownMenu(
                                expanded = showVaultMenu,
                                onDismissRequest = { showVaultMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Vault Security Settings 🔒") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    onClick = {
                                        showVaultMenu = false
                                        showVaultSecurityDialog = true
                                    }
                                )
                            }

                            if (showVaultSecurityDialog) {
                                com.HrshD1eux.Gallery.ui.vault.VaultSecurityDialog(
                                    viewModel = viewModel,
                                    onDismiss = { showVaultSecurityDialog = false },
                                    onVaultDeleted = {
                                        showVaultSecurityDialog = false
                                        viewModel.currentCategoryName = null
                                        viewModel.currentScreen = com.HrshD1eux.Gallery.ui.Screen.Albums
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    },
        bottomBar = {
            if (viewModel.activeMediaItem == null && currentScreen != Screen.DuplicateFinder && currentScreen != Screen.Search) {
                AnimatedContent(
                targetState = selectionState.inSelectionMode,
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { it }
                },
                label = "BottomBarTransition"
            ) { inSelection ->
                if (inSelection) {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 8.dp
                    ) {
                        val categoryName by viewModel.currentCategoryNameFlow.collectAsState()
                        val trashedList by viewModel.trashed.collectAsState()
                        val selectedIds = selectionState.selectedIds
                        val isViewingTrash = categoryName == "Trash" || (selectedIds.isNotEmpty() && trashedList.any { selectedIds.contains(it.id) })
                        val isViewingVault = categoryName == "Hidden Vault"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selection count & Deselect chip
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { selectionState.clear() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Deselect",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${selectionState.selectedIds.size} Selected",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            if (isViewingTrash) {
                                SelectionActionButton(
                                    icon = Icons.Default.RestoreFromTrash,
                                    label = "Restore",
                                    onClick = { viewModel.restoreSelectedMedia() }
                                )
                                SelectionActionButton(
                                    icon = Icons.Default.DeleteForever,
                                    label = "Delete Forever",
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { viewModel.deleteSelectedMediaPermanently(context) }
                                )
                            } else {
                                SelectionActionButton(
                                    icon = Icons.Default.PlayArrow,
                                    label = "Slideshow",
                                    onClick = {
                                        val selectedIdsSet = selectionState.selectedIds.toSet()
                                        val itemsToPlay = viewModel.visibleMediaItems.value.filter { selectedIdsSet.contains(it.id) }
                                        if (itemsToPlay.isNotEmpty()) {
                                            viewModel.activeMediaItem = itemsToPlay.first()
                                        }
                                    }
                                )

                                SelectionActionButton(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    onClick = { showSelectionShareDialog = true }
                                )

                                SelectionActionButton(
                                    icon = Icons.Default.PictureAsPdf,
                                    label = "Create PDF",
                                    onClick = {
                                        val selectedIdsSet = selectionState.selectedIds.toSet()
                                        scope.launch {
                                            val itemsToExport = viewModel.getSelectedMediaItems(selectedIdsSet)
                                            if (itemsToExport.isNotEmpty()) {
                                                android.widget.Toast.makeText(context, "Generating PDF...", android.widget.Toast.LENGTH_SHORT).show()
                                                val pdfUri = com.HrshD1eux.Gallery.core.util.PdfConverter.createPdfFromImages(context, itemsToExport)
                                                if (pdfUri != null) {
                                                    com.HrshD1eux.Gallery.core.util.HapticUtil.performSuccess(context)
                                                    com.HrshD1eux.Gallery.core.util.PdfConverter.sharePdf(context, pdfUri)
                                                } else {
                                                    com.HrshD1eux.Gallery.core.util.HapticUtil.performError(context)
                                                    android.widget.Toast.makeText(context, "Could not create PDF from selected items", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, "No photos selected for PDF", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )

                                SelectionActionButton(
                                    icon = if (isViewingVault) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    label = if (isViewingVault) "Unhide" else "Vault",
                                    onClick = { viewModel.hideSelectedMedia(context) }
                                )

                                if (!isViewingVault) {
                                    SelectionActionButton(
                                        icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                        label = "Move",
                                        onClick = { showMoveToAlbumDialog = true }
                                    )
                                }

                                SelectionActionButton(
                                    icon = Icons.Default.Delete,
                                    label = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { viewModel.deleteSelectedMedia(context) }
                                )
                            }
                        }
                    }
                } else {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == Screen.Photos,
                            onClick = { viewModel.currentScreen = Screen.Photos },
                            icon = { Icon(Icons.Default.Photo, contentDescription = "Photos") },
                            label = { Text("Photos") }
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.Albums,
                            onClick = { 
                                viewModel.currentScreen = Screen.Albums
                                viewModel.loadBuckets()
                            },
                            icon = { Icon(Icons.Default.Album, contentDescription = "Albums") },
                            label = { Text("Albums") }
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.Settings,
                            onClick = { viewModel.currentScreen = Screen.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        }
    }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = mainPagerState,
                beyondBoundsPageCount = 2,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = viewModel.activeMediaItem == null
            ) { page ->
                when (screens[page]) {
                    Screen.Photos -> TimelineScreen(viewModel = viewModel)
                    Screen.Albums -> AlbumsScreen(viewModel = viewModel)
                    Screen.Settings -> com.HrshD1eux.Gallery.ui.settings.SettingsScreen(viewModel = viewModel)
                    else -> TimelineScreen(viewModel = viewModel)
                }
            }

            AnimatedVisibility(
                visible = currentScreen == Screen.Search,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                SearchScreen(viewModel = viewModel)
            }

            AnimatedVisibility(
                visible = currentScreen == Screen.DuplicateFinder,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                com.HrshD1eux.Gallery.ui.search.DuplicateFinderScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.currentScreen = Screen.Albums }
                )
            }

            AnimatedVisibility(
                visible = viewModel.activeMediaItem != null,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f)
            ) {
                PhotoViewerScreen(viewModel = viewModel)
            }
        }
    }

    if (showSelectionShareDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionShareDialog = false },
            title = { Text("Share Selected Files") },
            text = {
                Column {
                    Text("Strip location and device identifiers (EXIF metadata) to secure your privacy before sharing.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = stripMetadataOnShare,
                            onCheckedChange = { stripMetadataOnShare = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Strip EXIF & Location tags")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSelectionShareDialog = false
                        viewModel.shareSelectedMedia(context, stripMetadataOnShare)
                    }
                ) {
                    Text("Share Securely")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectionShareDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showMoveToAlbumDialog) {
        var createNewAlbumInMove by remember { mutableStateOf(false) }
        var tempNewAlbumName by remember { mutableStateOf("") }
        val bucketList by viewModel.buckets.collectAsState()
        
        AlertDialog(
            onDismissRequest = { 
                showMoveToAlbumDialog = false 
                createNewAlbumInMove = false
                tempNewAlbumName = ""
            },
            title = { Text("Move to Album") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (createNewAlbumInMove) {
                        OutlinedTextField(
                            value = tempNewAlbumName,
                            onValueChange = { tempNewAlbumName = it },
                            label = { Text("New Album Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            item {
                                TextButton(
                                    onClick = { createNewAlbumInMove = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("+ Create New Album")
                                }
                            }
                            items(bucketList) { bucket ->
                                TextButton(
                                    onClick = {
                                        viewModel.moveSelectedMediaToFolder(context, bucket.name)
                                        showMoveToAlbumDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(bucket.name)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (createNewAlbumInMove) {
                    Button(
                        onClick = {
                            if (tempNewAlbumName.isNotBlank()) {
                                viewModel.moveSelectedMediaToFolder(context, tempNewAlbumName.trim())
                                showMoveToAlbumDialog = false
                                createNewAlbumInMove = false
                                tempNewAlbumName = ""
                            }
                        }
                    ) {
                        Text("Move")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMoveToAlbumDialog = false
                        createNewAlbumInMove = false
                        tempNewAlbumName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionFallbackScreen(onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Privacy First Media Access",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To list your items local-first, the app queries your device's media storage database directly. No internet telemetry is requested.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRequestPermissions) {
                Text("Allow Local Access")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("App Settings")
            }
        }
    }
}

@Composable
fun SelectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

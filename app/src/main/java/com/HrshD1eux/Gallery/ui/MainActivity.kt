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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.VisibilityOff
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
            GalleryTheme {
                val hasPermissions by hasPermissionsState
                val activeItem = viewModel.activeMediaItem

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasPermissions) {
                        MainScreenLayout(viewModel)
                        
                        if (activeItem != null) {
                            PhotoViewerScreen(viewModel = viewModel)
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
            viewModel.loadMediaStream()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.handleActivityResult(requestCode, resultCode)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreenLayout(viewModel: MainViewModel) {
    val selectionState = viewModel.selectionState
    val currentScreen = viewModel.currentScreen
    val context = LocalContext.current
    var showSelectionShareDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var stripMetadataOnShare by remember { androidx.compose.runtime.mutableStateOf(true) }
    var showMoveToAlbumDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (viewModel.currentCategoryName == "Hidden Vault") {
                    viewModel.currentCategoryName = null
                    viewModel.currentScreen = Screen.Albums
                }
                viewModel.clearVaultCache(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val screens = remember { listOf(Screen.Photos, Screen.Albums, Screen.Search) }
    
    val backEnabled = viewModel.activeMediaItem != null ||
            viewModel.currentCategoryName != null ||
            viewModel.currentBucketId != null ||
            currentScreen != Screen.Photos

    BackHandler(enabled = backEnabled) {
        if (viewModel.activeMediaItem != null) {
            viewModel.activeMediaItem = null
        } else if (viewModel.currentCategoryName != null) {
            viewModel.currentCategoryName = null
            viewModel.currentScreen = Screen.Albums
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

    // Sync from screen change to pager page
    LaunchedEffect(currentScreen) {
        val page = screens.indexOf(currentScreen).coerceAtLeast(0)
        if (mainPagerState.currentPage != page) {
            mainPagerState.animateScrollToPage(page)
        }
    }

    // Sync from pager page to screen change
    LaunchedEffect(mainPagerState.currentPage) {
        val targetScreen = screens.getOrNull(mainPagerState.currentPage)
        if (targetScreen != null && viewModel.currentScreen != targetScreen) {
            viewModel.currentScreen = targetScreen
            if (targetScreen == Screen.Albums) {
                viewModel.loadBuckets()
            }
        }
    }

    Scaffold(
        topBar = {
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
                            viewModel.currentCategoryName = null 
                            viewModel.currentScreen = Screen.Albums
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AnimatedContent(
                targetState = selectionState.inSelectionMode,
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { it }
                },
                label = "BottomBarTransition"
            ) { inSelection ->
                if (inSelection) {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectionState.selectedIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row {
                                IconButton(onClick = { showSelectionShareDialog = true }) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                                }
                                IconButton(onClick = {
                                    val selected = viewModel.mediaItems.value.filter { selectionState.selectedIds.contains(it.id) }
                                    selected.forEach { viewModel.toggleHidden(context, it) }
                                    selectionState.clear()
                                }) {
                                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = "Hide")
                                }
                                 IconButton(onClick = { showMoveToAlbumDialog = true }) {
                                    Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = "Move to Album")
                                }
                                IconButton(onClick = {
                                    val selected = viewModel.mediaItems.value.filter { selectionState.selectedIds.contains(it.id) }
                                    selected.forEach { viewModel.toggleTrashed(context, it) }
                                    selectionState.clear()
                                }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                                }
                                IconButton(onClick = { selectionState.clear() }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                                }
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
                            selected = currentScreen == Screen.Search,
                            onClick = { viewModel.currentScreen = Screen.Search },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") }
                        )
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
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = viewModel.activeMediaItem == null
            ) { page ->
                when (screens[page]) {
                    Screen.Photos -> TimelineScreen(viewModel = viewModel)
                    Screen.Albums -> AlbumsScreen(viewModel = viewModel)
                    Screen.Search -> SearchScreen()
                }
            }

            AnimatedVisibility(
                visible = viewModel.activeMediaItem != null,
                enter = fadeIn(),
                exit = fadeOut()
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
                                        val selected = viewModel.mediaItems.value.filter { selectionState.selectedIds.contains(it.id) }
                                        viewModel.moveMediaToFolder(context, selected, bucket.name)
                                        selectionState.clear()
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
                                val selected = viewModel.mediaItems.value.filter { selectionState.selectedIds.contains(it.id) }
                                viewModel.moveMediaToFolder(context, selected, tempNewAlbumName.trim())
                                selectionState.clear()
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

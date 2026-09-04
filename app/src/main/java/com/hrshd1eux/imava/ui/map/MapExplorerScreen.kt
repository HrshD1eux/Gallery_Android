package com.hrshd1eux.imava.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.viewinterop.AndroidView
import com.hrshd1eux.imava.core.util.ExifLocationUtil
import com.hrshd1eux.imava.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MapPhotoPoint(
    val mediaId: Long,
    val uri: String,
    val lat: Double,
    val lng: Double,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapExplorerScreen(
    mediaItems: List<MediaItem>,
    onBack: () -> Unit,
    onPhotoClick: (Long) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var photoPoints by remember { mutableStateOf<List<MapPhotoPoint>>(emptyList()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(mediaItems) {
        withContext(Dispatchers.IO) {
            val points = mutableListOf<MapPhotoPoint>()
            mediaItems.take(500).forEach { item ->
                val geo = ExifLocationUtil.getGeotag(context, item.uri, item.path)
                if (geo != null) {
                    points.add(
                        MapPhotoPoint(
                            mediaId = item.id,
                            uri = item.uri.toString(),
                            lat = geo.latitude,
                            lng = geo.longitude,
                            title = item.path.substringAfterLast("/")
                        )
                    )
                }
            }
            photoPoints = points
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photo Map 🗺️", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (isLoading) "Scanning GPS tags..." else "${photoPoints.size} photos located",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (photoPoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PinDrop,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "No GPS tags found in your photos",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Photos taken with camera location enabled will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createLeafletWebView(ctx, photoPoints, onPhotoClick).also {
                            webViewRef = it
                        }
                    },
                    update = { webView ->
                        // update markers if points changed
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createLeafletWebView(
    context: Context,
    points: List<MapPhotoPoint>,
    onPhotoClick: (Long) -> Unit
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        class WebAppInterface {
            @JavascriptInterface
            fun onMarkerClicked(id: Long) {
                post { onPhotoClick(id) }
            }
        }
        addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        val jsonArray = JSONArray().apply {
            points.forEach { pt ->
                put(
                    JSONObject().apply {
                        put("id", pt.mediaId)
                        put("lat", pt.lat)
                        put("lng", pt.lng)
                        put("title", pt.title)
                    }
                )
            }
        }

        val htmlContent = buildLeafletHtml(jsonArray.toString())
        loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
    }
}

private fun buildLeafletHtml(jsonPoints: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <style>
        body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #121212; }
        .custom-pin {
            background: #FF5722;
            border: 2px solid #FFFFFF;
            border-radius: 50%;
            width: 14px;
            height: 14px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.5);
            cursor: pointer;
        }
        .leaflet-popup-content { font-family: sans-serif; font-size: 13px; text-align: center; }
        .popup-btn {
            background: #2196F3;
            color: white;
            border: none;
            padding: 6px 12px;
            border-radius: 4px;
            cursor: pointer;
            margin-top: 6px;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <script>
        const points = $jsonPoints;
        const initialLat = points.length > 0 ? points[0].lat : 20.0;
        const initialLng = points.length > 0 ? points[0].lng : 0.0;
        const map = L.map('map', { zoomControl: true }).setView([initialLat, initialLng], points.length > 0 ? 10 : 2);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '© OpenStreetMap'
        }).addTo(map);

        const markers = [];
        points.forEach(pt => {
            const marker = L.circleMarker([pt.lat, pt.lng], {
                radius: 8,
                fillColor: '#FF3D00',
                color: '#FFFFFF',
                weight: 2,
                opacity: 1,
                fillOpacity: 0.9
            }).addTo(map);

            marker.bindPopup(`
                <div>
                    <b>${'$'}{pt.title}</b><br/>
                    <button class="popup-btn" onclick="AndroidBridge.onMarkerClicked(${'$'}{pt.id})">Open Photo</button>
                </div>
            `);
            markers.push([pt.lat, pt.lng]);
        });

        if (markers.length > 1) {
            const bounds = L.latLngBounds(markers);
            map.fitBounds(bounds, { padding: [40, 40] });
        }
    </script>
</body>
</html>
    """.trimIndent()
}

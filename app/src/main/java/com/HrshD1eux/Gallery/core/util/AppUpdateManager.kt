package com.HrshD1eux.Gallery.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?
)

object AppUpdateManager {

    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.3"
        } catch (_: Exception) {
            "1.1.3"
        }

        val githubApiUrls = listOf(
            "https://api.github.com/repos/HrshD1eux/Gallery_Android/releases/latest",
            "https://api.github.com/repos/HrshD1eux/Gallery/releases/latest"
        )

        var jsonResponse: String? = null
        for (apiUrl in githubApiUrls) {
            try {
                val url = URL(apiUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Gallery-App")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                if (connection.responseCode == 200) {
                    jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (jsonResponse == null) {
            return@withContext UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                releaseNotes = "Could not check GitHub releases. Please check your internet connection.",
                apkDownloadUrl = null
            )
        }

        try {
            val json = JSONObject(jsonResponse)
            val rawTag = json.optString("tag_name", "v1.0.0")
            val latestVersion = rawTag.removePrefix("v").trim()
            val body = json.optString("body", "No release notes available.")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val browserUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = browserUrl
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(latestVersion, currentVersion)
            UpdateInfo(
                hasUpdate = isNewer && !apkUrl.isNullOrEmpty(),
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                releaseNotes = body,
                apkDownloadUrl = apkUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                releaseNotes = "Error parsing release info.",
                apkDownloadUrl = null
            )
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }

            val totalSize = connection.contentLength
            var downloaded = 0

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            val percent = ((downloaded.toLong() * 100) / totalSize).toInt()
                            onProgress(percent)
                        }
                    }
                }
            }

            if (apkFile.exists() && apkFile.length() > 0) {
                installApk(context, apkFile)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

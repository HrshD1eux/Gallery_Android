package com.hrshd1eux.imava.core.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperUtil {

    const val WALLPAPER_HOME = WallpaperManager.FLAG_SYSTEM
    const val WALLPAPER_LOCK = WallpaperManager.FLAG_LOCK
    const val WALLPAPER_BOTH = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK

    suspend fun setWallpaperDirect(context: Context, uri: Uri, which: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        wallpaperManager.setStream(stream, null, true, which)
                        return@withContext true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(bitmap, null, true, which)
                    } else {
                        wallpaperManager.setBitmap(bitmap)
                    }
                    bitmap.recycle()
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun launchPixelOrSystemWallpaperPicker(activity: Activity, uri: Uri): Boolean {
        // Attempt direct launch of Pixel Wallpaper Cropper / Picker first
        val pixelPackages = listOf(
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.nexus",
            "com.android.wallpaper"
        )
        for (pkg in pixelPackages) {
            try {
                val intent = Intent(WallpaperManager.ACTION_CROP_AND_SET_WALLPAPER).apply {
                    setDataAndType(uri, "image/*")
                    putExtra("mimeType", "image/*")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Fallback to system crop & set wallpaper intent
        return try {
            val wallpaperManager = WallpaperManager.getInstance(activity)
            val intent = wallpaperManager.getCropAndSetWallpaperIntent(uri)
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(uri, "image/*")
                    putExtra("mimeType", "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(intent, "Set Wallpaper With"))
                true
            } catch (e2: Exception) {
                e2.printStackTrace()
                false
            }
        }
    }

    fun setAsContactPhoto(activity: Activity, uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                setDataAndType(uri, "image/*")
                putExtra("mimeType", "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Set as Contact Photo"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

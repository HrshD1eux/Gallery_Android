package com.hrshd1eux.imava.core.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperUtil {

    fun openSystemWallpaperCropper(activity: Activity, uri: Uri): Boolean {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(activity)
            val intent = wallpaperManager.getCropAndSetWallpaperIntent(uri)
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                // Fallback: Generic Attach Data intent
                val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(uri, "image/*")
                    putExtra("mimeType", "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(intent, "Set as"))
                true
            } catch (e2: Exception) {
                e2.printStackTrace()
                false
            }
        }
    }

    suspend fun setWallpaperDirect(context: Context, uri: Uri, which: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(bitmap, null, true, which)
                    } else {
                        wallpaperManager.setBitmap(bitmap)
                    }
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

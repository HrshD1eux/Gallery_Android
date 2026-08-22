package com.hrshd1eux.imava

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ImavaApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        scheduleTrashAutoPurge()
        scheduleAppUpdateChecker()
    }

    private fun scheduleTrashAutoPurge() {
        try {
            val purgeRequest = androidx.work.PeriodicWorkRequestBuilder<com.hrshd1eux.imava.core.worker.TrashAutoPurgeWorker>(
                1, java.util.concurrent.TimeUnit.DAYS
            ).build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "TrashAutoPurgeWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                purgeRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleAppUpdateChecker() {
        try {
            val updateCheckRequest = androidx.work.PeriodicWorkRequestBuilder<com.hrshd1eux.imava.core.worker.AppUpdateCheckWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ImavaAppUpdateCheckWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                updateCheckRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(com.hrshd1eux.imava.core.util.VaultFetcher.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}

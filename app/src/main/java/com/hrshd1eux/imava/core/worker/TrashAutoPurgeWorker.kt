package com.hrshd1eux.imava.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hrshd1eux.imava.data.database.GalleryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TrashAutoPurgeWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = GalleryDatabase.getInstance(applicationContext)
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            val expiredItems = db.metadataDao().getExpiredTrashItems(cutoff)

            expiredItems.forEach { entity ->
                // Delete physical file if exists on disk
                if (!entity.originalPath.isNullOrEmpty()) {
                    val file = File(entity.originalPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                db.metadataDao().deleteByMediaId(entity.mediaId)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

package com.hrshd1eux.imava.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hrshd1eux.imava.R
import com.hrshd1eux.imava.core.util.AppUpdateManager
import com.hrshd1eux.imava.ui.MainActivity

class AppUpdateCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "imava_app_updates"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val isAutoCheckEnabled = prefs.getBoolean("auto_check_updates", true)
        if (!isAutoCheckEnabled) {
            return Result.success()
        }

        return try {
            val updateInfo = AppUpdateManager.checkForUpdates(applicationContext)
            if (updateInfo.hasUpdate) {
                showUpdateNotification(updateInfo.latestVersion, updateInfo.currentVersion, updateInfo.apkDownloadUrl)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showUpdateNotification(latestVersion: String, currentVersion: String, downloadUrl: String?) {
        val context = applicationContext
        createNotificationChannel(context)

        val intent = if (!downloadUrl.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showMigration = AppUpdateManager.isVersionOlderThan(currentVersion, "1.1.8")
        val message = if (showMigration) {
            "Imava v$latestVersion is available.\n\n⚠️ Migration Note: Because of our transition to the new package ID (com.hrshd1eux.imava), please download the APK, install it, and delete the older version manually."
        } else {
            "Imava v$latestVersion is available. Tap to download and install."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Imava Update Available: v$latestVersion")
            .setContentText("A new version is available. Tap to download and install.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Updates"
            val descriptionText = "Notifications for new Imava app releases"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

package com.hrshd1eux.imava.core.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLockManager {

    private const val PREFS_NAME = "imava_app_lock_prefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_LOCKED_BUCKETS = "locked_bucket_ids"
    private const val KEY_LOCKED_NAMES = "locked_bucket_names"
    private const val KEY_LOCKED_PATHS = "locked_folder_paths"

    private val _lockStateVersion = MutableStateFlow(0)
    val lockStateVersion: StateFlow<Int> = _lockStateVersion.asStateFlow()

    private fun notifyLockStateChanged() {
        _lockStateVersion.value += 1
    }

    // Session cache (resets when app is closed / killed)
    private var isAppUnlockedInSession = false
    private val sessionUnlockedBuckets = mutableSetOf<Long>()
    private var lastBackgroundTimestamp = 0L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            isAppUnlockedInSession = true
        }
        notifyLockStateChanged()
    }

    fun isAppUnlockedForSession(): Boolean {
        return isAppUnlockedInSession
    }

    fun markAppUnlocked() {
        isAppUnlockedInSession = true
        notifyLockStateChanged()
    }

    fun onAppResume() {
        if (lastBackgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
            // Re-lock if backgrounded for more than 30 seconds
            if (elapsed > 30_000L) {
                isAppUnlockedInSession = false
                sessionUnlockedBuckets.clear()
                notifyLockStateChanged()
            }
        }
    }

    fun onAppPause() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun isAlbumLocked(context: Context, bucketId: Long, bucketName: String? = null): Boolean {
        val lockedIds = getLockedBucketIds(context)
        val lockedNames = getLockedBucketNames(context)
        val isConfigured = bucketId in lockedIds || (bucketName != null && bucketName.lowercase() in lockedNames)
        return isConfigured && bucketId !in sessionUnlockedBuckets
    }

    fun isAlbumConfiguredLocked(context: Context, bucketId: Long, bucketName: String? = null): Boolean {
        val lockedIds = getLockedBucketIds(context)
        val lockedNames = getLockedBucketNames(context)
        return bucketId in lockedIds || (bucketName != null && bucketName.lowercase() in lockedNames)
    }

    fun isMediaItemLocked(context: Context, bucketId: Long, bucketName: String? = null): Boolean {
        return isAlbumConfiguredLocked(context, bucketId, bucketName)
    }

    fun lockAlbum(context: Context, bucketId: Long, bucketName: String? = null, folderPath: String? = null) {
        val currentIds = getLockedBucketIds(context).toMutableSet().apply { add(bucketId) }
        saveLockedBucketIds(context, currentIds)

        if (!bucketName.isNullOrBlank()) {
            val currentNames = getLockedBucketNames(context).toMutableSet().apply { add(bucketName.trim().lowercase()) }
            saveLockedBucketNames(context, currentNames)
        }

        if (!folderPath.isNullOrBlank()) {
            val currentPaths = getLockedFolderPaths(context).toMutableSet().apply { add(folderPath) }
            saveLockedFolderPaths(context, currentPaths)

            // Create .nomedia in folder so Android system and other apps remove it from system media
            try {
                val dir = java.io.File(folderPath)
                if (dir.exists() && dir.isDirectory) {
                    val noMedia = java.io.File(dir, ".nomedia")
                    if (!noMedia.exists()) {
                        noMedia.createNewFile()
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(noMedia.absolutePath, dir.absolutePath),
                            null,
                            null
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        sessionUnlockedBuckets.remove(bucketId)
        notifyLockStateChanged()
    }

    fun unlockAlbum(context: Context, bucketId: Long, bucketName: String? = null, folderPath: String? = null) {
        val currentIds = getLockedBucketIds(context).toMutableSet().apply { remove(bucketId) }
        saveLockedBucketIds(context, currentIds)

        if (!bucketName.isNullOrBlank()) {
            val currentNames = getLockedBucketNames(context).toMutableSet().apply { remove(bucketName.trim().lowercase()) }
            saveLockedBucketNames(context, currentNames)
        }

        val resolvedPath = folderPath ?: getLockedFolderPaths(context).find { it.endsWith(bucketName ?: "", ignoreCase = true) }
        if (!resolvedPath.isNullOrBlank()) {
            val currentPaths = getLockedFolderPaths(context).toMutableSet().apply { remove(resolvedPath) }
            saveLockedFolderPaths(context, currentPaths)

            // Remove .nomedia so system restores it
            try {
                val dir = java.io.File(resolvedPath)
                if (dir.exists() && dir.isDirectory) {
                    val noMedia = java.io.File(dir, ".nomedia")
                    if (noMedia.exists()) {
                        noMedia.delete()
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(noMedia.absolutePath, dir.absolutePath),
                            null,
                            null
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        sessionUnlockedBuckets.remove(bucketId)
        notifyLockStateChanged()
    }

    fun markAlbumUnlockedForSession(bucketId: Long) {
        sessionUnlockedBuckets.add(bucketId)
        notifyLockStateChanged()
    }

    fun getActiveLockedBucketIds(context: Context): Set<Long> {
        val locked = getLockedBucketIds(context)
        return locked.filter { it !in sessionUnlockedBuckets }.toSet()
    }

    fun getLockedBucketIds(context: Context): Set<Long> {
        val rawSet = getPrefs(context).getStringSet(KEY_LOCKED_BUCKETS, emptySet()) ?: emptySet()
        return rawSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun getLockedBucketNames(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_LOCKED_NAMES, emptySet()) ?: emptySet()
    }

    fun getLockedFolderPaths(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_LOCKED_PATHS, emptySet()) ?: emptySet()
    }

    private fun saveLockedBucketIds(context: Context, ids: Set<Long>) {
        getPrefs(context).edit()
            .putStringSet(KEY_LOCKED_BUCKETS, ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun saveLockedBucketNames(context: Context, names: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_LOCKED_NAMES, names)
            .apply()
    }

    private fun saveLockedFolderPaths(context: Context, paths: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_LOCKED_PATHS, paths)
            .apply()
    }
}

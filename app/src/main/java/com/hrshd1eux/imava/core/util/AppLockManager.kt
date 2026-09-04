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

    fun isAlbumLocked(context: Context, bucketId: Long): Boolean {
        val lockedIds = getLockedBucketIds(context)
        return bucketId in lockedIds && bucketId !in sessionUnlockedBuckets
    }

    fun isAlbumConfiguredLocked(context: Context, bucketId: Long): Boolean {
        return bucketId in getLockedBucketIds(context)
    }

    fun lockAlbum(context: Context, bucketId: Long) {
        val current = getLockedBucketIds(context).toMutableSet()
        current.add(bucketId)
        sessionUnlockedBuckets.remove(bucketId)
        saveLockedBucketIds(context, current)
        notifyLockStateChanged()
    }

    fun unlockAlbum(context: Context, bucketId: Long) {
        val current = getLockedBucketIds(context).toMutableSet()
        current.remove(bucketId)
        sessionUnlockedBuckets.remove(bucketId)
        saveLockedBucketIds(context, current)
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

    private fun saveLockedBucketIds(context: Context, ids: Set<Long>) {
        getPrefs(context).edit()
            .putStringSet(KEY_LOCKED_BUCKETS, ids.map { it.toString() }.toSet())
            .apply()
    }
}

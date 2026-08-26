package com.hrshd1eux.imava.core.util

import android.content.Context
import com.hrshd1eux.imava.data.database.MediaMetadataEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object VaultCacheManager {

    private fun getVaultCacheDir(context: Context): File {
        return File(context.cacheDir, "vault_cache").apply { mkdirs() }
    }

    fun getDecryptedFile(context: Context, entity: MediaMetadataEntity): File? {
        if (entity.vaultPath.isBlank()) return null
        val vaultFile = File(entity.vaultPath)
        if (!vaultFile.exists() || vaultFile.length() <= 0) return null

        val cacheDir = getVaultCacheDir(context)
        val ext = when {
            entity.mimeType.contains("png", ignoreCase = true) -> "png"
            entity.mimeType.contains("gif", ignoreCase = true) -> "gif"
            entity.mimeType.contains("webp", ignoreCase = true) -> "webp"
            entity.mimeType.contains("video", ignoreCase = true) || entity.mimeType.contains("mp4", ignoreCase = true) -> "mp4"
            entity.mimeType.contains("mkv", ignoreCase = true) -> "mkv"
            else -> "jpg"
        }
        val cachedFile = File(cacheDir, "decrypted_${entity.mediaId}.$ext")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }

        val tempFile = File(cacheDir, "decrypted_${entity.mediaId}.tmp")
        return try {
            FileInputStream(vaultFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    VaultCrypto.decrypt(input, output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (cachedFile.exists()) cachedFile.delete()
                tempFile.renameTo(cachedFile)
                cachedFile
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            tempFile.delete()
            e.printStackTrace()
            null
        }
    }

    fun removeCachedFile(context: Context, mediaId: Long) {
        try {
            val cacheDir = getVaultCacheDir(context)
            cacheDir.listFiles { _, name -> name.startsWith("decrypted_${mediaId}") }?.forEach {
                it.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "vault_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

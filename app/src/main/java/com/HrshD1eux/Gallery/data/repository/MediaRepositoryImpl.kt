package com.HrshD1eux.Gallery.data.repository

import com.HrshD1eux.Gallery.data.database.MediaMetadataEntity
import com.HrshD1eux.Gallery.data.database.MetadataDao
import com.HrshD1eux.Gallery.data.media.BucketInfo
import com.HrshD1eux.Gallery.data.media.MediaStoreDataSource
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import android.content.Context
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val metadataDao: MetadataDao
) : MediaRepository {

    override fun getMediaFlow(bucketId: Long?, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder): Flow<List<MediaItem>> {
        return mediaStoreDataSource.observeMediaStore()
            .flatMapLatest {
                flow {
                    val media = loadMediaPaged(limit = 200, offset = 0, bucketId = bucketId, sortOrder = sortOrder)
                    emit(media)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun loadMediaPaged(limit: Int, offset: Int, bucketId: Long?, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder): List<MediaItem> = withContext(Dispatchers.IO) {
        var currentOffset = offset
        val resultList = mutableListOf<MediaItem>()
        var reachedEnd = false
        val isAscending = (sortOrder == com.HrshD1eux.Gallery.ui.SortOrder.OLDEST_FIRST)
        
        while (resultList.size < limit && !reachedEnd) {
            val remaining = limit - resultList.size
            val rawMedia = mediaStoreDataSource.fetchMedia(limit = remaining, offset = currentOffset, bucketId = bucketId, isAscending = isAscending)
            if (rawMedia.isEmpty()) {
                break
            }
            val ids = rawMedia.map { it.id }
            val metadataList = metadataDao.getMetadataForMediaIds(ids)
            val metadataMap = metadataList.associateBy { it.mediaId }

            val filtered = rawMedia.map { item ->
                val meta = metadataMap[item.id]
                applyMetadata(item, meta)
            }.filter { !it.isHidden && !it.isTrashed }
            
            resultList.addAll(filtered)
            currentOffset += rawMedia.size
            if (rawMedia.size < remaining) {
                break
            }
        }
        if (isAscending) {
            resultList.sortedBy { it.dateTaken }
        } else {
            resultList.sortedByDescending { it.dateTaken }
        }
    }

    override fun observeMediaChanges(): Flow<Unit> {
        return mediaStoreDataSource.observeMediaStore()
    }

    override suspend fun getBuckets(): List<BucketInfo> = withContext(Dispatchers.IO) {
        val rawBuckets = mediaStoreDataSource.fetchBuckets()
        val metadataList = metadataDao.getHiddenOrTrashedMetadata()
        
        val hiddenOrTrashedCounts = metadataList
            .filter { it.isHidden || it.isTrashed }
            .groupBy { it.bucketId }
            .mapValues { it.value.size }
            
        rawBuckets.map { bucket ->
            val subtractCount = hiddenOrTrashedCounts[bucket.id] ?: 0
            val newCount = (bucket.count - subtractCount).coerceAtLeast(0)
            BucketInfo(bucket.id, bucket.name, newCount)
        }.filter { it.count > 0 }
         .sortedByDescending { it.count }
    }

    override fun getBucketsFlow(): Flow<List<BucketInfo>> {
        return mediaStoreDataSource.observeMediaStore()
            .flatMapLatest {
                flow {
                    emit(getBuckets())
                }
            }
            .combine(metadataDao.getAllMetadataFlow()) { _, _ ->
                getBuckets()
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun toggleFavorite(mediaItem: MediaItem) {
        val currentMeta = metadataDao.getMetadataForMedia(mediaItem.id)
            ?: MediaMetadataEntity(mediaId = mediaItem.id)
        val updated = currentMeta.copy(isFavorite = !mediaItem.isFavorite)
        metadataDao.insertOrUpdate(updated)
    }

    override suspend fun toggleHidden(context: Context, mediaItem: MediaItem) {
        val currentMeta = metadataDao.getMetadataForMedia(mediaItem.id)
        if (mediaItem.isHidden) {
            // Restore from Vault back to public storage
            if (currentMeta != null && currentMeta.vaultPath.isNotEmpty()) {
                val vaultFile = java.io.File(currentMeta.vaultPath)
                if (vaultFile.exists()) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, java.io.File(currentMeta.originalPath).name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, currentMeta.mimeType)
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Restored")
                    }
                    val collectionUri = if (currentMeta.mimeType.contains("video", ignoreCase = true)) {
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val targetUri = resolver.insert(collectionUri, contentValues)
                    if (targetUri != null) {
                        resolver.openOutputStream(targetUri)?.use { output ->
                            vaultFile.inputStream().use { input ->
                                com.HrshD1eux.Gallery.core.util.VaultCrypto.decrypt(input, output)
                            }
                        }
                        vaultFile.delete()
                        val metaFile = java.io.File(currentMeta.vaultPath + ".meta")
                        if (metaFile.exists()) metaFile.delete()
                    }
                }
                metadataDao.delete(currentMeta)
            }
        } else {
            // Hide and encrypt into secure vault
            val vaultDir = java.io.File(context.filesDir, "vault").apply { mkdirs() }
            val vaultFile = java.io.File(vaultDir, "vault_${mediaItem.id}")
            val resolver = context.contentResolver
            resolver.openInputStream(mediaItem.uri)?.use { input ->
                vaultFile.outputStream().use { output ->
                    com.HrshD1eux.Gallery.core.util.VaultCrypto.encrypt(input, output)
                }
            }
            
            val duration = if (mediaItem is MediaItem.Video) mediaItem.durationMs else 0L
            val entity = MediaMetadataEntity(
                mediaId = mediaItem.id,
                isFavorite = mediaItem.isFavorite,
                isHidden = true,
                isTrashed = false,
                originalPath = mediaItem.path,
                vaultPath = vaultFile.absolutePath,
                mimeType = mediaItem.mimeType,
                dateTaken = mediaItem.dateTaken,
                size = mediaItem.size,
                width = mediaItem.width,
                height = mediaItem.height,
                bucketId = mediaItem.bucketId,
                bucketName = mediaItem.bucketName,
                durationMs = duration
            )
            metadataDao.insertOrUpdate(entity)

            // Write encrypted meta file
            val metaFile = java.io.File(vaultFile.absolutePath + ".meta")
            try {
                val props = java.util.Properties().apply {
                    setProperty("mediaId", entity.mediaId.toString())
                    setProperty("isFavorite", entity.isFavorite.toString())
                    setProperty("isHidden", entity.isHidden.toString())
                    setProperty("isTrashed", entity.isTrashed.toString())
                    setProperty("originalPath", entity.originalPath)
                    setProperty("vaultPath", entity.vaultPath)
                    setProperty("mimeType", entity.mimeType)
                    setProperty("dateTaken", entity.dateTaken.toString())
                    setProperty("size", entity.size.toString())
                    setProperty("width", entity.width.toString())
                    setProperty("height", entity.height.toString())
                    setProperty("bucketId", entity.bucketId.toString())
                    setProperty("bucketName", entity.bucketName)
                    setProperty("durationMs", entity.durationMs.toString())
                }
                val byteArrayOutput = java.io.ByteArrayOutputStream()
                props.store(byteArrayOutput, "Vault Metadata Sidecar")
                metaFile.outputStream().use { out ->
                    com.HrshD1eux.Gallery.core.util.VaultCrypto.encrypt(
                        java.io.ByteArrayInputStream(byteArrayOutput.toByteArray()),
                        out
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Delete original file from public MediaStore
            try {
                resolver.delete(mediaItem.uri, null, null)
            } catch (e: SecurityException) {
                throw e
            }
        }
    }

    override suspend fun toggleTrashed(mediaItem: MediaItem) {
        val currentMeta = metadataDao.getMetadataForMedia(mediaItem.id)
            ?: MediaMetadataEntity(mediaId = mediaItem.id)
        val updated = currentMeta.copy(
            isTrashed = !mediaItem.isTrashed,
            trashTime = if (!mediaItem.isTrashed) System.currentTimeMillis() else 0L
        )
        metadataDao.insertOrUpdate(updated)
    }

    override fun getFavoriteMediaFlow(): Flow<List<MediaItem>> {
        return metadataDao.getFavoriteIdsFlow()
            .combine(mediaStoreDataSource.observeMediaStore()) { favoriteIds, _ -> favoriteIds }
            .map { favoriteIds ->
                if (favoriteIds.isEmpty()) return@map emptyList<MediaItem>()
                val items = mediaStoreDataSource.fetchMediaByIds(favoriteIds.toSet())
                val metadataList = metadataDao.getMetadataForMediaIds(favoriteIds)
                val metadataMap = metadataList.associateBy { it.mediaId }
                items.map { item ->
                    val meta = metadataMap[item.id]
                    applyMetadata(item, meta)
                }.filter { it.isFavorite && !it.isHidden && !it.isTrashed }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getTrashedMediaFlow(): Flow<List<MediaItem>> {
        return metadataDao.getTrashedIdsFlow()
            .combine(mediaStoreDataSource.observeMediaStore()) { trashedIds, _ -> trashedIds }
            .map { trashedIds ->
                val storeTrashed = mediaStoreDataSource.fetchMedia(limit = 1000, offset = 0, includeTrashed = true).filter { it.isTrashed }
                val dbItems = if (trashedIds.isNotEmpty()) {
                    mediaStoreDataSource.fetchMediaByIds(trashedIds.toSet())
                } else {
                    emptyList()
                }
                val metadataList = metadataDao.getMetadataForMediaIds(trashedIds)
                val metadataMap = metadataList.associateBy { it.mediaId }
                
                val combined = (storeTrashed + dbItems).map { item ->
                    applyMetadata(item, metadataMap[item.id])
                }.filter { it.isTrashed }
                combined.distinctBy { it.id }
            }
            .flowOn(Dispatchers.IO)
    }

    private suspend fun syncVaultMetadata() = withContext(Dispatchers.IO) {
        val vaultDir = java.io.File(context.filesDir, "vault")
        if (!vaultDir.exists()) return@withContext
        val metaFiles = vaultDir.listFiles { _, name -> name.endsWith(".meta") } ?: return@withContext
        
        if (metaFiles.isNotEmpty()) {
            val dbIds = metadataDao.getAllMetadata().map { it.mediaId }.toSet()
            metaFiles.forEach { metaFile ->
                try {
                    val props = java.util.Properties()
                    try {
                        val byteArrayOutput = java.io.ByteArrayOutputStream()
                        java.io.FileInputStream(metaFile).use { input ->
                            com.HrshD1eux.Gallery.core.util.VaultCrypto.decrypt(input, byteArrayOutput)
                        }
                        props.load(java.io.ByteArrayInputStream(byteArrayOutput.toByteArray()))
                    } catch (e: Exception) {
                        // Fallback for legacy unencrypted meta files
                        java.io.FileInputStream(metaFile).use { input ->
                            props.load(input)
                        }
                    }
                    val mediaId = props.getProperty("mediaId").toLong()
                    if (mediaId !in dbIds) {
                        val entity = MediaMetadataEntity(
                            mediaId = mediaId,
                            isFavorite = props.getProperty("isFavorite").toBoolean(),
                            isHidden = props.getProperty("isHidden").toBoolean(),
                            isTrashed = props.getProperty("isTrashed").toBoolean(),
                            originalPath = props.getProperty("originalPath") ?: "",
                            vaultPath = props.getProperty("vaultPath") ?: "",
                            mimeType = props.getProperty("mimeType") ?: "",
                            dateTaken = props.getProperty("dateTaken").toLong(),
                            size = props.getProperty("size").toLong(),
                            width = props.getProperty("width").toInt(),
                            height = props.getProperty("height").toInt(),
                            bucketId = props.getProperty("bucketId").toLong(),
                            bucketName = props.getProperty("bucketName") ?: "",
                            durationMs = props.getProperty("durationMs").toLong()
                        )
                        metadataDao.insertOrUpdate(entity)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getDecryptedCacheFile(context: Context, entity: MediaMetadataEntity): java.io.File? {
        return try {
            val vaultFile = java.io.File(entity.vaultPath)
            if (!vaultFile.exists()) return null
            val cacheDir = java.io.File(context.cacheDir, "vault_cache").apply { mkdirs() }
            val ext = entity.mimeType.substringAfter("/").ifEmpty { "jpg" }
            val cacheFile = java.io.File(cacheDir, "decrypted_${entity.mediaId}.$ext")
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                java.io.FileOutputStream(cacheFile).use { out ->
                    vaultFile.inputStream().use { input ->
                        com.HrshD1eux.Gallery.core.util.VaultCrypto.decrypt(input, out)
                    }
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getHiddenMediaFlow(isVaultUnlocked: Boolean): Flow<List<MediaItem>> {
        return metadataDao.getAllMetadataFlow()
            .onStart { if (isVaultUnlocked) syncVaultMetadata() }
            .map { metadataList ->
                if (!isVaultUnlocked) return@map emptyList<MediaItem>()
                metadataList.filter { it.isHidden && !it.isTrashed }.mapNotNull { entity ->
                    val cacheFile = getDecryptedCacheFile(context, entity) ?: return@mapNotNull null
                    val uri = android.net.Uri.fromFile(cacheFile)

                    if (entity.mimeType.contains("video", ignoreCase = true)) {
                        MediaItem.Video(
                            id = entity.mediaId,
                            uri = uri,
                            path = cacheFile.absolutePath,
                            mimeType = entity.mimeType,
                            dateTaken = entity.dateTaken,
                            size = entity.size,
                            width = entity.width,
                            height = entity.height,
                            durationMs = entity.durationMs,
                            isFavorite = entity.isFavorite,
                            isHidden = entity.isHidden,
                            isTrashed = entity.isTrashed,
                            bucketId = entity.bucketId,
                            bucketName = entity.bucketName
                        )
                    } else {
                        MediaItem.Photo(
                            id = entity.mediaId,
                            uri = uri,
                            path = cacheFile.absolutePath,
                            mimeType = entity.mimeType,
                            dateTaken = entity.dateTaken,
                            size = entity.size,
                            width = entity.width,
                            height = entity.height,
                            isFavorite = entity.isFavorite,
                            isHidden = entity.isHidden,
                            isTrashed = entity.isTrashed,
                            bucketId = entity.bucketId,
                            bucketName = entity.bucketName
                        )
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    override suspend fun clearVaultCache(context: Context): Unit = withContext(Dispatchers.IO) {
        try {
            val cacheDir = java.io.File(context.cacheDir, "vault_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        withContext(Dispatchers.Main) {
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Unit
    }

    private fun applyMetadata(item: MediaItem, meta: MediaMetadataEntity?): MediaItem {
        if (meta == null) return item
        return when (item) {
            is MediaItem.Photo -> item.copy(
                isFavorite = meta.isFavorite,
                isHidden = meta.isHidden,
                isTrashed = meta.isTrashed || item.isTrashed
            )
            is MediaItem.Video -> item.copy(
                isFavorite = meta.isFavorite,
                isHidden = meta.isHidden,
                isTrashed = meta.isTrashed || item.isTrashed
            )
        }
    }

    override suspend fun deleteMetadataPermanently(mediaId: Long) {
        val meta = metadataDao.getMetadataForMedia(mediaId)
        if (meta != null) {
            if (meta.vaultPath.isNotEmpty()) {
                val vaultFile = java.io.File(meta.vaultPath)
                if (vaultFile.exists()) vaultFile.delete()
                val metaFile = java.io.File(meta.vaultPath + ".meta")
                if (metaFile.exists()) metaFile.delete()
            }
            metadataDao.delete(meta)
        }
    }

    override suspend fun deleteOrphanedMetadata(activeIds: List<Long>) = withContext(Dispatchers.IO) {
        val activeIdSet = activeIds.toSet()
        val trackedIds = metadataDao.getTrackedNonHiddenIds()
        val orphanedIds = trackedIds.filter { it !in activeIdSet }
        
        if (orphanedIds.isNotEmpty()) {
            orphanedIds.chunked(500).forEach { chunk ->
                metadataDao.deleteMetadataByIds(chunk)
            }
        }
    }

    override suspend fun getActiveMediaIds(): List<Long> = mediaStoreDataSource.fetchMediaIds()

    override suspend fun getMediaByIds(ids: Set<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val items = mediaStoreDataSource.fetchMediaByIds(ids)
        val metadataList = metadataDao.getMetadataForMediaIds(ids.toList())
        val metadataMap = metadataList.associateBy { it.mediaId }
        items.map { item ->
            val meta = metadataMap[item.id]
            applyMetadata(item, meta)
        }
    }

    override suspend fun searchMedia(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val rawResults = mediaStoreDataSource.searchMedia(query)
        if (rawResults.isEmpty()) return@withContext emptyList()

        val ids = rawResults.map { it.id }
        val metadataList = metadataDao.getMetadataForMediaIds(ids)
        val metadataMap = metadataList.associateBy { it.mediaId }

        rawResults.map { item ->
            val meta = metadataMap[item.id]
            applyMetadata(item, meta)
        }.filter { !it.isHidden && !it.isTrashed }
    }

    override suspend fun scanSecondaryMediaDirectories(): Int = mediaStoreDataSource.scanSecondaryMediaDirectories()

    override suspend fun getDatePositionIndex(bucketId: Long?, sortOrder: com.HrshD1eux.Gallery.ui.SortOrder): List<DatePositionHeader> = mediaStoreDataSource.getDatePositionIndex(bucketId, isAscending = (sortOrder == com.HrshD1eux.Gallery.ui.SortOrder.OLDEST_FIRST))

    override suspend fun renameMedia(
        context: Context,
        mediaItem: MediaItem,
        newDisplayName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(mediaItem.path)
            val ext = file.extension.ifEmpty {
                when (mediaItem.mimeType) {
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    "image/webp" -> "webp"
                    "video/mp4" -> "mp4"
                    "video/x-matroska" -> "mkv"
                    else -> "jpg"
                }
            }
            val finalName = if (newDisplayName.contains(".")) newDisplayName else "$newDisplayName.$ext"

            if (mediaItem.isHidden) {
                val entity = metadataDao.getMetadataForMedia(mediaItem.id)
                if (entity != null) {
                    val newOriginalPath = java.io.File(java.io.File(entity.originalPath).parentFile, finalName).absolutePath
                    metadataDao.insertOrUpdate(entity.copy(originalPath = newOriginalPath))
                }
                return@withContext true
            }

            var updated = false
            val resolver = context.contentResolver
            try {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(android.provider.MediaStore.MediaColumns.TITLE, finalName.substringBeforeLast("."))
                }
                val rows = resolver.update(mediaItem.uri, contentValues, null, null)
                if (rows > 0) updated = true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (file.exists() && file.parentFile != null) {
                val targetFile = java.io.File(file.parentFile, finalName)
                if (file.renameTo(targetFile)) {
                    updated = true
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath, targetFile.absolutePath),
                        null,
                        null
                    )
                } else {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null,
                        null
                    )
                }
            }

            val entity = metadataDao.getMetadataForMedia(mediaItem.id)
            if (entity != null && file.parentFile != null) {
                val newPath = java.io.File(file.parentFile, finalName).absolutePath
                metadataDao.insertOrUpdate(entity.copy(originalPath = newPath))
            }

            updated || !file.exists()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun purgeExpiredTrashMedia(): Int = withContext(Dispatchers.IO) {
        var purgedCount = 0
        try {
            val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
            val cutoffTime = System.currentTimeMillis() - thirtyDaysMs
            val allMetadata = metadataDao.getAllMetadata()
            val expiredEntities = allMetadata.filter { it.isTrashed && it.trashTime > 0 && it.trashTime < cutoffTime }

            for (entity in expiredEntities) {
                deleteMetadataPermanently(entity.mediaId)
                purgedCount++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        purgedCount
    }
}

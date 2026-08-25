package com.hrshd1eux.imava.data.repository

import com.hrshd1eux.imava.data.database.MediaMetadataEntity
import com.hrshd1eux.imava.data.database.MetadataDao
import com.hrshd1eux.imava.data.media.BucketInfo
import com.hrshd1eux.imava.data.media.MediaStoreDataSource
import com.hrshd1eux.imava.data.model.MediaItem
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
import com.hrshd1eux.imava.data.media.MediaTypeFilter
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val metadataDao: MetadataDao
) : MediaRepository {

    override fun getMediaFlow(bucketId: Long?, sortOrder: com.hrshd1eux.imava.ui.SortOrder, mediaType: MediaTypeFilter): Flow<List<MediaItem>> {
        return combine(
            mediaStoreDataSource.observeMediaStore().onStart { emit(Unit) },
            metadataDao.getAllMetadataFlow().onStart { emit(emptyList()) }
        ) { _, _ ->
            loadMediaPaged(limit = Int.MAX_VALUE, offset = 0, bucketId = bucketId, sortOrder = sortOrder, mediaType = mediaType)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun loadMediaPaged(limit: Int, offset: Int, bucketId: Long?, sortOrder: com.hrshd1eux.imava.ui.SortOrder, mediaType: MediaTypeFilter): List<MediaItem> = withContext(Dispatchers.IO) {
        var currentOffset = offset
        val resultList = mutableListOf<MediaItem>()
        var reachedEnd = false
        val isAscending = (sortOrder == com.hrshd1eux.imava.ui.SortOrder.OLDEST_FIRST)
        
        while (resultList.size < limit && !reachedEnd) {
            val remaining = limit - resultList.size
            val fetchBatch = if (remaining == Int.MAX_VALUE) 1000 else minOf(remaining, 1000)
            val rawMedia = mediaStoreDataSource.fetchMedia(limit = fetchBatch, offset = currentOffset, bucketId = bucketId, isAscending = isAscending, mediaType = mediaType)
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
            if (rawMedia.size < fetchBatch) {
                break
            }
        }
        if (isAscending) {
            resultList.sortedBy { it.dateTaken }
        } else {
            resultList.sortedByDescending { it.dateTaken }
        }
    }

    override suspend fun getTotalMediaCount(bucketId: Long?, mediaType: MediaTypeFilter): Int = withContext(Dispatchers.IO) {
        mediaStoreDataSource.getTotalMediaCount(bucketId, mediaType)
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
            
        val userPrefs = context.getSharedPreferences("user_albums", Context.MODE_PRIVATE)
        val createdAlbums = userPrefs.getStringSet("created_albums", emptySet()) ?: emptySet()

        val processedBuckets = rawBuckets.map { bucket ->
            val subtractCount = hiddenOrTrashedCounts[bucket.id] ?: 0
            val newCount = (bucket.count - subtractCount).coerceAtLeast(0)
            BucketInfo(bucket.id, bucket.name, newCount)
        }.toMutableList()

        // Include user-created albums that may currently be empty in MediaStore
        val existingNames = processedBuckets.map { it.name }.toSet()
        for (albumName in createdAlbums) {
            if (!existingNames.contains(albumName)) {
                val bucketId = albumName.hashCode().toLong()
                processedBuckets.add(BucketInfo(bucketId, albumName, 0))
            }
        }

        processedBuckets.filter { it.count > 0 || createdAlbums.contains(it.name) }
            .sortedWith(compareByDescending<BucketInfo> { it.count }.thenBy { it.name })
    }

    override fun getBucketsFlow(): Flow<List<BucketInfo>> {
        return combine(
            mediaStoreDataSource.observeMediaStore().onStart { emit(Unit) },
            metadataDao.getAllMetadataFlow().onStart { emit(emptyList()) }
        ) { _, _ ->
            getBuckets()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun toggleFavorite(mediaItem: MediaItem) {
        val currentMeta = metadataDao.getMetadataForMedia(mediaItem.id)
            ?: MediaMetadataEntity(mediaId = mediaItem.id)
        val updated = currentMeta.copy(isFavorite = !mediaItem.isFavorite)
        metadataDao.insertOrUpdate(updated)
    }

    override suspend fun toggleFavoriteBatch(mediaIds: Set<Long>) {
        if (mediaIds.isEmpty()) return
        val idList = mediaIds.toList()
        val metas = metadataDao.getMetadataForMediaIds(idList).associateBy { it.mediaId }
        val allFavorited = idList.all { metas[it]?.isFavorite == true }
        val newFavoriteState = !allFavorited

        idList.chunked(500).forEach { chunk ->
            chunk.forEach { id ->
                val meta = metas[id] ?: MediaMetadataEntity(mediaId = id)
                metadataDao.insertOrUpdate(meta.copy(isFavorite = newFavoriteState))
            }
        }
    }

    override suspend fun toggleHidden(context: Context, mediaItem: MediaItem) {
        val currentMeta = metadataDao.getMetadataForMedia(mediaItem.id)
        if (mediaItem.isHidden) {
            // Restore from Vault back to public storage
            if (currentMeta != null && currentMeta.vaultPath.isNotEmpty()) {
                val vaultFile = java.io.File(currentMeta.vaultPath)
                if (vaultFile.exists()) {
                    val resolver = context.contentResolver
                    val originalName = if (currentMeta.originalPath.isNotBlank() && java.io.File(currentMeta.originalPath).name.isNotBlank()) {
                        java.io.File(currentMeta.originalPath).name
                    } else {
                        "restored_${currentMeta.mediaId}.${if (currentMeta.mimeType.contains("png")) "png" else "jpg"}"
                    }
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, originalName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (currentMeta.mimeType.isNotBlank()) currentMeta.mimeType else "image/jpeg")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Restored")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                        }
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
                                com.hrshd1eux.imava.core.util.VaultCrypto.decrypt(input, output)
                            }
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(targetUri, contentValues, null, null)
                        }
                        vaultFile.delete()
                        val metaFile = java.io.File(currentMeta.vaultPath + ".meta")
                        if (metaFile.exists()) metaFile.delete()
                        metadataDao.delete(currentMeta)
                    }
                }
            }
        } else {
            // Hide and encrypt into secure vault
            val vaultDir = java.io.File(context.filesDir, "vault").apply { mkdirs() }
            val vaultFile = java.io.File(vaultDir, "vault_${mediaItem.id}")
            val resolver = context.contentResolver
            resolver.openInputStream(mediaItem.uri)?.use { input ->
                vaultFile.outputStream().use { output ->
                    com.hrshd1eux.imava.core.util.VaultCrypto.encrypt(input, output)
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
                    com.hrshd1eux.imava.core.util.VaultCrypto.encrypt(
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
                val storeTrashed = mediaStoreDataSource.fetchTrashedMedia()
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
                            com.hrshd1eux.imava.core.util.VaultCrypto.decrypt(input, byteArrayOutput)
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

    override fun getHiddenMediaFlow(isVaultUnlocked: Boolean): Flow<List<MediaItem>> {
        return metadataDao.getAllMetadataFlow()
            .onStart { if (isVaultUnlocked) syncVaultMetadata() }
            .map { metadataList ->
                if (!isVaultUnlocked) return@map emptyList<MediaItem>()
                metadataList.filter { it.isHidden && !it.isTrashed }.mapNotNull { entity ->
                    val vaultFile = java.io.File(entity.vaultPath)
                    if (!vaultFile.exists()) return@mapNotNull null
                    val uri = android.net.Uri.fromFile(vaultFile)

                    if (entity.mimeType.contains("video", ignoreCase = true)) {
                        MediaItem.Video(
                            id = entity.mediaId,
                            uri = uri,
                            path = entity.originalPath.ifEmpty { entity.vaultPath },
                            mimeType = entity.mimeType,
                            dateTaken = entity.dateTaken,
                            size = entity.size,
                            width = entity.width,
                            height = entity.height,
                            durationMs = entity.durationMs,
                            isFavorite = entity.isFavorite,
                            isHidden = entity.isHidden,
                            isTrashed = entity.isTrashed,
                            trashTime = entity.trashTime,
                            bucketId = entity.bucketId,
                            bucketName = entity.bucketName
                        )
                    } else {
                        MediaItem.Photo(
                            id = entity.mediaId,
                            uri = uri,
                            path = entity.originalPath.ifEmpty { entity.vaultPath },
                            mimeType = entity.mimeType,
                            dateTaken = entity.dateTaken,
                            size = entity.size,
                            width = entity.width,
                            height = entity.height,
                            isFavorite = entity.isFavorite,
                            isHidden = entity.isHidden,
                            isTrashed = entity.isTrashed,
                            trashTime = entity.trashTime,
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
        try {
            val imageLoader = coil.Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyMetadata(item: MediaItem, meta: MediaMetadataEntity?): MediaItem {
        if (meta == null) return item
        val tagList = if (meta.tags.isNotBlank()) meta.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
        return when (item) {
            is MediaItem.Photo -> item.copy(
                isFavorite = meta.isFavorite,
                isHidden = meta.isHidden,
                isTrashed = meta.isTrashed || item.isTrashed,
                trashTime = meta.trashTime,
                tags = tagList
            )
            is MediaItem.Video -> item.copy(
                isFavorite = meta.isFavorite,
                isHidden = meta.isHidden,
                isTrashed = meta.isTrashed || item.isTrashed,
                trashTime = meta.trashTime,
                tags = tagList
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
        val mappedStoreItems = items.map { item ->
            val meta = metadataMap[item.id]
            applyMetadata(item, meta)
        }

        val foundIds = mappedStoreItems.map { it.id }.toSet()
        val missingIds = ids - foundIds
        if (missingIds.isNotEmpty()) {
            val vaultEntities = metadataList.filter { it.mediaId in missingIds && it.isHidden }
            val vaultItems = vaultEntities.mapNotNull { entity ->
                val vaultFile = java.io.File(entity.vaultPath)
                if (!vaultFile.exists()) return@mapNotNull null
                val uri = android.net.Uri.fromFile(vaultFile)
                if (entity.mimeType.contains("video", ignoreCase = true)) {
                    MediaItem.Video(
                        id = entity.mediaId,
                        uri = uri,
                        path = entity.originalPath.ifEmpty { entity.vaultPath },
                        mimeType = entity.mimeType,
                        dateTaken = entity.dateTaken,
                        size = entity.size,
                        width = entity.width,
                        height = entity.height,
                        durationMs = entity.durationMs,
                        isFavorite = entity.isFavorite,
                        isHidden = entity.isHidden,
                        isTrashed = entity.isTrashed,
                        trashTime = entity.trashTime,
                        bucketId = entity.bucketId,
                        bucketName = entity.bucketName
                    )
                } else {
                    MediaItem.Photo(
                        id = entity.mediaId,
                        uri = uri,
                        path = entity.originalPath.ifEmpty { entity.vaultPath },
                        mimeType = entity.mimeType,
                        dateTaken = entity.dateTaken,
                        size = entity.size,
                        width = entity.width,
                        height = entity.height,
                        isFavorite = entity.isFavorite,
                        isHidden = entity.isHidden,
                        isTrashed = entity.isTrashed,
                        trashTime = entity.trashTime,
                        bucketId = entity.bucketId,
                        bucketName = entity.bucketName
                    )
                }
            }
            mappedStoreItems + vaultItems
        } else {
            mappedStoreItems
        }
    }

    override suspend fun searchMedia(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val cleanQuery = query.trim().removePrefix("#")
        val rawResults = mediaStoreDataSource.searchMedia(cleanQuery)
        val tagMatchedEntities = metadataDao.getMetadataByTag(cleanQuery)
        val tagMatchedIds = tagMatchedEntities.map { it.mediaId }.toSet()

        val allIds = (rawResults.map { it.id } + tagMatchedIds).toList()
        if (allIds.isEmpty()) return@withContext emptyList()

        val metadataList = metadataDao.getMetadataForMediaIds(allIds)
        val metadataMap = metadataList.associateBy { it.mediaId }

        val extraItems = if (tagMatchedIds.isNotEmpty()) {
            val existingIds = rawResults.map { it.id }.toSet()
            val missingIds = tagMatchedIds - existingIds
            if (missingIds.isNotEmpty()) {
                mediaStoreDataSource.fetchMediaByIds(missingIds)
            } else emptyList<MediaItem>()
        } else emptyList<MediaItem>()

        (rawResults + extraItems).distinctBy { it.id }.map { item ->
            val meta = metadataMap[item.id]
            applyMetadata(item, meta)
        }.filter { !it.isHidden && !it.isTrashed }
    }

    override suspend fun updateMediaTags(mediaId: Long, tags: List<String>) = withContext(Dispatchers.IO) {
        val currentMeta = metadataDao.getMetadataForMedia(mediaId) ?: MediaMetadataEntity(mediaId = mediaId)
        val tagStr = tags.joinToString(",")
        metadataDao.insertOrUpdate(currentMeta.copy(tags = tagStr))
    }

    override suspend fun scanSecondaryMediaDirectories(): Int = mediaStoreDataSource.scanSecondaryMediaDirectories()

    override suspend fun getDatePositionIndex(bucketId: Long?, sortOrder: com.hrshd1eux.imava.ui.SortOrder, mediaType: MediaTypeFilter): List<DatePositionHeader> = mediaStoreDataSource.getDatePositionIndex(bucketId, isAscending = (sortOrder == com.hrshd1eux.imava.ui.SortOrder.OLDEST_FIRST))

    override suspend fun renameMedia(
        context: Context,
        mediaItem: MediaItem,
        newDisplayName: String
    ): Boolean = withContext(Dispatchers.IO) {
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

        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalName)
            put(android.provider.MediaStore.MediaColumns.TITLE, finalName.substringBeforeLast("."))
        }

        val rows = try {
            resolver.update(mediaItem.uri, contentValues, null, null)
        } catch (e: Exception) {
            val recoverable = e as? android.app.RecoverableSecurityException
                ?: e.cause as? android.app.RecoverableSecurityException
            val isSec = e is SecurityException || e.cause is SecurityException
            if (recoverable != null || isSec) {
                throw e
            }
            0
        }

        if (rows > 0) {
            if (file.exists() && file.parentFile != null) {
                val targetFile = java.io.File(file.parentFile, finalName)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath, targetFile.absolutePath),
                    null,
                    null
                )
            }
            val entity = metadataDao.getMetadataForMedia(mediaItem.id)
            if (entity != null && file.parentFile != null) {
                val newPath = java.io.File(file.parentFile, finalName).absolutePath
                metadataDao.insertOrUpdate(entity.copy(originalPath = newPath))
            }
            return@withContext true
        }

        if (file.exists() && file.parentFile != null) {
            val targetFile = java.io.File(file.parentFile, finalName)
            if (file.renameTo(targetFile)) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath, targetFile.absolutePath),
                    null,
                    null
                )
                val entity = metadataDao.getMetadataForMedia(mediaItem.id)
                if (entity != null) {
                    metadataDao.insertOrUpdate(entity.copy(originalPath = targetFile.absolutePath))
                }
                return@withContext true
            }
        }

        false
    }

    override suspend fun batchRenameMedia(
        context: Context,
        itemsWithNewNames: List<Pair<MediaItem, String>>
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        val resolver = context.contentResolver
        val pathsToScan = mutableListOf<String>()

        itemsWithNewNames.forEach { (item, newDisplayName) ->
            try {
                val file = java.io.File(item.path)
                val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
                val finalName = if (newDisplayName.endsWith(ext, ignoreCase = true)) newDisplayName else "$newDisplayName$ext"

                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(android.provider.MediaStore.MediaColumns.TITLE, finalName.substringBeforeLast("."))
                }

                val rows = try {
                    resolver.update(item.uri, contentValues, null, null)
                } catch (_: Exception) { 0 }

                if (rows > 0) {
                    successCount++
                    if (file.exists() && file.parentFile != null) {
                        val targetFile = java.io.File(file.parentFile, finalName)
                        pathsToScan.add(file.absolutePath)
                        pathsToScan.add(targetFile.absolutePath)
                        val entity = metadataDao.getMetadataForMedia(item.id)
                        if (entity != null) {
                            metadataDao.insertOrUpdate(entity.copy(originalPath = targetFile.absolutePath))
                        }
                    }
                } else if (file.exists() && file.parentFile != null) {
                    val targetFile = java.io.File(file.parentFile, finalName)
                    if (file.renameTo(targetFile)) {
                        successCount++
                        pathsToScan.add(file.absolutePath)
                        pathsToScan.add(targetFile.absolutePath)
                        val entity = metadataDao.getMetadataForMedia(item.id)
                        if (entity != null) {
                            metadataDao.insertOrUpdate(entity.copy(originalPath = targetFile.absolutePath))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (pathsToScan.isNotEmpty()) {
            android.media.MediaScannerConnection.scanFile(
                context,
                pathsToScan.toTypedArray(),
                null,
                null
            )
        }

        successCount
    }

    override suspend fun updateMediaDateTaken(context: Context, mediaItem: MediaItem, newDateMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // Update Room database metadata
            val existing = metadataDao.getMetadataForMedia(mediaItem.id)
            if (existing != null) {
                metadataDao.insertOrUpdate(existing.copy(dateTaken = newDateMs))
            } else {
                metadataDao.insertOrUpdate(
                    com.hrshd1eux.imava.data.database.MediaMetadataEntity(
                        mediaId = mediaItem.id,
                        isFavorite = mediaItem.isFavorite,
                        isTrashed = mediaItem.isTrashed,
                        isHidden = mediaItem.isHidden,
                        dateTaken = newDateMs,
                        originalPath = mediaItem.path,
                        mimeType = mediaItem.mimeType,
                        size = mediaItem.size,
                        width = mediaItem.width,
                        height = mediaItem.height,
                        durationMs = if (mediaItem is MediaItem.Video) mediaItem.durationMs else 0L,
                        bucketId = mediaItem.bucketId,
                        bucketName = mediaItem.bucketName
                    )
                )
            }

            // Update MediaStore if not in vault
            if (!mediaItem.isHidden) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, newDateMs)
                    put(android.provider.MediaStore.MediaColumns.DATE_ADDED, newDateMs / 1000L)
                }
                try {
                    resolver.update(mediaItem.uri, contentValues, null, null)
                } catch (_: Exception) {}

                // Update EXIF on disk if possible
                val file = java.io.File(mediaItem.path)
                if (file.exists() && file.canWrite()) {
                    try {
                        val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
                        val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.getDefault())
                        val formattedDate = sdf.format(java.util.Date(newDateMs))
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, formattedDate)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)
                        exif.saveAttributes()
                    } catch (_: Exception) {}
                }
            }
            true
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

    override suspend fun restoreAllVaultMedia(context: Context): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        try {
            val allMetadata = metadataDao.getAllMetadata()
            val hiddenEntities = allMetadata.filter { it.isHidden }
            for (entity in hiddenEntities) {
                val dummyItem = if (entity.mimeType.contains("video", ignoreCase = true)) {
                    MediaItem.Video(
                        id = entity.mediaId,
                        uri = android.net.Uri.fromFile(java.io.File(entity.vaultPath)),
                        path = entity.originalPath.ifEmpty { entity.vaultPath },
                        mimeType = if (entity.mimeType.isNotBlank()) entity.mimeType else "video/mp4",
                        dateTaken = entity.dateTaken,
                        size = entity.size,
                        width = entity.width,
                        height = entity.height,
                        durationMs = entity.durationMs,
                        bucketId = entity.bucketId,
                        bucketName = entity.bucketName,
                        isHidden = true
                    )
                } else {
                    MediaItem.Photo(
                        id = entity.mediaId,
                        uri = android.net.Uri.fromFile(java.io.File(entity.vaultPath)),
                        path = entity.originalPath.ifEmpty { entity.vaultPath },
                        mimeType = if (entity.mimeType.isNotBlank()) entity.mimeType else "image/jpeg",
                        dateTaken = entity.dateTaken,
                        size = entity.size,
                        width = entity.width,
                        height = entity.height,
                        bucketId = entity.bucketId,
                        bucketName = entity.bucketName,
                        isHidden = true
                    )
                }
                toggleHidden(context, dummyItem)
                restoredCount++
            }
            clearVaultCache(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        restoredCount
    }

    override suspend fun deleteAllVaultData(context: Context): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        try {
            val vaultDir = java.io.File(context.filesDir, "vault")
            if (vaultDir.exists()) {
                vaultDir.listFiles()?.forEach { file ->
                    file.delete()
                    deletedCount++
                }
            }
            val allMetadata = metadataDao.getAllMetadata()
            val hiddenEntities = allMetadata.filter { it.isHidden }
            for (entity in hiddenEntities) {
                metadataDao.delete(entity)
            }
            clearVaultCache(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        deletedCount
    }
}

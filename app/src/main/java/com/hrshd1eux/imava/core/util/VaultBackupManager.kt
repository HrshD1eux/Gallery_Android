package com.hrshd1eux.imava.core.util

import android.content.Context
import com.hrshd1eux.imava.data.database.GalleryDatabase
import com.hrshd1eux.imava.data.database.MediaMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultBackupManager {

    private val MAGIC_HEADER = "IMAVABAK".toByteArray(Charsets.UTF_8)
    private const val PBKDF2_ITERATIONS = 250_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    data class BackupResult(val success: Boolean, val itemsCount: Int, val message: String)

    suspend fun exportVaultBackup(
        context: Context,
        database: GalleryDatabase,
        targetOutputStream: OutputStream,
        passphrase: CharArray
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val vaultDir = File(context.filesDir, "vault")
            if (!vaultDir.exists()) {
                return@withContext BackupResult(false, 0, "No vault directory found on this device.")
            }

            val hiddenEntities = database.metadataDao().getHiddenMetadata()
            if (hiddenEntities.isEmpty()) {
                return@withContext BackupResult(false, 0, "Vault is empty. Nothing to export.")
            }

            // zip package
            val zipByteArrayOut = ByteArrayOutputStream()
            ZipOutputStream(zipByteArrayOut).use { zipOut ->
                // Write manifest.tsv
                val manifestBuilder = StringBuilder()
                hiddenEntities.forEach { entity ->
                    manifestBuilder.append(entity.mediaId).append("\t")
                        .append(entity.originalPath.replace("\t", " ")).append("\t")
                        .append(entity.vaultPath.replace("\t", " ")).append("\t")
                        .append(entity.mimeType.replace("\t", " ")).append("\t")
                        .append(entity.dateTaken).append("\t")
                        .append(entity.size).append("\t")
                        .append(entity.width).append("\t")
                        .append(entity.height).append("\t")
                        .append(entity.bucketId).append("\t")
                        .append(entity.bucketName.replace("\t", " ")).append("\t")
                        .append(entity.durationMs).append("\t")
                        .append(entity.isFavorite).append("\t")
                        .append(entity.tags.replace("\t", " ")).append("\n")
                }

                zipOut.putNextEntry(ZipEntry("manifest.tsv"))
                zipOut.write(manifestBuilder.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // Write vault ciphertext and meta files
                hiddenEntities.forEach { entity ->
                    val vaultFile = File(vaultDir, "vault_" + entity.mediaId)
                    if (vaultFile.exists()) {
                        zipOut.putNextEntry(ZipEntry("files/vault_" + entity.mediaId))
                        FileInputStream(vaultFile).use { fileIn ->
                            fileIn.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }

                    val metaFile = File(vaultDir, "vault_" + entity.mediaId + ".meta")
                    if (metaFile.exists()) {
                        zipOut.putNextEntry(ZipEntry("files/vault_" + entity.mediaId + ".meta"))
                        FileInputStream(metaFile).use { metaIn ->
                            metaIn.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            val rawZipBytes = zipByteArrayOut.toByteArray()

            // encrypt payload
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES)
            random.nextBytes(salt)

            val iv = ByteArray(IV_LENGTH_BYTES)
            random.nextBytes(iv)

            val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            val encryptedPayload = cipher.doFinal(rawZipBytes)

            // write container
            targetOutputStream.use { out ->
                out.write(MAGIC_HEADER)
                out.write(salt)
                out.write(iv)
                out.write(encryptedPayload)
                out.flush()
            }

            BackupResult(true, hiddenEntities.size, "Successfully exported " + hiddenEntities.size + " vault items.")
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult(false, 0, "Export failed: " + e.localizedMessage)
        }
    }

    suspend fun restoreVaultBackup(
        context: Context,
        database: GalleryDatabase,
        inputStream: InputStream,
        passphrase: CharArray
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val allBytes = inputStream.use { it.readBytes() }
            val headerLen = MAGIC_HEADER.size
            if (allBytes.size < headerLen + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16) {
                return@withContext BackupResult(false, 0, "Invalid or corrupted backup file.")
            }

            // Verify Magic Header
            for (i in MAGIC_HEADER.indices) {
                if (allBytes[i] != MAGIC_HEADER[i]) {
                    return@withContext BackupResult(false, 0, "Not a valid .imava backup container.")
                }
            }

            var offset = headerLen
            val salt = allBytes.copyOfRange(offset, offset + SALT_LENGTH_BYTES)
            offset += SALT_LENGTH_BYTES

            val iv = allBytes.copyOfRange(offset, offset + IV_LENGTH_BYTES)
            offset += IV_LENGTH_BYTES

            val ciphertext = allBytes.copyOfRange(offset, allBytes.size)

            // Derive key & decrypt
            val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            val decryptedZipBytes = try {
                cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                return@withContext BackupResult(false, 0, "Incorrect password or corrupt backup file.")
            }

            val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
            val restoredManifestLines = mutableListOf<String>()
            var restoredFilesCount = 0

            // Unzip payload
            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.tsv") {
                        val reader = zipIn.bufferedReader(Charsets.UTF_8)
                        var line = reader.readLine()
                        while (line != null) {
                            if (line.isNotBlank()) {
                                restoredManifestLines.add(line)
                            }
                            line = reader.readLine()
                        }
                    } else if (entry.name.startsWith("files/")) {
                        val filename = entry.name.removePrefix("files/")
                        val targetFile = File(vaultDir, filename)
                        FileOutputStream(targetFile).use { fileOut ->
                            zipIn.copyTo(fileOut)
                        }
                        if (filename.startsWith("vault_") && !filename.endsWith(".meta")) {
                            restoredFilesCount++
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // Restore Room metadata
            for (line in restoredManifestLines) {
                val parts = line.split("\t")
                if (parts.isNotEmpty()) {
                    val mediaId = parts[0].toLongOrNull() ?: continue
                    val originalPath = parts.getOrElse(1) { "" }
                    val vaultPath = parts.getOrElse(2) { File(vaultDir, "vault_" + mediaId).absolutePath }
                    val mimeType = parts.getOrElse(3) { "image/jpeg" }
                    val dateTaken = parts.getOrElse(4) { System.currentTimeMillis().toString() }.toLongOrNull() ?: System.currentTimeMillis()
                    val size = parts.getOrElse(5) { "0" }.toLongOrNull() ?: 0L
                    val width = parts.getOrElse(6) { "0" }.toIntOrNull() ?: 0
                    val height = parts.getOrElse(7) { "0" }.toIntOrNull() ?: 0
                    val bucketId = parts.getOrElse(8) { "0" }.toLongOrNull() ?: 0L
                    val bucketName = parts.getOrElse(9) { "" }
                    val durationMs = parts.getOrElse(10) { "0" }.toLongOrNull() ?: 0L
                    val isFavorite = parts.getOrElse(11) { "false" }.toBoolean()
                    val tags = parts.getOrElse(12) { "" }

                    val entity = MediaMetadataEntity(
                        mediaId = mediaId,
                        isFavorite = isFavorite,
                        isHidden = true,
                        isTrashed = false,
                        trashTime = 0L,
                        originalPath = originalPath,
                        vaultPath = vaultPath,
                        mimeType = mimeType,
                        dateTaken = dateTaken,
                        size = size,
                        width = width,
                        height = height,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        durationMs = durationMs,
                        tags = tags
                    )
                    database.metadataDao().insertOrUpdate(entity)
                }
            }

            BackupResult(true, restoredFilesCount, "Successfully restored " + restoredFilesCount + " items into Vault.")
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult(false, 0, "Restore failed: " + e.localizedMessage)
        }
    }
}

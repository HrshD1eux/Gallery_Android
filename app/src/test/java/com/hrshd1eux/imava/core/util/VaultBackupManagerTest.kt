package com.hrshd1eux.imava.core.util

import android.content.Context
import com.hrshd1eux.imava.data.database.GalleryDatabase
import com.hrshd1eux.imava.data.database.MediaMetadataEntity
import com.hrshd1eux.imava.data.database.MetadataDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class VaultBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDatabase = mockk<GalleryDatabase>(relaxed = true)
    private val mockDao = mockk<MetadataDao>(relaxed = true)

    private lateinit var filesDir: File
    private lateinit var vaultDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        vaultDir = File(filesDir, "vault").apply { mkdirs() }

        every { mockContext.filesDir } returns filesDir
        every { mockDatabase.metadataDao() } returns mockDao
    }

    @Test
    fun testExportAndRestoreVaultBackup_success() = runTest {
        val vaultFile = File(vaultDir, "vault_101").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        val metaFile = File(vaultDir, "vault_101.meta").apply { writeText("originalName=test.jpg") }

        val entity = MediaMetadataEntity(
            mediaId = 101L,
            isFavorite = true,
            isHidden = true,
            originalPath = "/storage/test.jpg",
            vaultPath = vaultFile.absolutePath,
            mimeType = "image/jpeg",
            dateTaken = 1700000000000L,
            size = 8L,
            width = 100,
            height = 100,
            tags = "vacation,beach"
        )

        coEvery { mockDao.getHiddenMetadata() } returns listOf(entity)

        val outputStream = ByteArrayOutputStream()
        val passphrase = "MasterPassword123".toCharArray()

        val exportResult = VaultBackupManager.exportVaultBackup(
            context = mockContext,
            database = mockDatabase,
            targetOutputStream = outputStream,
            passphrase = passphrase
        )

        assertTrue(exportResult.success)
        assertEquals(1, exportResult.itemsCount)
        assertTrue(outputStream.size() > 50)

        vaultFile.delete()
        metaFile.delete()

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val restoreResult = VaultBackupManager.restoreVaultBackup(
            context = mockContext,
            database = mockDatabase,
            inputStream = inputStream,
            passphrase = passphrase
        )

        assertTrue(restoreResult.success)
        assertEquals(1, restoreResult.itemsCount)

        val restoredVaultFile = File(vaultDir, "vault_101")
        assertTrue(restoredVaultFile.exists())
        assertEquals(8L, restoredVaultFile.length())

        coVerify(atLeast = 1) { mockDao.insertOrUpdate(any()) }
    }

    @Test
    fun testRestoreVaultBackup_wrongPassphrase_fails() = runTest {
        val vaultFile = File(vaultDir, "vault_202").apply { writeBytes(byteArrayOf(10, 20, 30)) }
        val entity = MediaMetadataEntity(
            mediaId = 202L,
            isHidden = true
        )
        coEvery { mockDao.getHiddenMetadata() } returns listOf(entity)

        val outputStream = ByteArrayOutputStream()
        VaultBackupManager.exportVaultBackup(
            context = mockContext,
            database = mockDatabase,
            targetOutputStream = outputStream,
            passphrase = "CorrectPassword".toCharArray()
        )

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val restoreResult = VaultBackupManager.restoreVaultBackup(
            context = mockContext,
            database = mockDatabase,
            inputStream = inputStream,
            passphrase = "WrongPassword".toCharArray()
        )

        assertFalse(restoreResult.success)
        assertTrue(restoreResult.message.contains("Incorrect password") || restoreResult.message.contains("failed"))
    }

    @Test
    fun testRestoreVaultBackup_corruptedHeader_fails() = runTest {
        val corruptedBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val inputStream = ByteArrayInputStream(corruptedBytes)
        val restoreResult = VaultBackupManager.restoreVaultBackup(
            context = mockContext,
            database = mockDatabase,
            inputStream = inputStream,
            passphrase = "AnyPassword123".toCharArray()
        )

        assertFalse(restoreResult.success)
        assertTrue(restoreResult.message.contains("Not a valid") || restoreResult.message.contains("format") || restoreResult.message.contains("corrupted"))
    }

    @Test
    fun testRestoreVaultBackup_truncatedStream_fails() = runTest {
        val magicHeader = "IMAVABAK".toByteArray(Charsets.UTF_8)
        val truncatedStream = ByteArrayInputStream(magicHeader) // Missing salt, IV, and payload

        val restoreResult = VaultBackupManager.restoreVaultBackup(
            context = mockContext,
            database = mockDatabase,
            inputStream = truncatedStream,
            passphrase = "AnyPassword123".toCharArray()
        )

        assertFalse(restoreResult.success)
        assertTrue(restoreResult.message.contains("Invalid") || restoreResult.message.contains("corrupted") || restoreResult.message.contains("failed"))
    }
}


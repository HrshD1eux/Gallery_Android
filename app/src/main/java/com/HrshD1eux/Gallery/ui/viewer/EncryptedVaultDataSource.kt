package com.HrshD1eux.Gallery.ui.viewer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.HrshD1eux.Gallery.core.util.VaultCrypto
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

@OptIn(UnstableApi::class)
class EncryptedVaultDataSource(
    private val file: File
) : DataSource {

    private var uri: Uri? = null
    private var fileInputStream: FileInputStream? = null
    private var cipherInputStream: InputStream? = null
    private var bytesRemaining: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op for local encrypted vault file streaming
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val fis = FileInputStream(file)
        fileInputStream = fis

        val iv = ByteArray(12)
        val ivRead = fis.read(iv)
        if (ivRead != 12) {
            fis.close()
            throw IOException("Invalid encrypted vault video file: Missing 12-byte IV header")
        }

        val secretKey = VaultCrypto.getSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val cis = CipherInputStream(fis, cipher)
        cipherInputStream = cis

        if (dataSpec.position > 0) {
            var skipped = 0L
            val skipBuffer = ByteArray(8192)
            while (skipped < dataSpec.position) {
                val toRead = minOf(skipBuffer.size.toLong(), dataSpec.position - skipped).toInt()
                val read = cis.read(skipBuffer, 0, toRead)
                if (read == -1) break
                skipped += read
            }
        }

        val estimatedPlaintextLength = maxOf(0L, file.length() - 12L - 16L)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            maxOf(0L, estimatedPlaintextLength - dataSpec.position)
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val bytesRead = cipherInputStream?.read(buffer, offset, bytesToRead) ?: -1
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            cipherInputStream?.close()
        } catch (_: Exception) {}
        try {
            fileInputStream?.close()
        } catch (_: Exception) {}
        cipherInputStream = null
        fileInputStream = null
    }

    class Factory(private val file: File) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedVaultDataSource(file)
    }
}

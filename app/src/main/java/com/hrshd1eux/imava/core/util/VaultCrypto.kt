package com.hrshd1eux.imava.core.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object VaultCrypto {

    private const val KEY_ALIAS = "GalleryVaultMasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits for GCM
    private const val TAG_SIZE = 128 // 128 bit authentication tag

    @androidx.annotation.VisibleForTesting
    var testSecretKey: SecretKey? = null

    @Synchronized
    fun getSecretKey(): SecretKey {
        testSecretKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                if (key != null) return key
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) return entry.secretKey
                throw KeystoreUnavailableException("Master key alias '$KEY_ALIAS' exists in KeyStore but secret key could not be retrieved.")
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            throw KeystoreUnavailableException(
                "Hardware AndroidKeyStore is unavailable. Cannot perform secure encryption without hardware-backed master key.",
                e
            )
        }
    }

    private fun isKnownMediaHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        // JPEG: FF D8 FF
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return true
        // PNG: 89 50 4E 47
        if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) return true
        // GIF: 47 49 46 38
        if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) return true
        // WEBP / RIFF: 52 49 46 46
        if (header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte()) return true
        // MP4 / QuickTime ftyp at offset 4: 66 74 79 70
        if (header.size >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) return true
        return false
    }

    fun encrypt(inputStream: InputStream, outputStream: OutputStream) {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        outputStream.write(iv)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val encryptedChunk = cipher.update(buffer, 0, bytesRead)
            if (encryptedChunk != null && encryptedChunk.isNotEmpty()) {
                outputStream.write(encryptedChunk)
            }
        }
        val finalChunk = cipher.doFinal()
        if (finalChunk != null && finalChunk.isNotEmpty()) {
            outputStream.write(finalChunk)
        }
        outputStream.flush()
    }

    fun decrypt(inputStream: InputStream, outputStream: OutputStream) {
        val bufferedInput = if (inputStream.markSupported()) inputStream else java.io.BufferedInputStream(inputStream)
        bufferedInput.mark(16)
        val header = ByteArray(IV_SIZE)
        var totalRead = 0
        while (totalRead < IV_SIZE) {
            val count = bufferedInput.read(header, totalRead, IV_SIZE - totalRead)
            if (count == -1) break
            totalRead += count
        }

        if (totalRead < IV_SIZE) {
            bufferedInput.reset()
            bufferedInput.copyTo(outputStream)
            outputStream.flush()
            return
        }

        // If file is already unencrypted media, bypass decryption
        if (isKnownMediaHeader(header)) {
            bufferedInput.reset()
            bufferedInput.copyTo(outputStream)
            outputStream.flush()
            return
        }

        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(TAG_SIZE, header)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
            val decryptedChunk = cipher.update(buffer, 0, bytesRead)
            if (decryptedChunk != null && decryptedChunk.isNotEmpty()) {
                outputStream.write(decryptedChunk)
            }
        }
        val finalChunk = cipher.doFinal()
        if (finalChunk != null && finalChunk.isNotEmpty()) {
            outputStream.write(finalChunk)
        }
        outputStream.flush()
    }

    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_KEY_LENGTH = 256

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            encoded ?: java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            val decoded = Base64.decode(str, Base64.NO_WRAP)
            decoded ?: java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    fun hashPin(pin: String, salt: ByteArray): String {
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return base64Encode(hash)
    }

    // legacy SHA-256 compat
    fun hashPinLegacySha256(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return base64Encode(hash)
    }

    data class PinVerificationResult(
        val isValid: Boolean,
        val needsUpgrade: Boolean = false
    )

    fun verifyPin(input: String, storedPinHash: String, salt: ByteArray): PinVerificationResult {
        val decryptedHash = try {
            decryptString(storedPinHash)
        } catch (_: Exception) {
            storedPinHash
        }

        // PBKDF2 check
        val pbkdf2Hash = hashPin(input, salt)
        if (pbkdf2Hash == decryptedHash || pbkdf2Hash == storedPinHash) {
            return PinVerificationResult(isValid = true, needsUpgrade = false)
        }

        // legacy fallback
        val legacyHash = hashPinLegacySha256(input, salt)
        if (legacyHash == decryptedHash || legacyHash == storedPinHash) {
            return PinVerificationResult(isValid = true, needsUpgrade = true)
        }

        return PinVerificationResult(isValid = false, needsUpgrade = false)
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun encryptString(plaintext: String): String {
        val inStream = java.io.ByteArrayInputStream(plaintext.toByteArray(Charsets.UTF_8))
        val outStream = java.io.ByteArrayOutputStream()
        encrypt(inStream, outStream)
        return base64Encode(outStream.toByteArray())
    }

    fun decryptString(ciphertextBase64: String): String {
        val bytes = base64Decode(ciphertextBase64)
        val inStream = java.io.ByteArrayInputStream(bytes)
        val outStream = java.io.ByteArrayOutputStream()
        decrypt(inStream, outStream)
        return String(outStream.toByteArray(), Charsets.UTF_8)
    }
}

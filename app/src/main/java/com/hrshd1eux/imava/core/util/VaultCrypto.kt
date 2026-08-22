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
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) return entry.secretKey
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

    /**
     * Encrypts input stream content and writes IV (12 bytes) + AES-256-GCM ciphertext to output stream.
     */
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

    /**
     * Decrypts stream containing IV (12 bytes) + AES-256-GCM ciphertext back to plaintext output stream.
     */
    fun decrypt(inputStream: InputStream, outputStream: OutputStream) {
        val iv = ByteArray(IV_SIZE)
        val readIv = inputStream.read(iv)
        if (readIv != IV_SIZE) {
            throw IllegalArgumentException("Invalid encrypted vault file: missing IV header")
        }

        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
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

    /**
     * Hashes PIN with salt using PBKDF2WithHmacSHA256 (100,000 iterations) for brute-force resistant storage.
     */
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

    /**
     * Legacy single-round SHA-256 for backward-compatibility verification and transparent migration.
     */
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

    /**
     * Verifies an input PIN against stored encrypted/raw hash, supporting transparent migration from legacy SHA-256.
     */
    fun verifyPin(input: String, storedPinHash: String, salt: ByteArray): PinVerificationResult {
        val decryptedHash = try {
            decryptString(storedPinHash)
        } catch (_: Exception) {
            storedPinHash
        }

        // 1. Primary check: PBKDF2WithHmacSHA256
        val pbkdf2Hash = hashPin(input, salt)
        if (pbkdf2Hash == decryptedHash || pbkdf2Hash == storedPinHash) {
            return PinVerificationResult(isValid = true, needsUpgrade = false)
        }

        // 2. Fallback check: Legacy SHA-256 for existing installs (flags needsUpgrade for auto-migration)
        val legacyHash = hashPinLegacySha256(input, salt)
        if (legacyHash == decryptedHash || legacyHash == storedPinHash) {
            return PinVerificationResult(isValid = true, needsUpgrade = true)
        }

        return PinVerificationResult(isValid = false, needsUpgrade = false)
    }

    /**
     * Generates a cryptographically secure 16-byte salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Encrypts plaintext string using AndroidKeyStore master key returning Base64 representation.
     */
    fun encryptString(plaintext: String): String {
        val inStream = java.io.ByteArrayInputStream(plaintext.toByteArray(Charsets.UTF_8))
        val outStream = java.io.ByteArrayOutputStream()
        encrypt(inStream, outStream)
        return base64Encode(outStream.toByteArray())
    }

    /**
     * Decrypts AndroidKeyStore master key encrypted Base64 string back to plaintext.
     */
    fun decryptString(ciphertextBase64: String): String {
        val bytes = base64Decode(ciphertextBase64)
        val inStream = java.io.ByteArrayInputStream(bytes)
        val outStream = java.io.ByteArrayOutputStream()
        decrypt(inStream, outStream)
        return String(outStream.toByteArray(), Charsets.UTF_8)
    }
}

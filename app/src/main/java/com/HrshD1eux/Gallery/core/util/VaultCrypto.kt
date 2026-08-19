package com.HrshD1eux.Gallery.core.util

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

object VaultCrypto {

    private const val KEY_ALIAS = "GalleryVaultMasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits for GCM
    private const val TAG_SIZE = 128 // 128 bit authentication tag

    private var fallbackKey: SecretKey? = null

    @Synchronized
    private fun getSecretKey(): SecretKey {
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
            // Fallback for local JVM unit test environment where AndroidKeyStore provider is absent
            getFallbackTestKey()
        }
    }

    @Synchronized
    private fun getFallbackTestKey(): SecretKey {
        if (fallbackKey == null) {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            fallbackKey = keyGenerator.generateKey()
        }
        return fallbackKey!!
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

    /**
     * Hashes PIN with salt using SHA-256 for secure non-plaintext storage.
     */
    fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
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
        return Base64.encodeToString(outStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Decrypts AndroidKeyStore master key encrypted Base64 string back to plaintext.
     */
    fun decryptString(ciphertextBase64: String): String {
        val bytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val inStream = java.io.ByteArrayInputStream(bytes)
        val outStream = java.io.ByteArrayOutputStream()
        decrypt(inStream, outStream)
        return String(outStream.toByteArray(), Charsets.UTF_8)
    }
}

package com.hrshd1eux.imava.core.util

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.crypto.KeyGenerator

class VaultCryptoTest {

    @Before
    fun setUp() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        VaultCrypto.testSecretKey = keyGen.generateKey()
    }

    @After
    fun tearDown() {
        VaultCrypto.testSecretKey = null
    }

    @Test
    fun testEncryptDecrypt_byteForByteIntegrity() {
        val randomBytes = ByteArray(1024 * 64) // 64 KB of random data
        Random(42).nextBytes(randomBytes)

        val encryptedStream = ByteArrayOutputStream()
        VaultCrypto.encrypt(ByteArrayInputStream(randomBytes), encryptedStream)

        val ciphertext = encryptedStream.toByteArray()
        assertTrue(ciphertext.size > randomBytes.size) // IV (12) + Tag (16) overhead

        val decryptedStream = ByteArrayOutputStream()
        VaultCrypto.decrypt(ByteArrayInputStream(ciphertext), decryptedStream)

        val decryptedBytes = decryptedStream.toByteArray()
        assertArrayEquals(randomBytes, decryptedBytes)
    }

    @Test
    fun testEncryptDecrypt_jpegHeaderIntegrity() {
        val jpegData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(2048) { it.toByte() }

        val encryptedStream = ByteArrayOutputStream()
        VaultCrypto.encrypt(ByteArrayInputStream(jpegData), encryptedStream)

        val decryptedStream = ByteArrayOutputStream()
        VaultCrypto.decrypt(ByteArrayInputStream(encryptedStream.toByteArray()), decryptedStream)

        assertArrayEquals(jpegData, decryptedStream.toByteArray())
    }

    @Test
    fun testEncryptDecrypt_pngHeaderIntegrity() {
        val pngData = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()) + ByteArray(2048) { (it * 2).toByte() }

        val encryptedStream = ByteArrayOutputStream()
        VaultCrypto.encrypt(ByteArrayInputStream(pngData), encryptedStream)

        val decryptedStream = ByteArrayOutputStream()
        VaultCrypto.decrypt(ByteArrayInputStream(encryptedStream.toByteArray()), decryptedStream)

        assertArrayEquals(pngData, decryptedStream.toByteArray())
    }

    @Test
    fun testDecrypt_unencryptedMediaBypass() {
        // Plaintext JPEG data fed directly into decrypt should safely pass through without crashing
        val rawJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46)

        val outStream = ByteArrayOutputStream()
        VaultCrypto.decrypt(ByteArrayInputStream(rawJpeg), outStream)

        assertArrayEquals(rawJpeg, outStream.toByteArray())
    }

    @Test
    fun testPbkdf2PinHashingAndVerification() {
        val pin = "1234"
        val salt = VaultCrypto.generateSalt()
        val hash = VaultCrypto.hashPin(pin, salt)

        val verifySuccess = VaultCrypto.verifyPin("1234", hash, salt)
        assertTrue(verifySuccess.isValid)
        assertFalse(verifySuccess.needsUpgrade)

        val verifyFail = VaultCrypto.verifyPin("9999", hash, salt)
        assertFalse(verifyFail.isValid)
    }
}

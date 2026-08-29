package com.hrshd1eux.imava.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hrshd1eux.imava.core.util.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class VaultCryptoIntegrationTest {

    @Test
    fun testEncryptionAndDecryptionCycle() {
        val originalText = "Top secret media content for gallery vault testing!"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedStream = ByteArrayOutputStream()
        VaultCrypto.encrypt(ByteArrayInputStream(originalBytes), encryptedStream)
        val encryptedBytes = encryptedStream.toByteArray()

        assertFalse(String(encryptedBytes, Charsets.UTF_8).contains(originalText))

        val decryptedStream = ByteArrayOutputStream()
        VaultCrypto.decrypt(ByteArrayInputStream(encryptedBytes), decryptedStream)
        val decryptedBytes = decryptedStream.toByteArray()

        assertArrayEquals(originalBytes, decryptedBytes)
        assertEquals(originalText, String(decryptedBytes, Charsets.UTF_8))
    }

    @Test
    fun testPinHashingWithSalt() {
        val pin = "1234"
        val salt = VaultCrypto.generateSalt()

        assertEquals(16, salt.size)

        val hash1 = VaultCrypto.hashPin(pin, salt)
        val hash2 = VaultCrypto.hashPin(pin, salt)

        assertNotNull(hash1)
        assertEquals(hash1, hash2)

        val wrongHash = VaultCrypto.hashPin("4321", salt)
        assertFalse(hash1 == wrongHash)
    }
}

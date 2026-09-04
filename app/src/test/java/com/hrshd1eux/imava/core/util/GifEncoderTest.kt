package com.hrshd1eux.imava.core.util

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class GifEncoderTest {

    @Test
    fun testGifEncoder_writesValidGifHeaderAndTrailer() {
        val outputStream = ByteArrayOutputStream()
        val encoder = GifEncoder()
        encoder.setDelay(100)
        encoder.setRepeat(0)

        val started = encoder.start(outputStream)
        assertTrue(started)

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.width } returns 10
        every { mockBitmap.height } returns 10
        every { mockBitmap.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val arr = firstArg<IntArray>()
            arr.fill(0xFF0000)
        }

        val frameAdded = encoder.addFrame(mockBitmap)
        assertTrue(frameAdded)

        val finished = encoder.finish()
        assertTrue(finished)

        val bytes = outputStream.toByteArray()
        assertTrue(bytes.size > 10)

        // Check GIF89a magic header
        val header = String(bytes.copyOfRange(0, 6))
        assertEquals("GIF89a", header)

        // Check GIF trailer 0x3B (59)
        assertEquals(0x3B.toByte(), bytes.last())
    }
}

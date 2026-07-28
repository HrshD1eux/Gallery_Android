package com.HrshD1eux.Gallery.core.util

import android.graphics.Bitmap
import android.graphics.Matrix
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class PhotoEditorUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Bitmap::class)
        mockkConstructor(Matrix::class)
        every { anyConstructed<Matrix>().isIdentity } returns true
    }

    @Test
    fun testTransformBitmap_returnsBitmap() {
        val mockSource = mockk<Bitmap>(relaxed = true)
        every { mockSource.width } returns 100
        every { mockSource.height } returns 100
        every { mockSource.config } returns Bitmap.Config.ARGB_8888
        every { mockSource.copy(any(), any()) } returns mockSource

        val result = PhotoEditorUtils.transformBitmap(
            source = mockSource,
            rotationDegrees = 0f,
            flipHorizontal = false,
            flipVertical = false,
            brightnessOffset = 0f
        )

        assertNotNull(result)
    }

    @Test
    fun testDuplicateFinder_hashesAreConsistent() {
        val mockSource = mockk<Bitmap>(relaxed = true)
        every { mockSource.getPixel(any(), any()) } returns 0xFF112233.toInt()
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns mockSource

        val hash1 = DuplicateFinder.computeDHash(mockSource)
        val hash2 = DuplicateFinder.computeDHash(mockSource)

        org.junit.Assert.assertEquals(hash1, hash2)
    }
}

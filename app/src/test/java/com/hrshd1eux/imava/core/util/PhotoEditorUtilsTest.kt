package com.hrshd1eux.imava.core.util

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

    @Test
    fun testFormatUtils_fileSizeFormatting() {
        val size500Kb = 512 * 1024L
        val formatted500Kb = FormatUtils.formatFileSize(size500Kb)
        org.junit.Assert.assertEquals("512 KB", formatted500Kb)

        val size84Kb = (84.5 * 1024L).toLong()
        val formatted84Kb = FormatUtils.formatFileSize(size84Kb)
        org.junit.Assert.assertTrue(formatted84Kb.contains("KB"))

        val size10Mb = 10 * 1024 * 1024L
        val formatted10Mb = FormatUtils.formatFileSize(size10Mb)
        org.junit.Assert.assertEquals("10.00 MB", formatted10Mb)

        val size2Gb = 2L * 1024 * 1024 * 1024L
        val formatted2Gb = FormatUtils.formatFileSize(size2Gb)
        org.junit.Assert.assertEquals("2.00 GB", formatted2Gb)
    }

    @Test
    fun testHapticUtil_safeExecution() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.getBoolean(any(), any()) } returns true

        HapticUtil.performClick(mockContext)
        HapticUtil.performSelection(mockContext)
        HapticUtil.performLongPress(mockContext)
        HapticUtil.performSuccess(mockContext)
        HapticUtil.performError(mockContext)
        HapticUtil.performTick(mockContext)
    }

    @Test
    fun testMotionPhotoInfo_dataModel() {
        val info = MotionPhotoInfo(isMotionPhoto = true, videoOffsetFromEnd = 1024L, videoLength = 1024L)
        org.junit.Assert.assertTrue(info.isMotionPhoto)
        org.junit.Assert.assertEquals(1024L, info.videoOffsetFromEnd)
        org.junit.Assert.assertEquals(1024L, info.videoLength)

        val nonMotion = MotionPhotoInfo(isMotionPhoto = false)
        org.junit.Assert.assertFalse(nonMotion.isMotionPhoto)
    }

    @Test
    fun testStorageStats_modelCalculations() {
        val stats = com.hrshd1eux.imava.ui.MainViewModel.StorageStats(
            photosBytes = 1024 * 1024 * 100L, // 100 MB
            videosBytes = 1024 * 1024 * 500L, // 500 MB
            vaultBytes = 1024 * 1024 * 50L,   // 50 MB
            trashBytes = 1024 * 1024 * 10L    // 10 MB
        )
        org.junit.Assert.assertEquals("100.00 MB", FormatUtils.formatFileSize(stats.photosBytes))
        org.junit.Assert.assertEquals("500.00 MB", FormatUtils.formatFileSize(stats.videosBytes))
        org.junit.Assert.assertEquals("50.00 MB", FormatUtils.formatFileSize(stats.vaultBytes))
        org.junit.Assert.assertEquals("10.00 MB", FormatUtils.formatFileSize(stats.trashBytes))
    }
}

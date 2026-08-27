package com.hrshd1eux.imava.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageMakerUtilTest {

    @Test
    fun testComputeCellLayouts_returnsValidCellsFor2To9Images() {
        for (count in 2..9) {
            val cells = CollageMakerUtil.computeCellLayouts(count)
            assertEquals(count, cells.size)
            for (cell in cells) {
                assertTrue(cell.leftFraction >= 0f && cell.leftFraction <= 1f)
                assertTrue(cell.topFraction >= 0f && cell.topFraction <= 1f)
                assertTrue(cell.rightFraction > cell.leftFraction && cell.rightFraction <= 1f)
                assertTrue(cell.bottomFraction > cell.topFraction && cell.bottomFraction <= 1f)
            }
        }
    }

    @Test
    fun testCollageAspectRatio_ratios() {
        val square = CollageMakerUtil.CollageAspectRatio.SQUARE_1_1
        assertEquals(1.0f, square.widthRatio / square.heightRatio, 0.001f)

        val portrait = CollageMakerUtil.CollageAspectRatio.PORTRAIT_4_5
        assertEquals(0.8f, portrait.widthRatio / portrait.heightRatio, 0.001f)

        val story = CollageMakerUtil.CollageAspectRatio.STORY_9_16
        assertEquals(9f / 16f, story.widthRatio / story.heightRatio, 0.001f)

        val landscape = CollageMakerUtil.CollageAspectRatio.LANDSCAPE_16_9
        assertEquals(16f / 9f, landscape.widthRatio / landscape.heightRatio, 0.001f)
    }
}

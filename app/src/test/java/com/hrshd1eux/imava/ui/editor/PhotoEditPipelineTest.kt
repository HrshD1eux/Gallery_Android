package com.hrshd1eux.imava.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditPipelineTest {

    @Test
    fun testHasModifications_defaultIsFalse() {
        val pipeline = PhotoEditPipeline()
        assertFalse(pipeline.hasModifications())
    }

    @Test
    fun testHasModifications_rotationIsTrue() {
        val pipeline = PhotoEditPipeline(rotationDegrees = 90f)
        assertTrue(pipeline.hasModifications())
    }

    @Test
    fun testHasModifications_colorTuningIsTrue() {
        val pipeline = PhotoEditPipeline(contrast = 1.2f)
        assertTrue(pipeline.hasModifications())
    }
}

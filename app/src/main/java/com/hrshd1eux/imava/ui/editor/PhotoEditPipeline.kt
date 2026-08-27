package com.hrshd1eux.imava.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.hrshd1eux.imava.core.util.PhotoEditorUtils
import com.hrshd1eux.imava.core.util.PhotoMarkupUtils

data class PhotoEditPipeline(
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRect: RectF? = null,
    val brightnessOffset: Float = 0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val warmth: Float = 0f,
    val preset: String = "none",
    val strokes: List<PhotoMarkupUtils.MarkupStroke> = emptyList(),
    val referencePreviewWidth: Int = 0,
    val referencePreviewHeight: Int = 0
) {
    fun hasModifications(): Boolean {
        return rotationDegrees % 360f != 0f ||
                flipHorizontal ||
                flipVertical ||
                cropRect != null ||
                brightnessOffset != 0f ||
                contrast != 1.0f ||
                saturation != 1.0f ||
                warmth != 0f ||
                preset != "none" ||
                strokes.isNotEmpty()
    }

    fun apply(sourceBitmap: Bitmap): Bitmap {
        val transformed = PhotoEditorUtils.transformBitmap(
            source = sourceBitmap,
            rotationDegrees = rotationDegrees,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            cropRect = cropRect,
            brightnessOffset = brightnessOffset,
            contrast = contrast,
            saturation = saturation,
            warmth = warmth,
            preset = preset
        )

        if (strokes.isEmpty()) {
            return transformed
        }

        val scaleX = if (referencePreviewWidth > 0) transformed.width.toFloat() / referencePreviewWidth.toFloat() else 1f
        val scaleY = if (referencePreviewHeight > 0) transformed.height.toFloat() / referencePreviewHeight.toFloat() else 1f

        val scaledStrokes = strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { pt -> PointF(pt.x * scaleX, pt.y * scaleY) },
                strokeWidth = stroke.strokeWidth * scaleX,
                startPoint = stroke.startPoint?.let { PointF(it.x * scaleX, it.y * scaleY) },
                endPoint = stroke.endPoint?.let { PointF(it.x * scaleX, it.y * scaleY) }
            )
        }

        return PhotoMarkupUtils.renderStrokesToBitmap(transformed, scaledStrokes)
    }
}

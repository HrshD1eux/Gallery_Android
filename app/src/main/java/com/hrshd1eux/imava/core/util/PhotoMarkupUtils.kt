package com.hrshd1eux.imava.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object PhotoMarkupUtils {

    enum class MarkupTool {
        PEN,
        ARROW,
        RECTANGLE,
        CIRCLE,
        PIXELATE_MOSAIC
    }

    data class MarkupStroke(
        val tool: MarkupTool,
        val points: List<android.graphics.PointF>,
        val color: Int,
        val strokeWidth: Float,
        val startPoint: android.graphics.PointF? = null,
        val endPoint: android.graphics.PointF? = null
    )

    fun createMosaicBitmap(source: Bitmap, pixelBlockSize: Int = 24): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source

        val downscaledW = (w / pixelBlockSize).coerceAtLeast(1)
        val downscaledH = (h / pixelBlockSize).coerceAtLeast(1)

        val small = Bitmap.createScaledBitmap(source, downscaledW, downscaledH, false)
        val mosaic = Bitmap.createScaledBitmap(small, w, h, false)
        if (small != source && small != mosaic) {
            small.recycle()
        }
        return mosaic
    }

    fun renderStrokesToBitmap(
        source: Bitmap,
        strokes: List<MarkupStroke>
    ): Bitmap {
        if (strokes.isEmpty()) return source

        val resultBitmap = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val mosaicStrokes = strokes.filter { it.tool == MarkupTool.PIXELATE_MOSAIC }
        val vectorStrokes = strokes.filter { it.tool != MarkupTool.PIXELATE_MOSAIC }

        // Render Mosaic / Pixelation
        if (mosaicStrokes.isNotEmpty()) {
            val mosaicBitmap = createMosaicBitmap(source, pixelBlockSize = 28)
            val maskBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(maskBitmap)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            for (stroke in mosaicStrokes) {
                maskPaint.strokeWidth = stroke.strokeWidth
                val path = Path()
                stroke.points.forEachIndexed { index, pt ->
                    if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                maskCanvas.drawPath(path, maskPaint)
            }

            // Draw mosaic clipped by mask
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            maskCanvas.drawBitmap(mosaicBitmap, 0f, 0f, paint)

            canvas.drawBitmap(maskBitmap, 0f, 0f, null)

            mosaicBitmap.recycle()
            maskBitmap.recycle()
        }

        // Render Vector Tools (Pen, Arrow, Rect, Circle)
        val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in vectorStrokes) {
            vectorPaint.color = stroke.color
            vectorPaint.strokeWidth = stroke.strokeWidth

            when (stroke.tool) {
                MarkupTool.PEN -> {
                    vectorPaint.style = Paint.Style.STROKE
                    val path = Path()
                    stroke.points.forEachIndexed { index, pt ->
                        if (index == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    canvas.drawPath(path, vectorPaint)
                }
                MarkupTool.RECTANGLE -> {
                    val p1 = stroke.startPoint ?: stroke.points.firstOrNull()
                    val p2 = stroke.endPoint ?: stroke.points.lastOrNull()
                    if (p1 != null && p2 != null) {
                        vectorPaint.style = Paint.Style.STROKE
                        val rect = RectF(
                            minOf(p1.x, p2.x),
                            minOf(p1.y, p2.y),
                            maxOf(p1.x, p2.x),
                            maxOf(p1.y, p2.y)
                        )
                        canvas.drawRoundRect(rect, 12f, 12f, vectorPaint)
                    }
                }
                MarkupTool.CIRCLE -> {
                    val p1 = stroke.startPoint ?: stroke.points.firstOrNull()
                    val p2 = stroke.endPoint ?: stroke.points.lastOrNull()
                    if (p1 != null && p2 != null) {
                        vectorPaint.style = Paint.Style.STROKE
                        val rect = RectF(
                            minOf(p1.x, p2.x),
                            minOf(p1.y, p2.y),
                            maxOf(p1.x, p2.x),
                            maxOf(p1.y, p2.y)
                        )
                        canvas.drawOval(rect, vectorPaint)
                    }
                }
                MarkupTool.ARROW -> {
                    val p1 = stroke.startPoint ?: stroke.points.firstOrNull()
                    val p2 = stroke.endPoint ?: stroke.points.lastOrNull()
                    if (p1 != null && p2 != null) {
                        vectorPaint.style = Paint.Style.STROKE
                        // Draw main line
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, vectorPaint)

                        // Draw arrow head
                        val angle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                        val arrowLength = (stroke.strokeWidth * 3.5f).coerceIn(24f, 64f)
                        val arrowAngle = Math.toRadians(32.0)

                        val x1 = (p2.x - arrowLength * cos(angle - arrowAngle)).toFloat()
                        val y1 = (p2.y - arrowLength * sin(angle - arrowAngle)).toFloat()
                        val x2 = (p2.x - arrowLength * cos(angle + arrowAngle)).toFloat()
                        val y2 = (p2.y - arrowLength * sin(angle + arrowAngle)).toFloat()

                        val arrowHeadPath = Path().apply {
                            moveTo(p2.x, p2.y)
                            lineTo(x1, y1)
                            lineTo(x2, y2)
                            close()
                        }
                        vectorPaint.style = Paint.Style.FILL
                        canvas.drawPath(arrowHeadPath, vectorPaint)
                    }
                }
                else -> {}
            }
        }

        return resultBitmap
    }
}

package com.hrshd1eux.imava.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object CollageMakerUtil {

    enum class CollageAspectRatio(val widthRatio: Float, val heightRatio: Float) {
        SQUARE_1_1(1f, 1f),
        PORTRAIT_4_5(4f, 5f),
        STORY_9_16(9f, 16f),
        LANDSCAPE_16_9(16f, 9f)
    }

    enum class CollageShape {
        ROUNDED,
        CIRCLE,
        DIAMOND,
        HEXAGON,
        HEART,
        STAR
    }

    data class CollageCell(
        val leftFraction: Float,
        val topFraction: Float,
        val rightFraction: Float,
        val bottomFraction: Float
    )

    data class CellTransform(
        val scale: Float = 1f,
        val panX: Float = 0f,
        val panY: Float = 0f
    )

    fun computeCellLayouts(photoCount: Int, layoutVariant: Int = 0): List<CollageCell> {
        val count = photoCount.coerceIn(2, 9)
        return when (count) {
            2 -> {
                when (layoutVariant % 3) {
                    0 -> listOf( // Vertical split
                        CollageCell(0f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 1f, 1f)
                    )
                    1 -> listOf( // Horizontal split
                        CollageCell(0f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 1f, 1f)
                    )
                    else -> listOf( // Picture in picture / Overlay style
                        CollageCell(0f, 0f, 1f, 1f),
                        CollageCell(0.55f, 0.55f, 0.95f, 0.95f)
                    )
                }
            }
            3 -> {
                when (layoutVariant % 4) {
                    0 -> listOf( // 1 Top Hero + 2 Bottom
                        CollageCell(0f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 0.5f, 1f),
                        CollageCell(0.5f, 0.5f, 1f, 1f)
                    )
                    1 -> listOf( // 1 Left Hero + 2 Right
                        CollageCell(0f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 1f, 0.5f),
                        CollageCell(0.5f, 0.5f, 1f, 1f)
                    )
                    2 -> listOf( // 3 Columns
                        CollageCell(0f, 0f, 0.333f, 1f),
                        CollageCell(0.333f, 0f, 0.666f, 1f),
                        CollageCell(0.666f, 0f, 1f, 1f)
                    )
                    else -> listOf( // 3 Rows
                        CollageCell(0f, 0f, 1f, 0.333f),
                        CollageCell(0f, 0.333f, 1f, 0.666f),
                        CollageCell(0f, 0.666f, 1f, 1f)
                    )
                }
            }
            4 -> {
                when (layoutVariant % 4) {
                    0 -> listOf( // 2x2 Grid
                        CollageCell(0f, 0f, 0.5f, 0.5f),
                        CollageCell(0.5f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 0.5f, 1f),
                        CollageCell(0.5f, 0.5f, 1f, 1f)
                    )
                    1 -> listOf( // 1 Left Hero + 3 Right Stack
                        CollageCell(0f, 0f, 0.6f, 1f),
                        CollageCell(0.6f, 0f, 1f, 0.333f),
                        CollageCell(0.6f, 0.333f, 1f, 0.666f),
                        CollageCell(0.6f, 0.666f, 1f, 1f)
                    )
                    2 -> listOf( // 1 Top Hero + 3 Bottom Columns
                        CollageCell(0f, 0f, 1f, 0.6f),
                        CollageCell(0f, 0.6f, 0.333f, 1f),
                        CollageCell(0.333f, 0.6f, 0.666f, 1f),
                        CollageCell(0.666f, 0.6f, 1f, 1f)
                    )
                    else -> listOf( // 4 Vertical Strips
                        CollageCell(0f, 0f, 0.25f, 1f),
                        CollageCell(0.25f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 0.75f, 1f),
                        CollageCell(0.75f, 0f, 1f, 1f)
                    )
                }
            }
            5 -> {
                when (layoutVariant % 3) {
                    0 -> listOf( // 2 Top + 3 Bottom
                        CollageCell(0f, 0f, 0.5f, 0.5f),
                        CollageCell(0.5f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 0.333f, 1f),
                        CollageCell(0.333f, 0.5f, 0.666f, 1f),
                        CollageCell(0.666f, 0.5f, 1f, 1f)
                    )
                    1 -> listOf( // 1 Top Hero + 4 Bottom
                        CollageCell(0f, 0f, 1f, 0.6f),
                        CollageCell(0f, 0.6f, 0.25f, 1f),
                        CollageCell(0.25f, 0.6f, 0.5f, 1f),
                        CollageCell(0.5f, 0.6f, 0.75f, 1f),
                        CollageCell(0.75f, 0.6f, 1f, 1f)
                    )
                    else -> listOf( // 1 Left Hero + 4 Right Grid
                        CollageCell(0f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 0.75f, 0.5f),
                        CollageCell(0.75f, 0f, 1f, 0.5f),
                        CollageCell(0.5f, 0.5f, 0.75f, 1f),
                        CollageCell(0.75f, 0.5f, 1f, 1f)
                    )
                }
            }
            6 -> {
                when (layoutVariant % 2) {
                    0 -> listOf( // 2x3 Grid
                        CollageCell(0f, 0f, 0.333f, 0.5f),
                        CollageCell(0.333f, 0f, 0.666f, 0.5f),
                        CollageCell(0.666f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 0.333f, 1f),
                        CollageCell(0.333f, 0.5f, 0.666f, 1f),
                        CollageCell(0.666f, 0.5f, 1f, 1f)
                    )
                    else -> listOf( // 1 Big Left (spanning 2 rows) + 5 Tiles
                        CollageCell(0f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 0.75f, 0.333f),
                        CollageCell(0.75f, 0f, 1f, 0.333f),
                        CollageCell(0.5f, 0.333f, 1f, 0.666f),
                        CollageCell(0.5f, 0.666f, 0.75f, 1f),
                        CollageCell(0.75f, 0.666f, 1f, 1f)
                    )
                }
            }
            7 -> {
                // 3 Top + 4 Bottom
                listOf(
                    CollageCell(0f, 0f, 0.333f, 0.5f),
                    CollageCell(0.333f, 0f, 0.666f, 0.5f),
                    CollageCell(0.666f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 0.25f, 1f),
                    CollageCell(0.25f, 0.5f, 0.5f, 1f),
                    CollageCell(0.5f, 0.5f, 0.75f, 1f),
                    CollageCell(0.75f, 0.5f, 1f, 1f)
                )
            }
            8 -> {
                // 4 Top + 4 Bottom
                listOf(
                    CollageCell(0f, 0f, 0.25f, 0.5f),
                    CollageCell(0.25f, 0f, 0.5f, 0.5f),
                    CollageCell(0.5f, 0f, 0.75f, 0.5f),
                    CollageCell(0.75f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 0.25f, 1f),
                    CollageCell(0.25f, 0.5f, 0.5f, 1f),
                    CollageCell(0.5f, 0.5f, 0.75f, 1f),
                    CollageCell(0.75f, 0.5f, 1f, 1f)
                )
            }
            else -> {
                // 3x3 Master Grid (9 photos)
                listOf(
                    CollageCell(0f, 0f, 0.333f, 0.333f),
                    CollageCell(0.333f, 0f, 0.666f, 0.333f),
                    CollageCell(0.666f, 0f, 1f, 0.333f),
                    CollageCell(0f, 0.333f, 0.333f, 0.666f),
                    CollageCell(0.333f, 0.333f, 0.666f, 0.666f),
                    CollageCell(0.666f, 0.333f, 1f, 0.666f),
                    CollageCell(0f, 0.666f, 0.333f, 1f),
                    CollageCell(0.333f, 0.666f, 0.666f, 1f),
                    CollageCell(0.666f, 0.666f, 1f, 1f)
                )
            }
        }
    }

    fun createShapePath(destRect: RectF, shape: CollageShape, cornerRadiusPx: Float): Path {
        val path = Path()
        val cx = destRect.centerX()
        val cy = destRect.centerY()
        val width = destRect.width()
        val height = destRect.height()
        val radius = min(width, height) / 2f

        when (shape) {
            CollageShape.ROUNDED -> {
                path.addRoundRect(destRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
            CollageShape.CIRCLE -> {
                path.addOval(destRect, Path.Direction.CW)
            }
            CollageShape.DIAMOND -> {
                path.moveTo(cx, destRect.top)
                path.lineTo(destRect.right, cy)
                path.lineTo(cx, destRect.bottom)
                path.lineTo(destRect.left, cy)
                path.close()
            }
            CollageShape.HEXAGON -> {
                val w = width / 2f
                val h = height / 2f
                path.moveTo(cx, cy - h)
                path.lineTo(cx + w, cy - h / 2f)
                path.lineTo(cx + w, cy + h / 2f)
                path.lineTo(cx, cy + h)
                path.lineTo(cx - w, cy + h / 2f)
                path.lineTo(cx - w, cy - h / 2f)
                path.close()
            }
            CollageShape.HEART -> {
                val l = destRect.left
                val t = destRect.top
                val r = destRect.right
                val b = destRect.bottom
                val w = width
                val h = height

                path.moveTo(cx, t + h * 0.3f)
                path.cubicTo(
                    cx, t + h * 0.08f,
                    l + w * 0.05f, t,
                    l + w * 0.05f, t + h * 0.35f
                )
                path.cubicTo(
                    l + w * 0.05f, t + h * 0.6f,
                    cx - w * 0.2f, t + h * 0.8f,
                    cx, b - h * 0.02f
                )
                path.cubicTo(
                    cx + w * 0.2f, t + h * 0.8f,
                    r - w * 0.05f, t + h * 0.6f,
                    r - w * 0.05f, t + h * 0.35f
                )
                path.cubicTo(
                    r - w * 0.05f, t,
                    cx, t + h * 0.08f,
                    cx, t + h * 0.3f
                )
                path.close()
            }
            CollageShape.STAR -> {
                val outerRadius = radius
                val innerRadius = radius * 0.45f
                val step = (Math.PI / 5.0).toFloat()
                var angle = -Math.PI.toFloat() / 2f

                path.moveTo(
                    cx + outerRadius * cos(angle),
                    cy + outerRadius * sin(angle)
                )
                for (k in 1..5) {
                    angle += step
                    path.lineTo(
                        cx + innerRadius * cos(angle),
                        cy + innerRadius * sin(angle)
                    )
                    angle += step
                    path.lineTo(
                        cx + outerRadius * cos(angle),
                        cy + outerRadius * sin(angle)
                    )
                }
                path.close()
            }
        }
        return path
    }

    suspend fun generateCollageBitmap(
        context: Context,
        imageUris: List<Uri>,
        aspectRatio: CollageAspectRatio = CollageAspectRatio.SQUARE_1_1,
        shape: CollageShape = CollageShape.ROUNDED,
        layoutVariant: Int = 0,
        spacingPx: Float = 16f,
        cornerRadiusPx: Float = 24f,
        backgroundColor: Int = Color.BLACK,
        outputDimension: Int = 2048,
        transforms: List<CellTransform> = emptyList()
    ): Bitmap = withContext(Dispatchers.IO) {
        val totalWidth = outputDimension
        val totalHeight = (outputDimension * (aspectRatio.heightRatio / aspectRatio.widthRatio)).toInt()

        val outputBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(backgroundColor)

        val cells = computeCellLayouts(imageUris.size, layoutVariant)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val imageLoader = ImageLoader(context)

        for (i in imageUris.indices) {
            if (i >= cells.size) break
            val cell = cells[i]
            val uri = imageUris[i]
            val transform = transforms.getOrNull(i) ?: CellTransform()

            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(outputDimension)
                .build()

            val result = (imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
            if (result != null) {
                val cellLeft = (cell.leftFraction * totalWidth) + (spacingPx / 2f)
                val cellTop = (cell.topFraction * totalHeight) + (spacingPx / 2f)
                val cellRight = (cell.rightFraction * totalWidth) - (spacingPx / 2f)
                val cellBottom = (cell.bottomFraction * totalHeight) - (spacingPx / 2f)

                val destRect = RectF(cellLeft, cellTop, cellRight, cellBottom)

                val cellAspect = destRect.width() / destRect.height()
                val bitmapAspect = result.width.toFloat() / result.height.toFloat()

                val baseCropWidth = if (bitmapAspect > cellAspect) (result.height * cellAspect).toInt() else result.width
                val baseCropHeight = if (bitmapAspect > cellAspect) result.height else (result.width / cellAspect).toInt()

                val userScale = transform.scale.coerceIn(1f, 4f)
                val finalCropWidth = (baseCropWidth / userScale).toInt().coerceIn(1, result.width)
                val finalCropHeight = (baseCropHeight / userScale).toInt().coerceIn(1, result.height)

                val maxOffsetX = (result.width - finalCropWidth) / 2
                val maxOffsetY = (result.height - finalCropHeight) / 2

                val userOffsetX = (transform.panX * maxOffsetX).toInt().coerceIn(-maxOffsetX, maxOffsetX)
                val userOffsetY = (transform.panY * maxOffsetY).toInt().coerceIn(-maxOffsetY, maxOffsetY)

                val leftOffset = ((result.width - finalCropWidth) / 2) + userOffsetX
                val topOffset = ((result.height - finalCropHeight) / 2) + userOffsetY

                val srcRect = Rect(
                    leftOffset.coerceIn(0, result.width - finalCropWidth),
                    topOffset.coerceIn(0, result.height - finalCropHeight),
                    (leftOffset + finalCropWidth).coerceIn(finalCropWidth, result.width),
                    (topOffset + finalCropHeight).coerceIn(finalCropHeight, result.height)
                )

                val path = createShapePath(destRect, shape, cornerRadiusPx)

                canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(result, srcRect, destRect, paint)
                canvas.restore()
            }
        }

        outputBitmap
    }

    suspend fun saveCollage(
        context: Context,
        collageBitmap: Bitmap
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val filename = "Collage_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Collages")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null

                resolver.openOutputStream(targetUri)?.use { out ->
                    collageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)

                targetUri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val collagesDir = File(picturesDir, "Collages").apply { mkdirs() }
                val targetFile = File(collagesDir, filename)

                FileOutputStream(targetFile).use { out ->
                    collageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

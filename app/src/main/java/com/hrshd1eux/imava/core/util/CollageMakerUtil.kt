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

object CollageMakerUtil {

    enum class CollageAspectRatio(val widthRatio: Float, val heightRatio: Float) {
        SQUARE_1_1(1f, 1f),
        PORTRAIT_4_5(4f, 5f),
        STORY_9_16(9f, 16f),
        LANDSCAPE_16_9(16f, 9f)
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
                if (layoutVariant == 0) {
                    // Side-by-Side (Vertical split)
                    listOf(
                        CollageCell(0f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 1f, 1f)
                    )
                } else {
                    // Top-Bottom (Horizontal split)
                    listOf(
                        CollageCell(0f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 1f, 1f)
                    )
                }
            }
            3 -> {
                if (layoutVariant == 0) {
                    // 1 Top Hero + 2 Bottom
                    listOf(
                        CollageCell(0f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 0.5f, 1f),
                        CollageCell(0.5f, 0.5f, 1f, 1f)
                    )
                } else {
                    // 1 Left Hero + 2 Right
                    listOf(
                        CollageCell(0f, 0f, 0.5f, 1f),
                        CollageCell(0.5f, 0f, 1f, 0.5f),
                        CollageCell(0.5f, 0.5f, 1f, 1f)
                    )
                }
            }
            4 -> {
                if (layoutVariant == 0) {
                    // 2x2 Grid
                    listOf(
                        CollageCell(0f, 0f, 0.5f, 0.5f),
                        CollageCell(0.5f, 0f, 1f, 0.5f),
                        CollageCell(0f, 0.5f, 0.5f, 1f),
                        CollageCell(0.5f, 0.5f, 1f, 1f)
                    )
                } else {
                    // 1 Left Hero + 3 Right Stack
                    listOf(
                        CollageCell(0f, 0f, 0.6f, 1f),
                        CollageCell(0.6f, 0f, 1f, 0.333f),
                        CollageCell(0.6f, 0.333f, 1f, 0.666f),
                        CollageCell(0.6f, 0.666f, 1f, 1f)
                    )
                }
            }
            5 -> {
                // 2 Top + 3 Bottom
                listOf(
                    CollageCell(0f, 0f, 0.5f, 0.5f),
                    CollageCell(0.5f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 0.333f, 1f),
                    CollageCell(0.333f, 0.5f, 0.666f, 1f),
                    CollageCell(0.666f, 0.5f, 1f, 1f)
                )
            }
            6 -> {
                // 2x3 Grid
                listOf(
                    CollageCell(0f, 0f, 0.333f, 0.5f),
                    CollageCell(0.333f, 0f, 0.666f, 0.5f),
                    CollageCell(0.666f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 0.333f, 1f),
                    CollageCell(0.333f, 0.5f, 0.666f, 1f),
                    CollageCell(0.666f, 0.5f, 1f, 1f)
                )
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

    suspend fun generateCollageBitmap(
        context: Context,
        imageUris: List<Uri>,
        aspectRatio: CollageAspectRatio = CollageAspectRatio.SQUARE_1_1,
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

                val path = Path().apply {
                    addRoundRect(destRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
                }

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

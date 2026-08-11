package com.HrshD1eux.Gallery.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.HrshD1eux.Gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfConverter {

    private const val PAGE_WIDTH = 595 // Standard A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // Standard A4 height in points (72 dpi)
    private const val MARGIN = 36f // 0.5 inch margin

    /**
     * Converts a list of media items (photos) into a multi-page PDF document.
     * Returns the Uri of the created PDF file.
     */
    suspend fun createPdfFromImages(
        context: Context,
        items: List<MediaItem>,
        documentTitle: String = "Document_${System.currentTimeMillis()}"
    ): Uri? = withContext(Dispatchers.IO) {
        val photos = items.filterIsInstance<MediaItem.Photo>()
        if (photos.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bgPaint = Paint().apply { color = Color.WHITE }

        try {
            var pageNum = 1
            photos.forEach { mediaItem ->
                val bitmap = decodeSampledBitmap(context, mediaItem, PAGE_WIDTH * 2, PAGE_HEIGHT * 2)
                    ?: return@forEach

                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Fill background
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

                // Calculate aspect ratio fitting within margins
                val availableWidth = PAGE_WIDTH - (MARGIN * 2)
                val availableHeight = PAGE_HEIGHT - (MARGIN * 2)

                val bmpRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val pageRatio = availableWidth / availableHeight

                val destWidth: Float
                val destHeight: Float
                if (bmpRatio > pageRatio) {
                    destWidth = availableWidth
                    destHeight = availableWidth / bmpRatio
                } else {
                    destHeight = availableHeight
                    destWidth = availableHeight * bmpRatio
                }

                val left = MARGIN + (availableWidth - destWidth) / 2f
                val top = MARGIN + (availableHeight - destHeight) / 2f
                val destRect = RectF(left, top, left + destWidth, top + destHeight)

                canvas.drawBitmap(bitmap, null, destRect, paint)
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            if (pageNum == 1) {
                // No pages were successfully rendered
                return@withContext null
            }

            // Save PDF
            val fileName = "$documentTitle.pdf"
            val outputUri: Uri? = saveToCache(context, pdfDocument, fileName)

            // Also optionally save a copy to Documents folder for user access
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Gallery_PDFs")
                    }
                    val docUri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                    if (docUri != null) {
                        context.contentResolver.openOutputStream(docUri)?.use { stream ->
                            pdfDocument.writeTo(stream)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext outputUri
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }
    }

    private fun saveToCache(context: Context, document: PdfDocument, fileName: String): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "generated_pdfs").apply { mkdirs() }
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { stream ->
                document.writeTo(stream)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeSampledBitmap(context: Context, mediaItem: MediaItem.Photo, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val bytes = if (mediaItem.isHidden) {
                val byteOut = java.io.ByteArrayOutputStream()
                context.contentResolver.openInputStream(mediaItem.uri)?.use { input ->
                    VaultCrypto.decrypt(input, byteOut)
                }
                byteOut.toByteArray()
            } else {
                context.contentResolver.openInputStream(mediaItem.uri)?.use { input ->
                    input.readBytes()
                }
            } ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sharePdf(context: Context, pdfUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share PDF").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

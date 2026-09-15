package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageStorageHelper {

    private const val PRODUCT_IMAGES_DIR = "product_images"

    /**
     * Saves an image selected from the device gallery/camera into the app's private internal storage.
     * The image is resized to max 1024x1024 and compressed to JPEG to save space and load instantly.
     * Returns the absolute file path on the device, or null on failure.
     * NOTE: This is strictly local to the device and is never uploaded to any remote server.
     */
    fun saveProductImage(context: Context, sourceUri: Uri): String? {
        return try {
            val imagesDir = File(context.filesDir, PRODUCT_IMAGES_DIR).apply {
                if (!exists()) mkdirs()
            }

            val fileName = "prod_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val destFile = File(imagesDir, fileName)

            // Read bitmap with bounds first to calculate sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate inSampleSize for max dimension of 1024px
            val maxDim = 1024
            var sampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / sampleSize >= maxDim && halfWidth / sampleSize >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Safely deletes an existing local product image file.
     */
    fun deleteProductImage(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

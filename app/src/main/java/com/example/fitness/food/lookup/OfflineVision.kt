package com.example.fitness.food.lookup

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineVision(private val context: Context) {
    suspend fun normalize(uri: Uri): File = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Select a readable image." }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not read the image.")
        val orientation = context.contentResolver.openInputStream(uri).use {
            runCatching { ExifInterface(requireNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val folder = File(context.cacheDir, "selected_images").apply { mkdirs() }
        val output = File(folder, UUID.randomUUID().toString() + ".jpg")
        try { output.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
        finally { if (normalized !== bitmap) normalized.recycle(); bitmap.recycle() }
        require(output.length() <= 3_000_000) { "Select a smaller image." }
        output
    }
    suspend fun text(file: File): String = withContext(Dispatchers.IO) {
        modelLock.withLock {
            val root = File(context.filesDir, "ocr")
            val folder = File(root, "tessdata").apply { mkdirs() }
            val model = File(folder, "eng.traineddata")
            if (!model.isFile || model.length() == 0L) {
                val temp = File(folder, "eng.tmp")
                context.assets.open("ocr/eng.traineddata").use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(temp.renameTo(model))
            }
            val api = TessBaseAPI()
            try {
                check(api.init(root.absolutePath, "eng")) { "Could not load the bundled OCR model." }
                api.setImage(file)
                api.getUTF8Text().orEmpty()
            } finally { api.recycle() }
        }
    }
    suspend fun barcode(file: File): String = withContext(Dispatchers.Default) {
        var bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("Could not read the barcode image.")
        try {
            repeat(4) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF))) }
                val decoded = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                if (decoded != null) return@withContext decoded
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(90f) }, false)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            error("No product barcode found. Retake a clear close-up or type the code.")
        } finally { bitmap.recycle() }
    }
    companion object { private val modelLock = Mutex() }
}

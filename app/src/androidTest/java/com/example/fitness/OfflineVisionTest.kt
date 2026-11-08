package com.example.fitness

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.food.lookup.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineVisionTest {
    @Test fun bundledOcrAndBarcodeRecognitionWorkWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vision = OfflineVision(context)
        val label = File(context.cacheDir, "ocr-test.png")
        val barcode = File(context.cacheDir, "barcode-test.png")
        try {
            val bitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f; typeface = Typeface.DEFAULT_BOLD }
            listOf("Calories 200", "Protein 10 g", "Total fat 5 g", "Carbohydrate 30 g").forEachIndexed { i, line -> canvas.drawText(line, 40f, 100f + i * 110, paint) }
            label.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val text = vision.text(label)
            assertTrue("Bundled OCR must read the test label", text.contains("200"))
            assertEquals(200.0, parseLabel(text).food.nutrients.caloriesKcal, 0.0)
            val code = "5901234123457"
            val matrix = MultiFormatWriter().encode(code, BarcodeFormat.EAN_13, 800, 300)
            val image = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
            for (y in 0 until 300) for (x in 0 until 800) image.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            barcode.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            assertEquals(code, vision.barcode(barcode))
        } finally { label.delete(); barcode.delete() }
    }
}

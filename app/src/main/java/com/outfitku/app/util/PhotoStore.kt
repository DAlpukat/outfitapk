package com.outfitku.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** WebP ≤ 1080px sisi terpanjang, target 80–150 KB (PRD §13). */
object PhotoStore {

    fun save(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            bmp ?: return null

            val ratio = minOf(1f, 1080f / max(bmp.width, bmp.height))
            val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(
                bmp, (bmp.width * ratio).roundToInt(), (bmp.height * ratio).roundToInt(), true
            ) else bmp

            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.webp")
            var quality = 85
            while (true) {
                file.outputStream().use { scaled.compress(Bitmap.CompressFormat.WEBP, quality, it) }
                if (file.length() <= 150_000L || quality <= 40) break
                quality -= 15
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

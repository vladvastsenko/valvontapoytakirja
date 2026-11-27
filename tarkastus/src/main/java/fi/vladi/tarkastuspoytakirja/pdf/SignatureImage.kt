package fi.vladi.tarkastuspoytakirja.pdf

import android.graphics.Bitmap
import android.graphics.Color

/** Removes pale pink/white scanner backgrounds from imported signatures, without altering the source file. */
internal object SignatureImage {
    fun prepare(path: String): Bitmap? {
        val original = PdfBitmap.decode(path, 2400) ?: return null
        val width = original.width
        val height = original.height
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()
        if (bitmap == null) return null
        if (width <= 0 || height <= 0) return bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val nearWhite = r >= 238 && g >= 238 && b >= 238
            val palePink = r >= 185 && g >= 125 && b >= 125 && r - g >= 12 && r - b >= 8
            if (nearWhite || palePink) pixels[i] = Color.TRANSPARENT
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}

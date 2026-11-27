package fi.vladi.tarkastuspoytakirja.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Decode only the resolution needed for PDF rendering; never modify the source. */
internal object PdfBitmap {
    fun sampleSize(width: Int, height: Int, limit: Int): Int {
        require(limit > 0)
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while ((maxOf(width, height).toLong() + sample - 1) / sample > limit) {
            sample *= 2
        }
        return sample
    }

    fun decode(path: String, limit: Int = 2400): Bitmap? {
        if (path.isBlank() || !File(path).isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, limit)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }
}

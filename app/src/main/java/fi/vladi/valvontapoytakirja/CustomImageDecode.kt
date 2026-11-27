package fi.vladi.valvontapoytakirja

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

internal fun decodeCustomImage(file: File, limit: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path,bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth/sample > limit || bounds.outHeight/sample > limit) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
    val matrix = Matrix()
    when (orientation) {
        2 -> matrix.setScale(-1f,1f)
        3 -> matrix.setRotate(180f)
        4 -> matrix.setScale(1f,-1f)
        5 -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
        6 -> matrix.setRotate(90f)
        7 -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }
        8 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return try {
        Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also { if (it !== bitmap) bitmap.recycle() }
    } catch (e: Exception) { bitmap.recycle(); throw e }
}

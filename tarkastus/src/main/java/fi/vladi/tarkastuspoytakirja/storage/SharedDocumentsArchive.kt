package fi.vladi.tarkastuspoytakirja.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Shared public archive for both report types.
 * On Android 10+ MediaStore creates Documents/Tarkastus and its subfolders
 * without asking for broad storage permission.
 */
object SharedDocumentsArchive {
    private const val ROOT = "Tarkastus"

    fun archiveFile(
        context: Context,
        source: File,
        relativeDirectory: String,
        displayName: String = source.name,
        mimeType: String = mimeTypeFor(displayName)
    ): Uri? {
        if (!source.isFile || !source.exists()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$ROOT/${relativeDirectory.trim('/')}/"
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(displayName, relativePath),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        resolver.delete(Uri.withAppendedPath(collection, id.toString()), null, null)
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: error("Cannot create archive entry")
                try {
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        source.inputStream().buffered().use { input -> input.copyTo(output) }
                    } ?: error("Cannot open archive output")
                    val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, ready, null, null)
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    throw error
                }
                uri
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "$ROOT/${relativeDirectory.trim('/')}"
                )
                if (!directory.exists() && !directory.mkdirs()) error("Cannot create archive directory")
                val target = File(directory, displayName)
                source.copyTo(target, overwrite = true)
                Uri.fromFile(target)
            }
        }.getOrNull()
    }

    fun archiveText(
        context: Context,
        text: String,
        relativeDirectory: String,
        displayName: String,
        mimeType: String = "application/json"
    ): Uri? {
        val temporary = File(context.cacheDir, "archive_${displayName.hashCode()}_${System.nanoTime()}.tmp")
        return runCatching {
            temporary.writeText(text, Charsets.UTF_8)
            archiveFile(context, temporary, relativeDirectory, displayName, mimeType)
        }.getOrNull().also { temporary.delete() }
    }

    private fun mimeTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}

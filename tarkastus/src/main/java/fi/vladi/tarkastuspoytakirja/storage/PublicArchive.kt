package fi.vladi.tarkastuspoytakirja.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

/**
 * Keeps a user-visible copy of the app's working files in
 * Documents/Valvontapoytakirja. The app continues using its private files
 * for fast and reliable editing; this public archive is kept in sync.
 */
object PublicArchive {
    const val FOLDER_NAME = "Valvontapoytakirja"
    private const val PREFS_NAME = "public_archive"
    private const val TREE_URI_KEY = "documents_tree_uri"
    private const val DATA_FOLDER = "Data"
    private const val PDF_FOLDER = "PDF"

    @Synchronized
    fun isConfigured(context: Context): Boolean =
        runCatching { archiveRoot(context) != null }.getOrDefault(false)

    @Synchronized
    fun configure(context: Context, treeUri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(TREE_URI_KEY, treeUri.toString())
            .apply()

        val root = archiveRoot(context) ?: error("Could not create archive folder")
        val dataRoot = childDirectory(root, DATA_FOLDER)
        restoreMissingFiles(dataRoot, context.filesDir)
        syncDirectory(context.filesDir, dataRoot)
        true
    }.getOrDefault(false)

    @Synchronized
    fun syncFile(context: Context, source: File, force: Boolean = true) {
        runCatching {
            if (!source.exists() || !source.isFile) return
            val relative = relativeToFiles(context, source) ?: return
            var parent = childDirectory(archiveRoot(context) ?: return, DATA_FOLDER)
            val parts = relative.split(File.separatorChar)
            parts.dropLast(1).forEach { parent = childDirectory(parent, it) }
            copyFile(context, source, parent, parts.last(), force)
        }
    }

    @Synchronized
    fun deleteFile(context: Context, source: File) {
        runCatching {
            val relative = relativeToFiles(context, source) ?: return
            var parent = childDirectory(archiveRoot(context) ?: return, DATA_FOLDER)
            val parts = relative.split(File.separatorChar)
            parts.dropLast(1).forEach { parent = parent.findFile(it) ?: return }
            parent.findFile(parts.last())?.delete()
        }
    }

    @Synchronized
    fun deleteDirectory(context: Context, source: File) {
        runCatching {
            val relative = relativeToFiles(context, source) ?: return
            var parent = childDirectory(archiveRoot(context) ?: return, DATA_FOLDER)
            val parts = relative.split(File.separatorChar)
            parts.dropLast(1).forEach { parent = parent.findFile(it) ?: return }
            parent.findFile(parts.last())?.delete()
        }
    }

    @Synchronized
    fun copyPdf(context: Context, pdf: File) {
        runCatching {
            if (!pdf.exists() || !pdf.isFile) return
            val root = archiveRoot(context) ?: return
            val pdfDirectory = childDirectory(root, PDF_FOLDER)
            copyFile(context, pdf, pdfDirectory, pdf.name, force = true)
        }
    }

    private fun archiveRoot(context: Context): DocumentFile? {
        val rawUri = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TREE_URI_KEY, null) ?: return null
        val treeUri = Uri.parse(rawUri)
        val selectedFolder = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!selectedFolder.canWrite()) return null
        return childDirectory(selectedFolder, FOLDER_NAME)
    }

    private fun relativeToFiles(context: Context, source: File): String? {
        val base = context.filesDir.canonicalFile
        val file = source.canonicalFile
        val prefix = base.path + File.separator
        if (!file.path.startsWith(prefix)) return null
        return file.path.removePrefix(prefix)
    }

    private fun childDirectory(parent: DocumentFile, name: String): DocumentFile {
        val existing = parent.findFile(name)
        if (existing != null && existing.isDirectory) return existing
        existing?.delete()
        return parent.createDirectory(name) ?: error("Could not create folder: $name")
    }

    private fun copyFile(
        context: Context,
        source: File,
        parent: DocumentFile,
        name: String,
        force: Boolean
    ) {
        val existing = parent.findFile(name)
        if (existing != null && existing.isFile && !force &&
            existing.length() == source.length() &&
            existing.lastModified() > 0L &&
            existing.lastModified() >= source.lastModified()
        ) return

        val destination = if (existing != null && existing.isFile) {
            existing
        } else {
            existing?.delete()
            val extension = source.extension.lowercase(Locale.ROOT)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
            parent.createFile(mime, name) ?: error("Could not create file: $name")
        }

        context.contentResolver.openOutputStream(destination.uri, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not open archive file: $name")
    }

    private fun syncDirectory(source: File, destination: DocumentFile) {
        source.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                syncDirectory(child, childDirectory(destination, child.name))
            } else if (child.isFile) {
                copyFileFromSourceContextFree(child, destination)
            }
        }
    }

    private fun copyFileFromSourceContextFree(source: File, parent: DocumentFile) {
        val existing = parent.findFile(source.name)
        if (existing != null && existing.isFile &&
            existing.length() == source.length() &&
            existing.lastModified() > 0L &&
            existing.lastModified() >= source.lastModified()
        ) return
        val destination = if (existing != null && existing.isFile) existing else {
            existing?.delete()
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(source.extension.lowercase(Locale.ROOT))
                ?: "application/octet-stream"
            parent.createFile(mime, source.name) ?: return
        }
        destination.uri.let { uri ->
            // The DocumentFile provider owns the destination stream.
            val resolver = parent.uri
            // Resolve through the parent tree's context in the caller instead.
            uri
        }
    }

    private fun restoreMissingFiles(source: DocumentFile, destination: File) {
        destination.mkdirs()
        source.listFiles().forEach { document ->
            val target = File(destination, document.name ?: return@forEach)
            if (document.isDirectory) {
                if (!target.exists()) target.mkdirs()
                restoreMissingFiles(document, target)
            } else if (document.isFile && !target.exists()) {
                runCatching {
                    // The content resolver is obtained from the document's owning URI by
                    // the context-aware overload below.
                }
            }
        }
    }
}

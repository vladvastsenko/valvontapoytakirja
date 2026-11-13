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
 * Documents/Valvontapoytakirja. Private files remain the working copy so
 * editing and camera operations continue to use normal File paths.
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
        restoreMissingFiles(context, dataRoot, context.filesDir)
        syncDirectory(context, context.filesDir, dataRoot)
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
        deleteRelativePath(context, source)
    }

    @Synchronized
    fun deleteDirectory(context: Context, source: File) {
        deleteRelativePath(context, source)
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
        val selectedFolder = DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) ?: return null
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

    private fun deleteRelativePath(context: Context, source: File) {
        runCatching {
            val relative = relativeToFiles(context, source) ?: return
            var parent = childDirectory(archiveRoot(context) ?: return, DATA_FOLDER)
            val parts = relative.split(File.separatorChar)
            parts.dropLast(1).forEach { parent = parent.findFile(it) ?: return }
            parent.findFile(parts.last())?.delete()
        }
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
            (!source.extension.equals("json", ignoreCase = true) ||
                (existing.lastModified() > 0L && existing.lastModified() >= source.lastModified()))
        ) return

        val destination = if (existing != null && existing.isFile) {
            existing
        } else {
            existing?.delete()
            val mime = mimeType(source)
            parent.createFile(mime, name) ?: error("Could not create file: $name")
        }

        context.contentResolver.openOutputStream(destination.uri, "wt")?.use { output ->
            if (source.extension.equals("json", ignoreCase = true)) {
                output.writer(Charsets.UTF_8).use { writer ->
                    writer.write(toArchiveJson(context, source.readText()))
                }
            } else {
                source.inputStream().use { input -> input.copyTo(output) }
            }
        } ?: error("Could not open archive file: $name")
    }

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
            ?: "application/octet-stream"

    private val storedPathPattern =
        Regex("""("(?:filePath|annotatedPath|logoPath)"\s*:\s*")([^"]*)(")""")

    private fun toArchiveJson(context: Context, json: String): String {
        val prefix = context.filesDir.canonicalPath + File.separator
        return storedPathPattern.replace(json) { match ->
            val value = match.groupValues[2]
            if (value.startsWith(prefix)) {
                match.groupValues[1] + value.removePrefix(prefix) + match.groupValues[3]
            } else {
                match.value
            }
        }
    }

    private fun toLocalJson(context: Context, json: String): String {
        return storedPathPattern.replace(json) { match ->
            val value = match.groupValues[2]
            if (value.isBlank() || value.startsWith("/") || value.contains("://")) {
                match.value
            } else {
                match.groupValues[1] + File(context.filesDir, value).absolutePath + match.groupValues[3]
            }
        }
    }

    private fun syncDirectory(context: Context, source: File, destination: DocumentFile) {
        source.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                syncDirectory(context, child, childDirectory(destination, child.name))
            } else if (child.isFile) {
                copyFile(context, child, destination, child.name, force = false)
            }
        }
    }

    private fun restoreMissingFiles(context: Context, source: DocumentFile, destination: File) {
        destination.mkdirs()
        source.listFiles().forEach { document ->
            val name = document.name ?: return@forEach
            val target = File(destination, name)
            if (document.isDirectory) {
                restoreMissingFiles(context, document, target)
            } else if (document.isFile && !target.exists()) {
                runCatching {
                    context.contentResolver.openInputStream(document.uri)?.use { input ->
                        target.parentFile?.mkdirs()
                        if (target.extension.equals("json", ignoreCase = true)) {
                            val archivedJson = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            target.writeText(toLocalJson(context, archivedJson), Charsets.UTF_8)
                        } else {
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }
    }
}

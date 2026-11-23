package fi.vladi.valvontapoytakirja.cloud

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local-first backup of the complete app-private files directory.
 * The archive is portable between installs of the same Tarkastus application id.
 */
object CloudBackupManager {
    data class BackupFile(val uri: Uri, val name: String, val sizeBytes: Long, val lastModified: Long)

    fun listBackups(context: Context, treeUri: Uri): List<BackupFile> {
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Valittua Google Drive -kansiota ei voi avata.")
        check(folder.isDirectory && folder.canRead()) { "Valitun Google Drive -kansion sisältöä ei voi lukea." }
        return folder.listFiles()
            .filter { it.isFile && it.name?.startsWith("Tarkastus_backup_") == true && it.name?.endsWith(".zip") == true }
            .map { BackupFile(it.uri, it.name.orEmpty(), it.length(), it.lastModified()) }
            .sortedByDescending { backupTimestamp(it.name) ?: it.lastModified }
    }

    private fun backupTimestamp(name: String): Long? {
        val stamp = name.removePrefix("Tarkastus_backup_").removeSuffix(".zip")
        return listOf("yyyyMMdd_HHmmss_SSS", "yyyyMMdd_HHmmss").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(stamp)?.time }.getOrNull()
        }
    }
    private const val MANIFEST = "TARKASTUS_BACKUP_INFO.txt"
    private const val MANIFEST_CONTENT = "TARKASTUS_BACKUP_V1"
    private const val MAX_ENTRIES = 20_000
    private const val MAX_UNPACKED_BYTES = 2L * 1024L * 1024L * 1024L

    fun backupToFolder(context: Context, treeUri: Uri): String {
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Valittua Google Drive -kansiota ei voi avata.")
        check(folder.isDirectory && folder.canWrite()) {
            "Valittuun kansioon ei voi tallentaa."
        }

        val tempZip = File(context.cacheDir, "tarkastus_backup_${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write((MANIFEST_CONTENT + "\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val root = context.filesDir
                root.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val relative = file.relativeTo(root).invariantSeparatorsPath
                        require(relative.isNotBlank() && !relative.startsWith("../")) {
                            "Virheellinen tiedostopolku varmuuskopiossa."
                        }
                        zip.putNextEntry(ZipEntry("files/$relative"))
                        FileInputStream(file).buffered().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
            }

            val displayName = "Tarkastus_backup_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.zip"
            folder.findFile(displayName)?.delete()
            val target = folder.createFile("application/zip", displayName)
                ?: error("Varmuuskopiotiedostoa ei voitu luoda Google Driveen.")
            try {
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    tempZip.inputStream().buffered().use { input -> input.copyTo(output) }
                } ?: error("Google Drive -tiedostoa ei voitu kirjoittaa.")
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
            val pdfCount = copyPdfExportsToDrive(context, folder)
            return "$displayName; PDF-tiedostoja: $pdfCount"
        } finally {
            tempZip.delete()
        }
    }

    private fun copyPdfExportsToDrive(context: Context, destinationRoot: DocumentFile): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val basePath = "${Environment.DIRECTORY_DOCUMENTS}/Tarkastus/"
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf("${basePath}Raportit/PDF/%", "application/pdf")
        var copied = 0
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            selection,
            args,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameColumn) ?: continue
                val relativePath = cursor.getString(pathColumn) ?: continue
                val subPath = relativePath.removePrefix(basePath).trim('/')
                if (subPath.isBlank() || subPath.split('/').any { it == ".." || it.isBlank() }) continue
                var folder = destinationRoot
                subPath.split('/').forEach { segment ->
                    folder = folder.findFile(segment) ?: folder.createDirectory(segment) ?: error("Drive-kansiota ei voitu luoda.")
                }
                val existing = folder.findFile(displayName)
                existing?.delete()
                val target = folder.createFile("application/pdf", displayName)
                    ?: error("PDF-tiedostoa ei voitu luoda Google Driveen.")
                val sourceUri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                            input.copyTo(output)
                        } ?: error("PDF-tiedostoa ei voitu kirjoittaa Driveen.")
                    } ?: error("PDF-tiedostoa ei voitu lukea.")
                    copied++
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
            }
        }
        return copied
    }

    fun restoreFromUri(context: Context, archiveUri: Uri): String {
        val staging = File(context.cacheDir, "tarkastus_restore_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        var manifestFound = false
        var entryCount = 0
        var totalBytes = 0L

        try {
            val input = context.contentResolver.openInputStream(archiveUri)
                ?: error("Varmuuskopioita ei voitu avata.")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "Varmuuskopiossa on liian monta tiedostoa." }

                    val name = entry.name.replace('\\', '/')
                    require(!name.startsWith("/") && name.split('/').none { it == ".." || it.isBlank() }) {
                        "Varmuuskopiossa on virheellinen tiedostopolku."
                    }

                    if (name == MANIFEST) {
                        val manifestBytes = ByteArray(128)
                        val count = zip.read(manifestBytes)
                        require(count in 1..127 && zip.read() == -1) { "Varmuuskopion tunnistetiedot ovat virheelliset." }
                        val text = String(manifestBytes, 0, count, Charsets.UTF_8).trim()
                        require(text == MANIFEST_CONTENT) { "Tiedosto ei ole Tarkastus-varmuuskopio." }
                        manifestFound = true
                    } else if (name.startsWith("files/") && name.length > 6) {
                        val relative = name.removePrefix("files/")
                        val destination = File(staging, relative)
                        val canonicalRoot = staging.canonicalFile
                        val canonicalDestination = destination.canonicalFile
                        require(canonicalDestination.path.startsWith(canonicalRoot.path + File.separator)) {
                            "Varmuuskopiossa on kielletty tiedostopolku."
                        }
                        if (entry.isDirectory) {
                            destination.mkdirs()
                        } else {
                            destination.parentFile?.mkdirs()
                            FileOutputStream(destination).buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    totalBytes += count
                                    require(totalBytes <= MAX_UNPACKED_BYTES) {
                                        "Varmuuskopio on liian suuri palautettavaksi."
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    } else {
                        require(name == MANIFEST) { "Varmuuskopio sisältää tuntemattoman tiedoston." }
                    }
                    zip.closeEntry()
                }
            }
            check(manifestFound) { "Varmuuskopion tunnistetietoja ei löytynyt." }

            // Keep the current local data until the complete archive has been extracted and validated.
            val current = context.filesDir
            val previous = File(context.cacheDir, "tarkastus_restore_previous").apply { deleteRecursively() }
            check(current.renameTo(previous)) { "Nykyisiä tietoja ei voitu suojata palautuksen ajaksi." }
            if (!staging.renameTo(current)) {
                previous.renameTo(current)
                error("Varmuuskopion käyttöönotto epäonnistui. Nykyiset tiedot palautettiin.")
            }
            previous.deleteRecursively()
            return "Varmuuskopio palautettiin onnistuneesti."
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }
}

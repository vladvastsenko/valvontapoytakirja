package fi.vladi.valvontapoytakirja.cloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local-first backup of the complete app-private files directory.
 * The archive is portable between installs of the same Tarkastus application id.
 */
object CloudBackupManager {
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

            val displayName = "Tarkastus_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.zip"
            folder.findFile(displayName)?.delete()
            val target = folder.createFile("application/zip", displayName)
                ?: error("Varmuuskopiotiedostoa ei voitu luoda Google Driveen.")
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                tempZip.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Google Drive -tiedostoa ei voitu kirjoittaa.")
            return displayName
        } finally {
            tempZip.delete()
        }
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

                    val name = entry.name.replace('\\\\', '/')
                    require(!name.startsWith("/") && name.split('/').none { it == ".." || it.isBlank() }) {
                        "Varmuuskopiossa on virheellinen tiedostopolku."
                    }

                    if (name == MANIFEST) {
                        val text = zip.readBytes().toString(Charsets.UTF_8).trim()
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

package fi.vladi.valvontapoytakirja.cloud

import fi.vladi.tarkastuspoytakirja.storage.AtomicRecords
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
            .filter { it.isFile && it.name?.startsWith("TarkastusPro_building_backup_") == true && it.name?.endsWith(".zip") == true }
            .map { BackupFile(it.uri, it.name.orEmpty(), it.length(), it.lastModified()) }
            .sortedByDescending { backupTimestamp(it.name) ?: it.lastModified }
    }

    private fun backupTimestamp(name: String): Long? {
        val stamp = name.removePrefix("TarkastusPro_building_backup_").removeSuffix(".zip")
        return listOf("yyyyMMdd_HHmmss_SSS", "yyyyMMdd_HHmmss").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(stamp)?.time }.getOrNull()
        }
    }
    private const val MANIFEST = "TARKASTUSPRO_BUILDING_BACKUP_INFO.txt"
    private const val MANIFEST_CONTENT = "TARKASTUSPRO_BUILDING_BACKUP_V1"
    private const val CHECKSUMS = "SHA256.json"
    private const val V2 = "TARKASTUSPRO_BUILDING_BACKUP_V2"
    private fun digest(file: File): String {
        val d = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(8192); while (true) { val n = input.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
    private const val MAX_ENTRIES = 20_000
    private const val MAX_UNPACKED_BYTES = 2L * 1024L * 1024L * 1024L

    private fun writeArchive(root: File, target: File) = AtomicRecords.exclusive {
        // Recover legacy AtomicFile backups before taking the snapshot.
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".json.bak") }.toList().forEach {
            AtomicRecords.read(File(it.parentFile, it.name.removeSuffix(".bak")))
        }
        val hashes = org.json.JSONObject()
        var count = 2
        var total = 0L
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write((V2 + "\n").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            root.walkTopDown().filter { it.isFile && !it.name.endsWith(".json.new") && !it.name.endsWith(".json.bak") }.forEach { file ->
                require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
                val relative = file.relativeTo(root).invariantSeparatorsPath
                require(++count <= MAX_ENTRIES)
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry("files/$relative"))
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_UNPACKED_BYTES)
                        hash.update(buffer, 0, n)
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
                hashes.put(relative, hash.digest().joinToString("") { "%02x".format(it) })
            }
            zip.putNextEntry(ZipEntry(CHECKSUMS))
            zip.write(hashes.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun backupToDocument(context: Context, uri: Uri, emergency: Boolean = false) {
        val temp = File.createTempFile("pro-backup-", ".zip", context.cacheDir)
        try {
            val root = if (emergency) recoveryCopies(context).firstOrNull() ?: error("No recovery copy") else context.filesDir
            writeArchive(root, temp)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                temp.inputStream().use { it.copyTo(output) }
            } ?: error("Unable to open backup destination")
        } finally { temp.delete() }
    }

    fun backupToFolder(context: Context, treeUri: Uri): String {
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Valittua Google Drive -kansiota ei voi avata.")
        check(folder.isDirectory && folder.canWrite()) {
            "Valittuun kansioon ei voi tallentaa."
        }

        val tempZip = File(context.cacheDir, "tarkastuspro_building_backup_${System.currentTimeMillis()}.zip")
        try {
            writeArchive(context.filesDir, tempZip)

            val displayName = "TarkastusPro_building_backup_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.zip"
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
        val basePath = "${Environment.DIRECTORY_DOCUMENTS}/TarkastusPro/"
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
                if (subPath.isBlank() || subPath.split('/').any { it == ".." || it == "." || it.isBlank() }) continue
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

    private fun rebaseAttachments(staging: File, current: File) {
        val keys = setOf("filePath", "annotatedPath", "logoPath", "signatureImagePath")
        val roots = setOf("reports", "tarkastus_reports", "settings", "inspectors")
        fun visit(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (element) {
            is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(element.mapValues { (key, value) ->
                if (key in keys && value is kotlinx.serialization.json.JsonPrimitive && value.isString && value.content.isNotBlank()) {
                    val original = value.content
                    val relative = original.substringAfterLast("/files/", original)
                    val parts = relative.split("/")
                    val candidate = File(staging, relative)
                    if (parts.firstOrNull() in roots && parts.none { it == ".." || it == "." || it.isBlank() } && candidate.canonicalPath.startsWith(staging.canonicalPath + File.separator) && candidate.isFile)
                        kotlinx.serialization.json.JsonPrimitive(File(current, relative).absolutePath)
                    else value
                } else visit(value)
            })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(element.map { visit(it) })
            else -> element
        }
        val recordRoots = setOf("reports", "tarkastus_reports", "templates", "tarkastus_templates", "inspectors")
        staging.walkTopDown().filter { it.isFile && it.extension == "json" && (it.name == "app_settings.json" || it.relativeTo(staging).invariantSeparatorsPath.substringBefore("/") in recordRoots) }.forEach { file ->
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(file.readText())
            AtomicRecords.write(file, visit(parsed).toString())
        }
    }

    fun recoveryCopies(context: Context): List<File> = File(context.applicationInfo.dataDir).listFiles().orEmpty().filter { it.isDirectory && (it.name.startsWith("tarkastus_restore_retained_") || it.name == "tarkastus_restore_previous") }.sortedByDescending { it.lastModified() }

    fun recoverInterrupted(context: Context) = AtomicRecords.exclusive {
        val parent = File(context.applicationInfo.dataDir)
        val current = File(parent, "files")
        val previous = File(parent, "tarkastus_restore_previous")
        if (previous.exists()) {
            if (!current.exists() || current.listFiles()?.isEmpty() == true) {
                if (current.exists()) check(current.delete())
                check(previous.renameTo(current))
            } else {
                check(previous.renameTo(File(parent, "tarkastus_restore_retained_" + java.util.UUID.randomUUID())))
            }
        }
        File(parent, "tarkastus_restore_staging").deleteRecursively()
    }

    fun restoreFromUri(context: Context, archiveUri: Uri): String = AtomicRecords.exclusive { restoreLocked(context, archiveUri) }

    private fun restoreLocked(context: Context, archiveUri: Uri): String {
        // Recover the original directory before preparing a new restore.
        val original = context.filesDir
        val previousRecovery = File(original.parentFile, "tarkastus_restore_previous")
        if (previousRecovery.exists()) {
            if (!original.exists()) {
                check(previousRecovery.renameTo(original)) {
                    "Keskeytyneen palautuksen alkuperäisiä tietoja ei voitu palauttaa."
                }
            } else {
                error("Aiemman palautuksen turvakopio on olemassa. Tarkista tiedot ennen uutta palautusta.")
            }
        }
        // Stage beside filesDir so the final rename stays on the same filesystem.
        val staging = File(context.filesDir.parentFile, "tarkastus_restore_staging").apply {
            deleteRecursively()
            check(mkdirs()) { "Palautuksen väliaikaista kansiota ei voitu luoda." }
        }
        var manifestFound = false
        var v2 = false
        var hashes: org.json.JSONObject? = null
        val seenEntries = hashSetOf<String>()
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
                    require(seenEntries.add(name)) { "Varmuuskopiossa on päällekkäinen tiedostopolku." }
                    require(!name.startsWith("/") && name.split('/').none { it == ".." || it.isBlank() }) {
                        "Varmuuskopiossa on virheellinen tiedostopolku."
                    }

                    if (name == MANIFEST) {
                        val manifestBytes = ByteArray(128)
                        var count = 0
                        while (count < manifestBytes.size) {
                            val n = zip.read(manifestBytes, count, manifestBytes.size - count)
                            if (n < 0) break
                            count += n
                        }
                        require(count in 1..127 && zip.read() == -1) { "Varmuuskopion tunnistetiedot ovat virheelliset." }
                        val text = String(manifestBytes, 0, count, Charsets.UTF_8).trim()
                        require(text == MANIFEST_CONTENT || text == V2) { "Tiedosto ei ole Tarkastus-varmuuskopio." }
                        manifestFound = true
                        v2 = text == V2
                    } else if (name == CHECKSUMS) {
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) { val n = zip.read(buffer); if (n < 0) break; require(bytes.size() + n <= 16 * 1024 * 1024); bytes.write(buffer, 0, n) }
                        hashes = org.json.JSONObject(bytes.toString("UTF-8"))
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
            if (v2 || hashes != null) {
                val expected = requireNotNull(hashes)
                val actual = staging.walkTopDown().filter { it.isFile }.toList()
                require(actual.size == expected.length()) { "Backup file count mismatch" }
                actual.forEach { require(expected.optString(it.relativeTo(staging).invariantSeparatorsPath) == digest(it)) { "Backup checksum mismatch" } }
            }
            check(staging.walkTopDown().any { it.isFile }) { "Varmuuskopio ei sisällä palautettavia tiedostoja." }

            rebaseAttachments(staging, context.filesDir)
            // Keep the current local data until the complete archive has been extracted and validated.
            val current = context.filesDir
            val previous = File(current.parentFile, "tarkastus_restore_previous")
            // Never discard a previous recovery copy after an interrupted restore.
            if (previous.exists()) {
                if (!current.exists()) {
                    check(previous.renameTo(current)) { "Keskeytyneen palautuksen tietoja ei voitu palauttaa." }
                } else {
                    error("Aiemman palautuksen turvakopio on olemassa. Tarkista tiedot ennen uutta palautusta.")
                }
            }
            check(current.renameTo(previous)) { "Nykyisiä tietoja ei voitu suojata palautuksen ajaksi." }
            if (!staging.renameTo(current)) {
                check(previous.renameTo(current)) { "Palautus epäonnistui. Alkuperäiset tiedot ovat turvakansiossa." }
                error("Varmuuskopion käyttöönotto epäonnistui. Nykyiset tiedot palautettiin.")
            }
            // Keep the pre-restore snapshot for manual recovery; never overwrite it.
            val retained = File(current.parentFile, "tarkastus_restore_retained_" + java.util.UUID.randomUUID())
            check(previous.renameTo(retained)) { "Restored data is active; recovery copy remains in previous directory." }
            return "Varmuuskopio palautettiin onnistuneesti."
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }
}

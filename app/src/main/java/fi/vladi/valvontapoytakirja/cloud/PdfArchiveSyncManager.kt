package fi.vladi.valvontapoytakirja.cloud

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Two-way synchronization for PDFs exported to Documents/TarkastusPro/Raportit/PDF.
 * The Drive copy is stored in the selected Drive folder's top-level PDF directory.
 */
internal object PdfArchiveSyncManager {
    private const val PREFS = "tarkastus_pro_building_sync"
    private const val BASELINE_KEY = "pdf_baseline_v1"
    private const val DOCUMENTS_ROOT = "Documents/TarkastusPro/"
    private const val PDF_DIRECTORY = "Raportit/PDF"
    private const val REMOTE_FOLDER = "TarkastusPro_Building_PDF"

    data class Result(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val deleted: Int = 0,
        val conflicts: Int = 0,
        val foundLocal: Int = 0,
        val foundDrive: Int = 0
    )

    private data class LocalPdf(val uri: Uri)

    fun sync(context: Context, selectedRoot: DocumentFile, syncRoot: DocumentFile, conflictRoot: DocumentFile): Result {
        check(fi.vladi.valvontapoytakirja.billing.PremiumAccess.allowed()) { "Premium required" }
        // MediaStore RELATIVE_PATH is available from Android 10 (API 29).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Result()

        val remoteRoot = selectedRoot.findFile(REMOTE_FOLDER)
            ?: selectedRoot.createDirectory(REMOTE_FOLDER)
            ?: error("PDF-synkronointikansiota ei voitu luoda Google Driveen.")

        // One-time migration from the previous nested location TarkastusPro_Building_Sync/PDF.
        // Existing top-level PDFs remain authoritative if a same-path name already exists.
        migrateLegacyFolder(context, syncRoot.findFile(REMOTE_FOLDER), remoteRoot, conflictRoot)

        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldBaseline = runCatching {
            JSONObject(preferences.getString(BASELINE_KEY, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val nextBaseline = JSONObject()

        val localFiles = collectLocalPdfs(context)
        val remoteFiles = linkedMapOf<String, DocumentFile>()
        collectRemoteFiles(remoteRoot, "$PDF_DIRECTORY", remoteFiles)

        val baselinePaths: Set<String> = oldBaseline.keys().asSequence().toSet()
        val allPaths: Set<String> = buildSet {
            addAll(localFiles.keys)
            addAll(remoteFiles.keys)
            addAll(baselinePaths)
        }.toSortedSet()

        var uploaded = 0
        var downloaded = 0
        var deleted = 0
        var conflicts = 0

        for (path in allPaths) {
            validatePath(path)
            var local = localFiles[path]
            var remote = remoteFiles[path]?.takeIf { it.exists() }
            var localHash = local?.let { sha256(context, it.uri) }
            var remoteHash = remote?.let { sha256(context, it) }
            val baseHash = oldBaseline.optString(path).takeIf { it.isNotBlank() }

            if (localHash == null && remoteHash == null) {
                // Both sides are already deleted; do not report a false conflict.
                continue
            }
            if (localHash != null && localHash == remoteHash) {
                nextBaseline.put(path, localHash)
                continue
            }

            if (baseHash == null) {
                when {
                    local != null && remote == null -> {
                        upload(context, remoteRoot, driveRelativePath(path), local.uri)
                        uploaded++
                    }
                    local == null && remote != null -> {
                        download(context, remote, path)
                        downloaded++
                    }
                    local != null && remote != null -> {
                        saveConflict(context, conflictRoot, path, local.uri)
                        download(context, remote, path)
                        conflicts++
                        downloaded++
                    }
                }
            } else if (localHash == baseHash) {
                // Local PDF has not changed: accept the remote update or deletion.
                if (remote == null) {
                    // Never erase a local PDF just because it disappeared from Drive.
                    if (local != null) {
                        upload(context, remoteRoot, driveRelativePath(path), local.uri)
                        uploaded++
                    }
                } else {
                    download(context, remote, path)
                    downloaded++
                }
            } else if (remoteHash == baseHash) {
                // Drive copy has not changed: publish the local update or deletion.
                if (local == null) {
                    // Restore a missing local PDF instead of deleting the shared copy.
                    if (remote != null) {
                        download(context, remote, path)
                        downloaded++
                    }
                } else {
                    upload(context, remoteRoot, driveRelativePath(path), local.uri)
                    uploaded++
                }
            } else {
                // Both sides changed. Preserve the local version before accepting the remote one.
                conflicts++
                if (local != null) saveConflict(context, conflictRoot, path, local.uri)
                if (remote != null) {
                    download(context, remote, path)
                    downloaded++
                } else if (local != null) {
                    upload(context, remoteRoot, driveRelativePath(path), local.uri)
                    uploaded++
                }
            }

            local = collectLocalPdfs(context)[path]
            remote = findRemoteFile(remoteRoot, driveRelativePath(path))
            localHash = local?.let { sha256(context, it.uri) }
            remoteHash = remote?.let { sha256(context, it) }
            if (localHash != null && localHash == remoteHash) nextBaseline.put(path, localHash)
        }

        check(preferences.edit().putString(BASELINE_KEY, nextBaseline.toString()).commit()) {
            "PDF-synkronoinnin tilaa ei voitu tallentaa laitteelle."
        }
        return Result(uploaded, downloaded, deleted, conflicts, localFiles.size, remoteFiles.size)
    }


    private fun migrateLegacyFolder(
        context: Context,
        legacyRoot: DocumentFile?,
        targetRoot: DocumentFile,
        conflictRoot: DocumentFile
    ) {
        if (legacyRoot == null || !legacyRoot.exists() || !legacyRoot.isDirectory) return

        fun copyTree(sourceDir: DocumentFile, targetDir: DocumentFile, prefix: String) {
            sourceDir.listFiles().forEach { source ->
                val name = source.name ?: return@forEach
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                if (source.isDirectory) {
                    val childTarget = targetDir.findFile(name)?.takeIf { it.isDirectory }
                        ?: targetDir.createDirectory(name)
                        ?: error("PDF-kansion siirto epäonnistui: $relative")
                    copyTree(source, childTarget, relative)
                } else if (source.isFile && name.lowercase(Locale.ROOT).endsWith(".pdf")) {
                    val existing = targetDir.findFile(name)?.takeIf { it.isFile && it.exists() }
                    if (existing == null) {
                        val destination = targetDir.createFile("application/pdf", name)
                            ?: error("PDF-tiedoston siirto epäonnistui: $relative")
                        try {
                            context.contentResolver.openInputStream(source.uri)?.use { input ->
                                context.contentResolver.openOutputStream(destination.uri, "w")?.use { output ->
                                    input.copyTo(output)
                                } ?: error("PDF-tiedoston kirjoittaminen epäonnistui: $relative")
                            } ?: error("Vanhan PDF-tiedoston lukeminen epäonnistui: $relative")
                        } catch (error: Throwable) {
                            destination.delete()
                            throw error
                        }
                    } else if (sha256(context, existing) != sha256(context, source)) {
                        saveConflict(context, conflictRoot, "vanha_sijainti_$relative", source.uri)
                    }
                }
            }
        }

        val legacyPayload = legacyRoot.findFile("Raportit")?.findFile("PDF") ?: legacyRoot
        copyTree(legacyPayload, targetRoot, "")
        // Preserve the original folder: migration must never destroy source PDFs.
    }

    private fun deleteTree(directory: DocumentFile) {
        directory.listFiles().forEach { child ->
            if (child.isDirectory) deleteTree(child)
            if (child.exists() && !child.delete()) {
                error("Vanhaa PDF-kansiota ei voitu siivota Google Drivesta.")
            }
        }
        if (directory.exists() && !directory.delete()) {
            error("Vanhaa PDF-kansiota ei voitu poistaa Google Drivesta.")
        }
    }

    private fun driveRelativePath(localRelativePath: String): String =
        localRelativePath.removePrefix("$PDF_DIRECTORY/")

    private fun collectLocalPdfs(context: Context): Map<String, LocalPdf> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        // Do not filter by MIME_TYPE: some Android/OEM MediaStore providers leave it
        // empty or classify imported PDFs inconsistently. The .pdf extension is checked below.
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${DOCUMENTS_ROOT}${PDF_DIRECTORY}/%")
        val result = linkedMapOf<String, LocalPdf>()

        val cursor = context.contentResolver.query(collection, projection, selection, args, null)
            ?: error("PDF-arkistoa ei voitu lukea laitteen tallennustilasta.")
        cursor.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val relativeDirectory = cursor.getString(pathColumn) ?: continue
                if (!name.lowercase(Locale.ROOT).endsWith(".pdf")) continue
                val subDirectory = relativeDirectory.removePrefix(DOCUMENTS_ROOT).trim('/')
                if (!subDirectory.startsWith(PDF_DIRECTORY) ||
                    subDirectory.split('/').any { it.isBlank() || it == "." || it == ".." }
                ) continue
                val relativePath = "$subDirectory/$name"
                validatePath(relativePath)
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                result[relativePath] = LocalPdf(uri)
            }
        }
        return result
    }

    private fun collectRemoteFiles(
        directory: DocumentFile,
        prefix: String,
        result: MutableMap<String, DocumentFile>
    ) {
        directory.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) collectRemoteFiles(child, path, result)
            else if (child.isFile && name.lowercase(Locale.ROOT).endsWith(".pdf")) result[path] = child
        }
    }

    private fun findRemoteFile(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.split('/')
        var current = root
        for (part in parts.dropLast(1)) {
            current = current.findFile(part)?.takeIf { it.isDirectory } ?: return null
        }
        return current.findFile(parts.last())?.takeIf { it.isFile && it.exists() }
    }

    private fun ensureRemoteParent(root: DocumentFile, path: String): DocumentFile {
        val parts = path.split('/')
        var current = root
        for (part in parts.dropLast(1)) {
            current = current.findFile(part)?.takeIf { it.isDirectory }
                ?: current.createDirectory(part)
                ?: error("PDF-kansiota ei voitu luoda Google Driveen: $part")
        }
        return current
    }

    private fun upload(context: Context, root: DocumentFile, path: String, source: Uri) {
        val parent = ensureRemoteParent(root, path)
        val name = path.substringAfterLast('/')
        parent.findFile(name)?.let { existing ->
            if (!existing.delete()) error("Vanhaa PDF-tiedostoa ei voitu korvata: $path")
        }
        val target = parent.createFile("application/pdf", name)
            ?: error("PDF-tiedostoa ei voitu luoda Google Driveen: $path")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    input.copyTo(output)
                } ?: error("PDF-tiedostoa ei voitu kirjoittaa Google Driveen: $path")
            } ?: error("PDF-tiedostoa ei voitu lukea puhelimesta: $path")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun download(context: Context, source: DocumentFile, path: String) {
        val temporary = File(context.cacheDir, "pdf_sync_${System.currentTimeMillis()}_${path.hashCode()}.pdf")
        try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("PDF-tiedostoa ei voitu lukea Google Drivesta: $path")

            val relativeDirectory = path.substringBeforeLast('/')
            val displayName = path.substringAfterLast('/')
            val saved = SharedDocumentsArchive.archiveFile(
                context,
                temporary,
                relativeDirectory,
                displayName,
                "application/pdf",
                overwrite = true
            )
            check(saved != null) { "PDF-tiedostoa ei voitu tallentaa kansioon Documents/TarkastusPro: $path" }
        } finally {
            temporary.delete()
        }
    }

    private fun saveConflict(context: Context, conflictRoot: DocumentFile, path: String, local: Uri) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safeName = path.replace('/', '_').replace('\\', '_')
        val target = conflictRoot.createFile("application/pdf", "${safeName}_local_${stamp}.pdf")
            ?: error("Paikallisen PDF-version tallentaminen ristiriitatiedostoksi epäonnistui.")
        try {
            context.contentResolver.openInputStream(local)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output -> input.copyTo(output) }
                    ?: error("Ristiriitaisen PDF-version tallentaminen epäonnistui.")
            } ?: error("Paikallista PDF-versiota ei voitu lukea.")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("PDF-tiedoston tarkistussummaa ei voitu lukea.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(context: Context, file: DocumentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(file.uri)?.buffered()?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Google Drive -PDF:n tarkistussummaa ei voitu lukea.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith("/") &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
            !path.contains('\\')
        ) { "PDF-synkronoinnissa havaittiin virheellinen tiedostopolku." }
    }
}

package fi.vladi.valvontapoytakirja.cloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-level two-way sync. Each device stores hashes of the last common version.
 * One-sided edits are copied; simultaneous edits preserve the local copy in Drive/Conflicts.
 * Deletions propagate only when the other side has not changed since the last sync.
 */
object CloudSyncManager {
    private const val PREFS = "tarkastus_cloud_sync"
    private const val BASELINE_KEY = "baseline_shared_v2"
    private const val SYNC_FOLDER = "Tarkastus_Sync"
    private const val DATA_FOLDER = "shared_v2"
    private const val CONFLICTS_FOLDER = "Conflicts"

    /**
     * Explicit shared-data allowlist. Inspector profiles, licenses, signatures,
     * app_settings.json, credentials, templates and local preferences stay private.
     * Report snapshots and their attachments are shared intentionally.
     * Never synchronize arbitrary files from the app-private directory.
     */
    private fun isSharedPath(path: String): Boolean {
        val parts = path.split('/')
        if (parts.size < 2 || parts.any { it.isBlank() || it == "." || it == ".." }) return false
        return when (parts.first()) {
            "customers", "customer_sites" -> parts.size == 2 &&
                parts.last().endsWith(".json")
            "reports", "tarkastus_reports" ->
                (parts.size == 2 && parts.last().endsWith(".json")) ||
                (parts.size == 4 && parts[2] == "photos" &&
                    parts.last().substringAfterLast('.', "").lowercase() in
                    setOf("jpg", "jpeg", "png", "webp")) ||
                (parts.size == 3 &&
                    parts.last().substringAfterLast('.', "").lowercase() in
                    setOf("jpg", "jpeg", "png", "webp"))
            else -> false
        }
    }

    data class Result(
        val uploaded: Int,
        val downloaded: Int,
        val deleted: Int,
        val conflicts: Int,
        val pdfUploaded: Int = 0,
        val pdfDownloaded: Int = 0,
        val pdfDeleted: Int = 0,
        val pdfConflicts: Int = 0,
        val pdfFoundLocal: Int = 0,
        val pdfFoundDrive: Int = 0
    ) {
        fun asMessage(): String =
            "Synkronointi valmis. Tiedostot: lähetetty $uploaded, ladattu $downloaded, poistettu $deleted, ristiriitoja $conflicts. " +
                "PDF:t löytyi laitteelta $pdfFoundLocal, Drivesta $pdfFoundDrive; lähetetty $pdfUploaded, ladattu $pdfDownloaded, poistettu $pdfDeleted, ristiriitoja $pdfConflicts."
    }

    @Synchronized
    fun sync(context: Context, treeUri: Uri): Result {
        val selectedRoot = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Valittua Google Drive -kansiota ei voi avata.")
        check(selectedRoot.isDirectory && selectedRoot.canWrite()) { "Valittuun kansioon ei voi tallentaa." }

        val syncRoot = selectedRoot.findFile(SYNC_FOLDER) ?: selectedRoot.createDirectory(SYNC_FOLDER)
            ?: error("Synkronointikansiota ei voitu luoda.")
        // Older builds synchronized every private file into Tarkastus_Sync/data.
        // Refuse to silently treat that unsafe folder as the new shared dataset.
        // The old folder is left untouched for the owner to review and remove.
        val legacyData = syncRoot.findFile("data")
        check(legacyData == null || !legacyData.isDirectory ||
            legacyData.listFiles().isEmpty()) {
            "Vanhassa Tarkastus_Sync/data-kansiossa voi olla henkilökohtaisia tietoja. " +
                "Tarkista ja poista vanha data-kansio Google Drivesta ennen uutta synkronointia."
        }
        val remoteData = syncRoot.findFile(DATA_FOLDER) ?: syncRoot.createDirectory(DATA_FOLDER)
            ?: error("Synkronoinnin tietokansiota ei voitu luoda.")
        val conflictRoot = syncRoot.findFile(CONFLICTS_FOLDER) ?: syncRoot.createDirectory(CONFLICTS_FOLDER)
            ?: error("Ristiriitakansiota ei voitu luoda.")

        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldBaseline = runCatching {
            JSONObject(preferences.getString(BASELINE_KEY, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val nextBaseline = JSONObject()

        val localRoot = context.filesDir
        // Recover interrupted replacements before scanning files for synchronization.
        localRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".sync_backup") }
            .forEach { backup ->
                val target = File(backup.parentFile, backup.name.removeSuffix(".sync_backup"))
                val relativePath = target.relativeTo(localRoot).invariantSeparatorsPath
                if (isSharedPath(relativePath) && !target.exists()) {
                    check(backup.renameTo(target)) {
                        "Keskeytyneen synkronoinnin palautus epäonnistui: $relativePath"
                    }
                } else if (isSharedPath(relativePath) && target.exists()) {
                    backup.delete()
                }
            }
        val localFiles = localRoot.walkTopDown().filter { it.isFile }.mapNotNull { file ->
            val path = file.relativeTo(localRoot).invariantSeparatorsPath
            // Never publish interrupted downloads or temporary local files to colleagues.
            if (!isSharedPath(path) || path.endsWith(".sync_tmp") || path.endsWith(".sync_backup") || path.endsWith(".tmp")
            ) null else path to file
        }.toMap()
        val remoteFiles = linkedMapOf<String, DocumentFile>()
        collectRemoteFiles(remoteData, "", remoteFiles)

        // JSONObject.keys() is an Iterator, not a Collection. Materialize it first
        // so Kotlin keeps the path set strongly typed as String throughout the sync.
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
            validateRelativePath(path)
            if (!isSharedPath(path)) continue
            var local = localFiles[path]?.takeIf { it.isFile }
            var remote = remoteFiles[path]?.takeIf { it.exists() }
            var localHash = local?.let(::sha256)
            var remoteHash = remote?.let { sha256(context, it) }
            val baseHash = oldBaseline.optString(path).takeIf { it.isNotBlank() }

            if (localHash == remoteHash) {
                if (localHash != null) nextBaseline.put(path, localHash)
                continue
            }

            if (baseHash == null) {
                when {
                    local != null && remote == null -> {
                        upload(context, remoteData, path, local)
                        uploaded++
                    }
                    local == null && remote != null -> {
                        download(context, remote, localRoot, path)
                        downloaded++
                    }
                    local != null && remote != null -> {
                        saveConflict(context, conflictRoot, path, local)
                        download(context, remote, localRoot, path)
                        conflicts++
                        downloaded++
                    }
                }
            } else if (localHash == baseHash) {
                // Local side is unchanged: accept remote edit or deletion.
                if (remote == null) {
                    // Do not delete local reports based on a missing Drive file.
                    // A colleague or provider may have removed it accidentally.
                    if (local != null) { upload(context, remoteData, path, local); uploaded++ }
                } else {
                    download(context, remote, localRoot, path)
                    downloaded++
                }
            } else if (remoteHash == baseHash) {
                // Remote side is unchanged: publish local edit or deletion.
                if (local == null) {
                    // A local deletion is not sufficient authorization to delete
                    // a shared report or customer record from Drive.
                    if (remote != null) { download(context, remote, localRoot, path); downloaded++ }
                } else {
                    upload(context, remoteData, path, local)
                    uploaded++
                }
            } else {
                // Both sides changed since their last common version.
                conflicts++
                if (local != null) saveConflict(context, conflictRoot, path, local)
                if (remote != null) {
                    download(context, remote, localRoot, path)
                    downloaded++
                } else if (local != null) {
                    // Remote deletion conflicts with local edit; preserve the edit.
                    upload(context, remoteData, path, local)
                    uploaded++
                }
            }

            local = File(localRoot, path).takeIf { it.isFile }
            remote = findRemoteFile(remoteData, path)
            localHash = local?.let(::sha256)
            remoteHash = remote?.let { sha256(context, it) }
            if (localHash != null && localHash == remoteHash) nextBaseline.put(path, localHash)
        }

        check(preferences.edit().putString(BASELINE_KEY, nextBaseline.toString()).commit()) {
            "Synkronoinnin tilaa ei voitu tallentaa laitteelle."
        }

        // Synchronize exported PDFs from Documents/Tarkastus as a separate dataset.
        val pdfResult = PdfArchiveSyncManager.sync(context, selectedRoot, syncRoot, conflictRoot)
        return Result(
            uploaded = uploaded,
            downloaded = downloaded,
            deleted = deleted,
            conflicts = conflicts,
            pdfUploaded = pdfResult.uploaded,
            pdfDownloaded = pdfResult.downloaded,
            pdfDeleted = pdfResult.deleted,
            pdfConflicts = pdfResult.conflicts,
            pdfFoundLocal = pdfResult.foundLocal,
            pdfFoundDrive = pdfResult.foundDrive
        )
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
            else if (child.isFile) result[path] = child
        }
    }

    private fun findRemoteFile(root: DocumentFile, relativePath: String): DocumentFile? {
        val segments = relativePath.split('/')
        var current = root
        for (segment in segments.dropLast(1)) {
            current = current.findFile(segment)?.takeIf { it.isDirectory } ?: return null
        }
        return current.findFile(segments.last())?.takeIf { it.isFile && it.exists() }
    }

    private fun ensureRemoteParent(root: DocumentFile, relativePath: String): DocumentFile {
        val segments = relativePath.split('/')
        var current = root
        for (segment in segments.dropLast(1)) {
            current = current.findFile(segment)?.takeIf { it.isDirectory }
                ?: current.createDirectory(segment)
                ?: error("Drive-kansiota ei voitu luoda: $segment")
        }
        return current
    }

    private fun upload(context: Context, root: DocumentFile, path: String, source: File) {
        val parent = ensureRemoteParent(root, path)
        val name = path.substringAfterLast('/')
        parent.findFile(name)?.delete()
        val target = parent.createFile(mimeType(name), name) ?: error("Tiedostoa ei voitu luoda Driveen: $path")
        try {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Drive-tiedoston kirjoittaminen epäonnistui: $path")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun download(context: Context, source: DocumentFile, localRoot: File, path: String) {
        val target = File(localRoot, path)
        val canonicalRoot = localRoot.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
            "Virheellinen paikallinen tiedostopolku."
        }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".sync_tmp")
        try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("Drive-tiedoston lukeminen epäonnistui: $path")
            // Stage a complete download before touching the current file.
            // Keep a rollback copy until the replacement has succeeded.
            val backup = File(target.parentFile, target.name + ".sync_backup")
            if (backup.exists() && !backup.delete()) error("Vanhaa varmuuskopiota ei voitu poistaa: $path")
            if (target.exists() && !target.renameTo(backup)) {
                error("Paikallista tiedostoa ei voitu varmuuskopioida: $path")
            }
            try {
                if (!temporary.renameTo(target)) error("Ladatun tiedoston käyttöönotto epäonnistui: $path")
                backup.delete()
            } catch (error: Throwable) {
                if (backup.exists()) {
                    if (target.exists()) target.delete()
                    if (!backup.renameTo(target)) {
                        error("Tiedoston palautus epäonnistui; varmuuskopio on tallessa: ${backup.absolutePath}")
                    }
                }
                throw error
            }
        } finally {
            temporary.delete()
        }
    }

    private fun saveConflict(context: Context, conflictRoot: DocumentFile, path: String, localFile: File) {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safeName = path.replace('/', '_').replace('\\', '_')
        val target = conflictRoot.createFile("application/octet-stream", "${safeName}_local_${date}.conflict")
            ?: error("Ristiriitaisen version tallentaminen epäonnistui.")
        try {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                localFile.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Ristiriitaisen version kirjoittaminen epäonnistui.")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
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
        } ?: error("Drive-tiedoston tarkistussummaa ei voitu lukea.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank() && !path.startsWith("/") &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
            !path.contains('\\')
        ) { "Synkronoinnissa havaittiin virheellinen tiedostopolku." }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }
}

package fi.vladi.valvontapoytakirja

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

internal fun customId() = UUID.randomUUID().toString()
@Serializable
internal enum class CustomKind { TEXT, LONG_TEXT, NUMBER, DATE_TIME, CHECKBOX, SINGLE, MULTIPLE, PHOTOS, SIGNATURE, HEADING, TABLE }
@Serializable
internal data class CustomField(val id: String = customId(), val label: String = "", val kind: CustomKind = CustomKind.TEXT, val required: Boolean = false, val options: List<String> = emptyList(), val defaultValue: String = "", val photoColumns: Int = 2, val photoRows: Int = 2, val separatePhotoPages: Boolean = false)
@Serializable
internal data class CustomSection(val id: String = customId(), val title: String = "", val fields: List<CustomField> = emptyList())
@Serializable
internal data class CustomTemplate(val id: String = customId(), val title: String = "", val sections: List<CustomSection> = listOf(CustomSection()))
@Serializable
internal data class CustomDocument(val id: String = customId(), val template: CustomTemplate = CustomTemplate(), val values: Map<String,String> = emptyMap(), val attachments: Map<String,List<String>> = emptyMap(), val pdfLanguage: String = "", val useSharedSettings: Boolean = false, val customerId: String = "", val customerText: String = "", val siteId: String = "", val siteText: String = "", val inspectorId: String = "", val inspectorText: String = "")
@Serializable
internal data class CustomLibrary(val templates: List<CustomTemplate> = emptyList(), val documents: List<CustomDocument> = emptyList())
internal class CustomStore(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "custom_reports"))
    init { root.mkdirs() }
    companion object {
        private val lock = Any()
        internal fun <T> withStorageLock(action: () -> T): T = synchronized(lock) { action() }
    }
    private val file = AtomicFile(File(root, "library.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val records = File(root,"records").apply { mkdirs() }
    private val session = AtomicFile(File(root, "active-editor"))
    fun activeEditor(): String? = synchronized(lock) { try { session.readFully().decodeToString().takeIf { it.isNotBlank() } } catch (_: java.io.FileNotFoundException) { null } }
    fun rememberEditor(value: String) = synchronized(lock) { if (activeEditor().orEmpty() != value) write(session, value.encodeToByteArray()) }
    private val marker = AtomicFile(File(records,"ready"))
    private fun readLegacy(): CustomLibrary = try {
        json.decodeFromString<CustomLibrary>(file.readFully().decodeToString())
    } catch (e: java.io.FileNotFoundException) {
        if (file.baseFile.exists() || File(root,"library.json.bak").exists()) throw e else CustomLibrary()
    }
    private fun write(target: AtomicFile, bytes: ByteArray) {
        val out = target.startWrite()
        try { out.write(bytes); out.fd.sync(); target.finishWrite(out) }
        catch (e: Exception) { target.failWrite(out); throw e }
        check(target.readFully().contentEquals(bytes)) { "Record verification failed" }
    }
    private fun record(kind: String,id: String): AtomicFile {
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return AtomicFile(File(records,"$kind-$key.json"))
    }
    private fun tombstone(kind: String, id: String) = AtomicFile(File(record(kind,id).baseFile.path + ".deleted"))
    private fun deleted(kind: String, id: String) = tombstone(kind,id).baseFile.exists()
    fun load(): CustomLibrary = synchronized(lock) {
        if (!marker.baseFile.exists()) {
            val legacy = readLegacy()
            legacy.templates.forEach { if (!record("t",it.id).baseFile.exists() && !File(record("t",it.id).baseFile.path+".bak").exists()) saveTemplate(it) }
            legacy.documents.forEach { if (!record("d",it.id).baseFile.exists() && !File(record("d",it.id).baseFile.path+".bak").exists()) saveDocument(it) }
            write(marker,byteArrayOf(1))
        }
        val files = records.listFiles() ?: error("Cannot list records")
        val names = files.map { it.name.removeSuffix(".bak") }.distinct()
        val templates = names.filter { it.startsWith("t-") && it.endsWith(".json") }.map {
            json.decodeFromString<CustomTemplate>(AtomicFile(File(records,it)).readFully().decodeToString())
        }
        val documents = names.filter { it.startsWith("d-") && it.endsWith(".json") }.map {
            json.decodeFromString<CustomDocument>(AtomicFile(File(records,it)).readFully().decodeToString())
        }
        CustomLibrary(templates.filterNot { deleted("t",it.id) },documents.filterNot { deleted("d",it.id) })
    }
    fun deleteDocument(id: String) = synchronized(lock) {
        load()
        write(tombstone("d",id),byteArrayOf(1))
        val target = record("d", id)
        target.delete()
        check(!target.baseFile.exists() && !File(target.baseFile.path+".bak").exists())
        if (activeEditor() == "d:" + id) rememberEditor("")
    }
    fun deleteTemplate(id: String) = synchronized(lock) {
        load()
        write(tombstone("t",id),byteArrayOf(1))
        val target = record("t", id)
        target.delete()
        check(!target.baseFile.exists() && !File(target.baseFile.path+".bak").exists())
        if (activeEditor() == "t:" + id) rememberEditor("")
    }
    fun saveDocument(value: CustomDocument) = synchronized(lock) { if (!deleted("d",value.id)) write(record("d",value.id),json.encodeToString(value).encodeToByteArray()) }
    fun saveTemplate(value: CustomTemplate) = synchronized(lock) { if (!deleted("t",value.id)) write(record("t",value.id),json.encodeToString(value).encodeToByteArray()) }
    fun save(value: CustomLibrary) = synchronized(lock) {
        value.templates.forEach { saveTemplate(it) }
        value.documents.forEach { saveDocument(it) }
    }
    // Called only on opening the editor, before any new attachments are created.
    // Retain recent unreferenced files to tolerate interrupted imports or recovery.
    fun cleanUnused(value: CustomLibrary) = synchronized(lock) {
        val used = (value.documents + load().documents + readLegacy().documents.filterNot { deleted("d",it.id) }).flatMap { it.attachments.values.flatten() }.toSet()
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        root.listFiles()?.forEach { candidate ->
            if (candidate.isFile && candidate.name.matches(Regex("[0-9a-fA-F-]{36}\\.(image|png)")) &&
                candidate.name !in used && candidate.lastModified() in 1 until cutoff &&
                candidate.canonicalFile.parentFile == root.canonicalFile) candidate.delete()
        }
    }
    fun importImage(context: Context, uri: android.net.Uri): String {
        val target = File(root, customId() + ".image")
        val staging = File(root, target.name + ".pending")
        try { context.contentResolver.openInputStream(uri)?.use { input -> staging.outputStream().use { val buffer = ByteArray(8192); var total = 0L; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 50L * 1024 * 1024); it.write(buffer,0,n) }; it.fd.sync() } } ?: error("Cannot read image") }
        catch (e: Exception) { staging.delete(); throw e }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(staging.path,bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) { staging.delete(); error("Invalid image") }
        if (!staging.renameTo(target)) { staging.delete(); error("Cannot publish image") }
        return target.name
    }
    fun saveBitmap(bitmap: android.graphics.Bitmap): String {
        val target = File(root, customId() + ".png")
        val staging = File(root, target.name + ".pending")
        try { staging.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }; check(staging.renameTo(target)) }
        catch (e: Exception) { staging.delete(); throw e }
        return target.name
    }
    fun image(name: String): File {
        require(name.matches(Regex("[0-9a-fA-F-]{36}\\.(image|png)"))) { "Invalid attachment name" }
        return File(root,name)
    }
}

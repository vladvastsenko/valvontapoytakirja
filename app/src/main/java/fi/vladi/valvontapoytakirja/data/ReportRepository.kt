package fi.vladi.valvontapoytakirja.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import fi.vladi.valvontapoytakirja.model.InspectionReport
import fi.vladi.valvontapoytakirja.model.AppSettings
import fi.vladi.valvontapoytakirja.model.InspectorProfile
import fi.vladi.valvontapoytakirja.model.CustomerProfile
import fi.vladi.valvontapoytakirja.model.CustomerSite
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
import kotlinx.serialization.json.Json
import fi.vladi.tarkastuspoytakirja.storage.AtomicRecords
import java.io.File
import java.time.Instant

class ReportRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun settingsFile(): File = File(context.filesDir, "app_settings.json")

    fun loadSettings(): AppSettings =
        runCatching { json.decodeFromString(AppSettings.serializer(), AtomicRecords.read(settingsFile())) }
            .getOrDefault(AppSettings())

    fun saveSettings(settings: AppSettings) {
        settingsFile().writeAtomicText(json.encodeToString(AppSettings.serializer(), settings))
        SharedDocumentsArchive.archiveText(context, json.encodeToString(AppSettings.serializer(), settings), "Asetukset", "app_settings.json")
        File(settings.logoPath).takeIf { it.isFile }?.let { logo ->
            SharedDocumentsArchive.archiveFile(context, logo, "Asetukset", logo.name)
        }
        File(settings.signatureImagePath).takeIf { it.isFile }?.let { signature ->
            SharedDocumentsArchive.archiveFile(context, signature, "Asetukset", signature.name)
        }
    }

    private fun inspectorsDirectory(): File =
        File(context.filesDir, "inspectors").apply { mkdirs() }

    fun listInspectors(): List<InspectorProfile> =
        AtomicRecords.list(inspectorsDirectory())
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectorProfile.serializer(), AtomicRecords.read(file)) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveInspector(inspector: InspectorProfile) {
        require(inspector.name.isNotBlank()) { "Inspector name is required" }
        require(inspector.id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val file = File(inspectorsDirectory(), "${inspector.id}.json")
        file.writeAtomicText(json.encodeToString(InspectorProfile.serializer(), inspector))
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Tarkastajat", file.name, "application/json")
        File(inspector.signatureImagePath).takeIf { it.isFile }?.let { signature ->
            SharedDocumentsArchive.archiveFile(context, signature, "Asetukset/Tarkastajat/Allekirjoitukset", signature.name)
        }
    }

    fun deleteInspector(inspectorId: String) {
        require(inspectorId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        AtomicRecords.delete(File(inspectorsDirectory(), "${inspectorId}.json"))
    }

    private fun customersDirectory(): File =
        File(context.filesDir, "customers").apply { mkdirs() }

    fun listCustomers(): List<CustomerProfile> =
        AtomicRecords.list(customersDirectory())
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(CustomerProfile.serializer(), AtomicRecords.read(file)) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveCustomer(customer: CustomerProfile) {
        requireSafeId(customer.id)
        val file = File(customersDirectory(), "${customer.id}.json")
        file.writeAtomicText(json.encodeToString(CustomerProfile.serializer(), customer))
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Asiakkaat", file.name, "application/json")
    }

    fun deleteCustomer(customerId: String) {
        requireSafeId(customerId)
        AtomicRecords.delete(File(customersDirectory(), "${customerId}.json"))
        listSites().filter { it.customerId == customerId }.forEach { deleteSite(it.id) }
    }

    private fun sitesDirectory(): File =
        File(context.filesDir, "customer_sites").apply { mkdirs() }

    fun listSites(customerId: String? = null): List<CustomerSite> =
        AtomicRecords.list(sitesDirectory())
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(CustomerSite.serializer(), AtomicRecords.read(file)) }.getOrNull()
            }
            ?.filter { customerId == null || it.customerId == customerId }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveSite(site: CustomerSite) {
        requireSafeId(site.id)
        val file = File(sitesDirectory(), "${site.id}.json")
        file.writeAtomicText(json.encodeToString(CustomerSite.serializer(), site))
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Kohteet", file.name, "application/json")
    }

    fun deleteSite(siteId: String) {
        requireSafeId(siteId)
        AtomicRecords.delete(File(sitesDirectory(), "${siteId}.json"))
    }

    fun importInspectorSignature(inspectorId: String, uri: Uri): File? = runCatching {
        require(inspectorId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val directory = File(inspectorsDirectory(), "signatures").apply { mkdirs() }
        val extension = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val destination = File(directory, "signature_${inspectorId}_${Instant.now().toEpochMilli()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open inspector signature")
        input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
        destination
    }.getOrNull()

    fun importSettingsSignature(uri: Uri): File? = runCatching {
        val directory = File(context.filesDir, "settings").apply { mkdirs() }
        val extension = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val destination = File(directory, "signature_${Instant.now().toEpochMilli()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open signature")
        input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
        destination
    }.getOrNull()

    fun importSettingsLogo(uri: Uri): File? {
        return runCatching {
            val directory = File(context.filesDir, "settings").apply { mkdirs() }
            val extension = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(directory, "logo_${Instant.now().toEpochMilli()}.$extension")
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected logo")
            input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
            destination
        }.getOrNull()
    }

    fun archiveExistingData() {
        SharedDocumentsArchive.removeArchivedReportPhotos(context)
        val preferences = context.getSharedPreferences("shared_documents_archive", Context.MODE_PRIVATE)
        val migrationKey = "valvonta_v1"
        if (preferences.getBoolean(migrationKey, false)) return
        var complete = true
        fun archive(file: File, directory: String, mime: String = "application/json", overwrite: Boolean = true) {
            if (file.isFile && SharedDocumentsArchive.archiveFile(context, file, directory, file.name, mime, overwrite) == null) {
                complete = false
            }
        }

        settingsFile().takeIf { it.isFile }?.let { archive(it, "Asetukset") }
        File(settingsFile().parentFile, "settings").listFiles()?.forEach { archive(it, "Asetukset") }
        inspectorsDirectory().listFiles()?.forEach { archive(it, "Asetukset/Tarkastajat") }
        customersDirectory().listFiles()?.forEach { archive(it, "Asetukset/Asiakkaat") }
        sitesDirectory().listFiles()?.forEach { archive(it, "Asetukset/Kohteet") }

        AtomicRecords.list(reportsDirectory())?.forEach { file ->
            archive(file, "Raportit/Data/Valvontapoytakirja")
        }
        AtomicRecords.list(templatesDirectory())?.forEach {
            archive(it, "Raportit/Mallit/Valvontapoytakirja")
        }
        if (complete) preferences.edit().putBoolean(migrationKey, true).apply()
    }

    private fun reportsDirectory(): File =
        File(context.filesDir, "reports").apply { mkdirs() }

    fun listReports(): List<InspectionReport> =
        AtomicRecords.list(reportsDirectory())
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectionReport.serializer(), AtomicRecords.read(file)) }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    private fun templatesDirectory(): File =
        File(context.filesDir, "templates").apply { mkdirs() }

    fun listTemplates(): List<InspectionReport> =
        AtomicRecords.list(templatesDirectory())
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectionReport.serializer(), AtomicRecords.read(file)) }.getOrNull()
            }
            ?.sortedBy { it.templateName.lowercase() }
            ?: emptyList()

    fun saveTemplate(report: InspectionReport, name: String) {
        val template = report.copy(
            id = java.util.UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            templateName = name.trim(),
            photos = emptyList(),
            signatureStrokes = emptyList()
        )
        File(templatesDirectory(), template.id + ".json")
            .writeAtomicText(json.encodeToString(InspectionReport.serializer(), template))
    }
    fun deleteReport(reportId: String) {
        requireSafeId(reportId)
        AtomicRecords.delete(File(reportsDirectory(), "$reportId.json"))
        File(reportsDirectory(), reportId).deleteRecursively()
    }

    fun save(report: InspectionReport) {
        requireSafeId(report.id)
        val file = File(reportsDirectory(), "${report.id}.json")
        file.writeAtomicText(json.encodeToString(InspectionReport.serializer(), report))
        SharedDocumentsArchive.archiveFile(context, file, "Raportit/Data/Valvontapoytakirja", file.name, "application/json")
    }

    fun createCameraPhotoFile(reportId: String): File {
        requireSafeId(reportId)
        val directory = File(reportsDirectory(), "$reportId/photos").apply { mkdirs() }
        return File(directory, "photo_${java.util.UUID.randomUUID()}.jpg")
    }

    fun cameraUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun importPhoto(reportId: String, uri: Uri): File? {
        requireSafeId(reportId)
        return runCatching {
            val directory = File(reportsDirectory(), "$reportId/photos").apply { mkdirs() }
            val destination = File(directory, "photo_${java.util.UUID.randomUUID()}.jpg")
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width.coerceAtLeast(1)
                    val height = info.size.height.coerceAtLeast(1)
                    val scale = minOf(1f, 2200f / maxOf(width, height))
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    ?: error("Unable to read selected image")
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 2200) sampleSize *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Unable to decode selected image")
            }
            try {
                destination.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                        "Unable to save selected image"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            destination
        }.getOrNull()
    }

    fun importLogo(reportId: String, uri: Uri): File? {
        requireSafeId(reportId)
        return runCatching {
            val directory = File(reportsDirectory(), reportId).apply { mkdirs() }
            val extension = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(directory, "logo.${extension}")
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected logo")
            input.use {
                destination.outputStream().use { output -> it.copyTo(output) }
            }
            destination
        }.getOrNull()
    }

    fun deletePhoto(path: String) {
        runCatching {
            val file = File(path).canonicalFile
            val root = reportsDirectory().canonicalFile
            require(file.path.startsWith(root.path + File.separator) && file.parentFile?.name == "photos")
            file.delete()
        }
    }
}

private fun requireSafeId(id: String) {
    require(id.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) { "Invalid record identifier" }
}

private fun File.writeAtomicText(value: String) = AtomicRecords.write(this, value)

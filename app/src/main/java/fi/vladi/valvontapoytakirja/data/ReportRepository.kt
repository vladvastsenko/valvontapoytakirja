package fi.vladi.valvontapoytakirja.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import fi.vladi.valvontapoytakirja.model.InspectionReport
import fi.vladi.valvontapoytakirja.model.AppSettings
import fi.vladi.valvontapoytakirja.model.CustomerProfile
import fi.vladi.valvontapoytakirja.model.CustomerSite
import fi.vladi.tarkastuspoytakirja.storage.PublicArchive
import kotlinx.serialization.json.Json
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
        runCatching { json.decodeFromString(AppSettings.serializer(), settingsFile().readText()) }
            .getOrDefault(AppSettings())

    fun saveSettings(settings: AppSettings) {
        settingsFile().writeText(json.encodeToString(AppSettings.serializer(), settings))
        PublicArchive.syncFile(context, settingsFile())
        if (settings.logoPath.isNotBlank()) PublicArchive.syncFile(context, File(settings.logoPath), force = false)
    }

    private fun customersDirectory(): File =
        File(context.filesDir, "customers").apply { mkdirs() }

    fun listCustomers(): List<CustomerProfile> =
        customersDirectory().listFiles { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(CustomerProfile.serializer(), file.readText()) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveCustomer(customer: CustomerProfile) {
        val file = File(customersDirectory(), "${customer.id}.json")
        file.writeText(json.encodeToString(CustomerProfile.serializer(), customer))
        PublicArchive.syncFile(context, file)
    }

    fun deleteCustomer(customerId: String) {
        val file = File(customersDirectory(), "${customerId}.json")
        file.delete()
        PublicArchive.deleteFile(context, file)
        listSites().filter { it.customerId == customerId }.forEach { deleteSite(it.id) }
    }

    private fun sitesDirectory(): File =
        File(context.filesDir, "customer_sites").apply { mkdirs() }

    fun listSites(customerId: String? = null): List<CustomerSite> =
        sitesDirectory().listFiles { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(CustomerSite.serializer(), file.readText()) }.getOrNull()
            }
            ?.filter { customerId == null || it.customerId == customerId }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveSite(site: CustomerSite) {
        val file = File(sitesDirectory(), "${site.id}.json")
        file.writeText(json.encodeToString(CustomerSite.serializer(), site))
        PublicArchive.syncFile(context, file)
    }

    fun deleteSite(siteId: String) {
        val file = File(sitesDirectory(), "${siteId}.json")
        file.delete()
        PublicArchive.deleteFile(context, file)
    }

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
            PublicArchive.syncFile(context, destination)
            destination
        }.getOrNull()
    }

    private fun reportsDirectory(): File =
        File(context.filesDir, "reports").apply { mkdirs() }

    fun listReports(): List<InspectionReport> =
        reportsDirectory().listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectionReport.serializer(), file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    private fun templatesDirectory(): File =
        File(context.filesDir, "templates").apply { mkdirs() }

    fun listTemplates(): List<InspectionReport> =
        templatesDirectory().listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectionReport.serializer(), file.readText()) }.getOrNull()
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
        val file = File(templatesDirectory(), template.id + ".json")
        file.writeText(json.encodeToString(InspectionReport.serializer(), template))
        PublicArchive.syncFile(context, file)
    }
    fun save(report: InspectionReport) {
        val file = File(reportsDirectory(), "${report.id}.json")
        file.writeText(json.encodeToString(InspectionReport.serializer(), report))
        PublicArchive.syncFile(context, file)
        report.photos.forEach { photo ->
            PublicArchive.syncFile(context, File(photo.filePath), force = false)
        }
        if (report.logoPath.isNotBlank()) {
            PublicArchive.syncFile(context, File(report.logoPath), force = false)
        }
    }

    fun deleteReport(reportId: String) {
        val jsonFile = File(reportsDirectory(), "${reportId}.json")
        val reportDirectory = File(reportsDirectory(), reportId)
        jsonFile.delete()
        reportDirectory.deleteRecursively()
        PublicArchive.deleteFile(context, jsonFile)
        PublicArchive.deleteDirectory(context, reportDirectory)
    }

    fun createCameraPhotoFile(reportId: String): File {
        val directory = File(reportsDirectory(), "$reportId/photos").apply { mkdirs() }
        return File(directory, "photo_${Instant.now().toEpochMilli()}.jpg")
    }

    fun cameraUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun importPhoto(reportId: String, uri: Uri): File? {
        return runCatching {
            val directory = File(reportsDirectory(), "$reportId/photos").apply { mkdirs() }
            val destination = File(directory, "photo_${System.currentTimeMillis()}_${directory.listFiles()?.size ?: 0}.jpg")
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected image")
            input.use {
                destination.outputStream().use { output -> it.copyTo(output) }
            }
            PublicArchive.syncFile(context, destination)
            destination
        }.getOrNull()
    }

    fun importLogo(reportId: String, uri: Uri): File? {
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
            PublicArchive.syncFile(context, destination)
            destination
        }.getOrNull()
    }

    fun deletePhoto(path: String) {
        runCatching {
            val file = File(path)
            file.delete()
            PublicArchive.deleteFile(context, file)
        }
    }
}

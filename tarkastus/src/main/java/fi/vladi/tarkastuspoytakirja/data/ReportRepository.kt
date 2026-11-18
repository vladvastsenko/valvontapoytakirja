package fi.vladi.tarkastuspoytakirja.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import fi.vladi.tarkastuspoytakirja.model.InspectionReport
import fi.vladi.tarkastuspoytakirja.model.AppSettings
import fi.vladi.tarkastuspoytakirja.model.CustomerProfile
import fi.vladi.tarkastuspoytakirja.model.CustomerSite
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
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
        SharedDocumentsArchive.archiveText(context, json.encodeToString(AppSettings.serializer(), settings), "Asetukset", "app_settings.json")
        File(settings.logoPath).takeIf { it.isFile }?.let { logo ->
            SharedDocumentsArchive.archiveFile(context, logo, "Asetukset", logo.name)
        }
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
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Asiakkaat", file.name, "application/json")
    }

    fun deleteCustomer(customerId: String) {
        File(customersDirectory(), "${customerId}.json").delete()
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
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Kohteet", file.name, "application/json")
    }

    fun deleteSite(siteId: String) {
        File(sitesDirectory(), "${siteId}.json").delete()
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
            destination
        }.getOrNull()
    }

    fun archiveExistingData() {
        val preferences = context.getSharedPreferences("shared_documents_archive", Context.MODE_PRIVATE)
        val migrationKey = "tarkastus_v1"
        if (preferences.getBoolean(migrationKey, false)) return
        var complete = true
        fun archive(file: File, directory: String, mime: String = "application/json", overwrite: Boolean = true) {
            if (file.isFile && SharedDocumentsArchive.archiveFile(context, file, directory, file.name, mime, overwrite) == null) {
                complete = false
            }
        }

        settingsFile().takeIf { it.isFile }?.let { archive(it, "Asetukset") }
        File(settingsFile().parentFile, "settings").listFiles()?.forEach { archive(it, "Asetukset") }
        customersDirectory().listFiles()?.forEach { archive(it, "Asetukset/Asiakkaat") }
        sitesDirectory().listFiles()?.forEach { archive(it, "Asetukset/Kohteet") }

        reportsDirectory().listFiles { it.extension == "json" }?.forEach { file ->
            archive(file, "Raportit/Data/Tarkastuspoytakirja")
            val report = runCatching {
                json.decodeFromString(InspectionReport.serializer(), file.readText())
            }.getOrNull()
            report?.observations?.flatMap { it.photos }?.forEach { photo ->
                archive(File(photo.filePath), "Raportit/Kuvat/Tarkastuspoytakirja/" + report.id, overwrite = false)
                if (photo.annotatedPath.isNotBlank()) {
                    archive(File(photo.annotatedPath), "Raportit/Kuvat/Tarkastuspoytakirja/" + report.id, overwrite = false)
                }
            }
        }
        templatesDirectory().listFiles { it.extension == "json" }?.forEach {
            archive(it, "Raportit/Mallit/Tarkastuspoytakirja")
        }
        if (complete) preferences.edit().putBoolean(migrationKey, true).apply()
    }

    private fun reportsDirectory(): File =
        File(context.filesDir, "tarkastus_reports").apply { mkdirs() }

    fun listReports(): List<InspectionReport> =
        reportsDirectory().listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectionReport.serializer(), file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

    private fun templatesDirectory(): File =
        File(context.filesDir, "tarkastus_templates").apply { mkdirs() }

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
            observations = report.observations.map { it.copy(photos = emptyList()) },
            signatureStrokes = emptyList()
        )
        File(templatesDirectory(), template.id + ".json")
            .writeText(json.encodeToString(InspectionReport.serializer(), template))
    }
    fun save(report: InspectionReport) {
        val file = File(reportsDirectory(), "${report.id}.json")
        file.writeText(json.encodeToString(InspectionReport.serializer(), report))
        SharedDocumentsArchive.archiveFile(context, file, "Raportit/Data/Tarkastuspoytakirja", file.name, "application/json")
        report.observations.flatMap { it.photos }.forEach { photo ->
            SharedDocumentsArchive.archiveFile(
                context, File(photo.filePath),
                "Raportit/Kuvat/Tarkastuspoytakirja/" + report.id,
                File(photo.filePath).name,
                overwrite = false
            )
            if (photo.annotatedPath.isNotBlank()) {
                SharedDocumentsArchive.archiveFile(
                    context, File(photo.annotatedPath),
                    "Raportit/Kuvat/Tarkastuspoytakirja/" + report.id,
                    File(photo.annotatedPath).name,
                    overwrite = false
                )
            }
        }
    }

    fun createCameraPhotoFile(reportId: String): File {
        val directory = File(reportsDirectory(), "$reportId/photos").apply { mkdirs() }
        return File(directory, "photo_${java.util.UUID.randomUUID()}.jpg")
    }

    fun cameraUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun importPhoto(reportId: String, uri: Uri): File? {
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
        runCatching { File(path).delete() }
    }
}

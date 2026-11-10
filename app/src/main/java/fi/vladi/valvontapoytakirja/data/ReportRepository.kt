package fi.vladi.valvontapoytakirja.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import fi.vladi.valvontapoytakirja.model.InspectionReport
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

class ReportRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
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
        File(templatesDirectory(), template.id + ".json")
            .writeText(json.encodeToString(InspectionReport.serializer(), template))
    }
    fun save(report: InspectionReport) {
        val file = File(reportsDirectory(), "${report.id}.json")
        file.writeText(json.encodeToString(InspectionReport.serializer(), report))
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

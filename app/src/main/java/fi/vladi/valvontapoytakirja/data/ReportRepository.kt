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
                runCatching { json.decodeFromString<InspectionReport>(file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()

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

    fun importPhoto(reportId: String, uri: Uri): File? = runCatching {
        val directory = File(reportsDirectory(), "$reportId/photos").apply { mkdirs() }
        val destination = File(directory, "photo_${System.currentTimeMillis()}_${directory.listFiles()?.size ?: 0}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        destination
    }.getOrNull()

    fun deletePhoto(path: String) {
        runCatching { File(path).delete() }
    }
}

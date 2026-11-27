package fi.vladi.valvontapoytakirja

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.vladi.valvontapoytakirja.cloud.CloudBackupManager
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @Test fun exportRestoreAndEmergencyExport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = File(context.filesDir, "backup-roundtrip-marker.txt")
        val archive = File(context.cacheDir, "roundtrip.zip")
        val recovery = File(context.cacheDir, "recovery.zip")
        try {
            data.writeText("before")
            CloudBackupManager.backupToDocument(context, Uri.fromFile(archive))
            data.writeText("after")
            CloudBackupManager.restoreFromUri(context, Uri.fromFile(archive))
            assertEquals("before", data.readText())
            CloudBackupManager.backupToDocument(context, Uri.fromFile(recovery), true)
            CloudBackupManager.restoreFromUri(context, Uri.fromFile(recovery))
            assertEquals("after", data.readText())
        } finally {
            data.delete()
            archive.delete()
            recovery.delete()
        }
    }

    @Test fun damagedArchiveDoesNotReplaceData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = File(context.filesDir, "damage-marker.txt")
        val archive = File(context.cacheDir, "damaged.zip")
        try {
            marker.writeText("keep")
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                fun entry(name: String, value: String) {
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
                entry("TARKASTUSPRO_BUILDING_BACKUP_INFO.txt", "TARKASTUSPRO_BUILDING_BACKUP_V2")
                entry("files/damage-marker.txt", "replace")
                entry("SHA256.json", """{"damage-marker.txt":"invalid"}""")
            }
            assertTrue(runCatching { CloudBackupManager.restoreFromUri(context, Uri.fromFile(archive)) }.isFailure)
            assertEquals("keep", marker.readText())
        } finally { marker.delete(); archive.delete() }
    }

    @Test fun realReportsWithPortableAttachments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wet = fi.vladi.valvontapoytakirja.data.ReportRepository(context)
        val general = fi.vladi.tarkastuspoytakirja.data.ReportRepository(context)
        val w = fi.vladi.valvontapoytakirja.model.InspectionReport()
        val g = fi.vladi.tarkastuspoytakirja.model.InspectionReport()
        val archive = File(context.cacheDir, "attachments.zip")
        val attachments = mutableListOf<File>()
        fun attachment(relative: String): String {
            val file = File(context.filesDir, relative)
            file.parentFile?.mkdirs()
            file.writeText(relative)
            attachments.add(file)
            return "/data/user/99/fi.vladi.tarkastuspro/files/$relative"
        }
        try {
            wet.save(w.copy(
                logoPath = attachment("reports/${w.id}/logo.png"),
                signatureImagePath = attachment("reports/${w.id}/signature.png"),
                photos = listOf(fi.vladi.valvontapoytakirja.model.ReportPhoto(filePath = attachment("reports/${w.id}/photos/photo.jpg")))
            ))
            general.save(g.copy(
                logoPath = attachment("tarkastus_reports/${g.id}/logo.png"),
                signatureImagePath = attachment("tarkastus_reports/${g.id}/signature.png"),
                observations = listOf(fi.vladi.tarkastuspoytakirja.model.InspectionObservation(photos = listOf(
                    fi.vladi.tarkastuspoytakirja.model.ReportPhoto(filePath = attachment("tarkastus_reports/${g.id}/photos/photo.jpg"), annotatedPath = attachment("tarkastus_reports/${g.id}/photos/annotated.jpg"))
                )))
            ))
            CloudBackupManager.backupToDocument(context, Uri.fromFile(archive))
            attachments.forEach { it.delete() }
            CloudBackupManager.restoreFromUri(context, Uri.fromFile(archive))
            val wr = wet.listReports().single { it.id == w.id }
            val gr = general.listReports().single { it.id == g.id }
            val paths = listOf(wr.logoPath, wr.signatureImagePath, wr.photos.single().filePath, gr.logoPath, gr.signatureImagePath, gr.observations.single().photos.single().filePath, gr.observations.single().photos.single().annotatedPath)
            assertEquals(attachments.map { it.absolutePath }, paths)
            paths.forEach { assertTrue(File(it).isFile) }
        } finally { wet.deleteReport(w.id); general.deleteReport(g.id); archive.delete() }
    }

    @Test fun startupRecoveryPreservesPreviousDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val current = context.filesDir
        val previous = File(current.parentFile, "tarkastus_restore_previous")
        val marker = File(current, "startup-marker.txt")
        marker.writeText("original")
        try {
            assertFalse(previous.exists())
            assertTrue(current.renameTo(previous))
            CloudBackupManager.recoverInterrupted(context)
            assertEquals("original", marker.readText())
        } finally {
            if (!current.exists() && previous.exists()) previous.renameTo(current)
            marker.delete()
        }
    }
}

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
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.MAGENTA)
            file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
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
            paths.forEach {
                val bitmap = android.graphics.BitmapFactory.decodeFile(it)
                assertNotNull(bitmap)
                assertEquals(android.graphics.Color.MAGENTA, bitmap.getPixel(0, 0))
                bitmap.recycle()
            }
            val pdfs = listOf(
                fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, wr, wet.loadSettings()),
                fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, gr, general.loadSettings())
            )
            pdfs.forEach { pdf ->
                try {
                    android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                            assertTrue(renderer.pageCount > 0)
                            var found = false
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val pixels = IntArray(bitmap.width * bitmap.height)
                                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                                    found = found || pixels.any { android.graphics.Color.red(it) > 200 && android.graphics.Color.blue(it) > 200 && android.graphics.Color.green(it) < 60 }
                                    bitmap.recycle()
                                }
                            }
                            assertTrue("Restored image not visible in PDF", found)
                        }
                    }
                } finally { pdf.delete() }
            }
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

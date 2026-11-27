package fi.vladi.valvontapoytakirja

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.vladi.valvontapoytakirja.cloud.CloudBackupManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CustomZipRoundTripTest {
    @Test fun restoreTemplateDocumentAndImagesThenExportPdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = CustomStore(context)
        val photo = CustomField(label = "Photo", kind = CustomKind.PHOTOS)
        val signature = CustomField(label = "Signature", kind = CustomKind.SIGNATURE)
        val template = CustomTemplate(title = "ZIP test", sections = listOf(CustomSection(fields = listOf(photo, signature))))
        fun image(color: Int): String {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try { bitmap.eraseColor(color); return store.saveBitmap(bitmap) } finally { bitmap.recycle() }
        }
        val a = image(Color.MAGENTA)
        val b = image(Color.GREEN)
        val report = CustomDocument(template = template, attachments = mapOf(photo.id to listOf(a), signature.id to listOf(b)), useSharedSettings = false)
        val zip = File.createTempFile("custom-roundtrip", ".zip", context.cacheDir)
        val pending = File(store.image(a).parentFile, customId() + ".image.pending")
        try {
            store.saveTemplate(template)
            store.saveDocument(report)
            pending.writeText("incomplete")
            CloudBackupManager.backupToDocument(context, Uri.fromFile(zip))
            java.util.zip.ZipFile(zip).use { archive ->
                assertNull(archive.getEntry("files/custom_reports/" + pending.name))
            }
            store.deleteDocument(report.id)
            store.deleteTemplate(template.id)
            store.image(a).delete()
            store.image(b).delete()
            CloudBackupManager.restoreFromUri(context, Uri.fromFile(zip))
            val restored = CustomStore(context)
            assertEquals(template, restored.load().templates.single { it.id == template.id })
            val doc = restored.load().documents.single { it.id == report.id }
            assertEquals(report, doc)
            for ((name, color) in listOf(a to Color.MAGENTA, b to Color.GREEN)) {
                val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeFile(restored.image(name).path))
                try { assertEquals(color, bitmap.getPixel(0,0)) } finally { bitmap.recycle() }
            }
            val uri = exportCustomPdf(context, restored, doc)
            try {
                context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { renderer -> assertTrue(renderer.pageCount > 0) }
                }
            } finally { context.contentResolver.delete(uri, null, null) }
        } finally {
            store.deleteDocument(report.id)
            store.deleteTemplate(template.id)
            store.image(a).delete()
            store.image(b).delete()
            pending.delete()
            zip.delete()
        }
    }
}

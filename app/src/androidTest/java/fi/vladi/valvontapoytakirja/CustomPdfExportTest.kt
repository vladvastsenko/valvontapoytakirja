package fi.vladi.valvontapoytakirja

import android.graphics.Bitmap
import android.graphics.Color
import fi.vladi.valvontapoytakirja.data.ReportRepository
import fi.vladi.valvontapoytakirja.model.AppSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class CustomPdfExportTest {
    @Test fun exportWithAttachmentsAndSettingsPreservesPreviousPdf() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val store = CustomStore(context)
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val image = store.saveBitmap(bitmap)
        bitmap.recycle()
        ReportRepository(context).saveSettings(AppSettings(
            logoPath = store.image(image).absolutePath,
            signatureImagePath = store.image(image).absolutePath,
            headerText = "Company header", footerText = "Company footer", inspectorName = "Inspector"
        ))
        val photo = CustomField(label = "Photo", kind = CustomKind.PHOTOS)
        val signature = CustomField(label = "Signature", kind = CustomKind.SIGNATURE)
        val report = CustomDocument(
            template = CustomTemplate(title = "Export test", sections = listOf(CustomSection(fields = listOf(photo, signature)))),
            attachments = mapOf(photo.id to listOf(image), signature.id to listOf(image)),
            customerText = "Customer", siteText = "Site", pdfLanguage = "en", useSharedSettings = true
        )
        store.saveDocument(report)
        val first = exportCustomPdf(context, store, report)
        fun read(uri: android.net.Uri): ByteArray = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
        val bytes = read(first)
        assertTrue(bytes.size > 100)
        assertEquals("%PDF-", bytes.copyOfRange(0,5).decodeToString())
        val second = exportCustomPdf(context, store, report.copy(useSharedSettings = false))
        assertNotEquals(first, second)
        assertFalse(bytes.contentEquals(read(second)))
        assertArrayEquals(bytes, read(first))
        assertEquals("%PDF-", read(second).copyOfRange(0,5).decodeToString())
    }
}

package fi.vladi.valvontapoytakirja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfLanguageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun checkPdf(file: File) {
        try {
            assertTrue(file.length() > 0)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> assertTrue(renderer.pageCount > 0) }
            }
        } finally { file.delete() }
    }
    @Test fun wet_fi() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "fi", siteAddress = "Test-wet-fi"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_fi() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "fi", siteAddress = "Test-general-fi")))
    @Test fun wet_en() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "en", siteAddress = "Test-wet-en"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_en() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "en", siteAddress = "Test-general-en")))
    @Test fun wet_sv() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "sv", siteAddress = "Test-wet-sv"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_sv() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "sv", siteAddress = "Test-general-sv")))
    @Test fun wet_nb() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "nb", siteAddress = "Test-wet-nb"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_nb() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "nb", siteAddress = "Test-general-nb")))
    @Test fun wet_da() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "da", siteAddress = "Test-wet-da"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_da() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "da", siteAddress = "Test-general-da")))
    @Test fun wet_et() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "et", siteAddress = "Test-wet-et"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_et() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "et", siteAddress = "Test-general-et")))
    @Test fun wet_lv() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "lv", siteAddress = "Test-wet-lv"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_lv() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "lv", siteAddress = "Test-general-lv")))
    @Test fun wet_lt() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "lt", siteAddress = "Test-wet-lt"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_lt() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "lt", siteAddress = "Test-general-lt")))
    @Test fun wet_ru() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "ru", siteAddress = "Test-wet-ru"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_ru() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "ru", siteAddress = "Test-general-ru")))
    @Test fun wet_fr() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "fr", siteAddress = "Test-wet-fr"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_fr() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "fr", siteAddress = "Test-general-fr")))
    @Test fun wet_es() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "es", siteAddress = "Test-wet-es"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_es() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "es", siteAddress = "Test-general-es")))
    @Test fun wet_it() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "it", siteAddress = "Test-wet-it"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_it() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "it", siteAddress = "Test-general-it")))
    @Test fun wet_de() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "de", siteAddress = "Test-wet-de"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_de() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "de", siteAddress = "Test-general-de")))
    @Test fun wet_pl() = checkPdf(fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.valvontapoytakirja.model.InspectionReport(pdfLanguage = "pl", siteAddress = "Test-wet-pl"), fi.vladi.valvontapoytakirja.model.AppSettings()))
    @Test fun general_pl() = checkPdf(fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator.generate(context, fi.vladi.tarkastuspoytakirja.model.InspectionReport(pdfLanguage = "pl", siteAddress = "Test-general-pl")))
}

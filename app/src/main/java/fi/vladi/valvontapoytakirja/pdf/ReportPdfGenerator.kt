package fi.vladi.valvontapoytakirja.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import fi.vladi.valvontapoytakirja.model.InspectionReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportPdfGenerator {
    fun generate(context: Context, report: InspectionReport): File {
        val document = PdfDocument()
        val writer = PdfWriter(document)

        writer.title("VALVONTAPÖYTÄKIRJA")
        writer.field("Kohde", report.siteAddress)
        writer.field("Tilaaja / kohteen edustaja", report.clientOrRepresentative)
        writer.field("Tilaajan sähköposti", report.clientEmail)
        writer.field("Vedeneristäjän nimi", report.installerName)
        writer.field("Sertifikaattinumero", report.installerCertificateNumber)
        writer.field("Puhelin", report.installerPhone)
        writer.field("Sähköposti", report.installerEmail)
        writer.field("Asennustyön ajankohta", "${report.workStartDate} – ${report.workEndDate}")

        writer.section("ALUSTAT JA TYÖOLOSUHTEET")
        writer.field("Seinien alusta", report.wallSubstrate)
        writer.field("Lattian alusta", report.floorSubstrate)
        writer.field("Työohjeet annettu ja luettu", if (report.workInstructionsProvidedAndRead) "Kyllä" else "Ei")
        writer.field("Lämpötila (°C)", report.roomTemperatureC)
        writer.field("Suhteellinen kosteus (%)", report.relativeHumidityPercent)
        writer.field("Alustan kosteus (%)", report.substrateMoisturePercent)

        writer.section("KÄYTETYT MATERIAALIT")
        report.materials.forEach { material ->
            val product = listOf(material.manufacturer, material.productName).filter { it.isNotBlank() }.joinToString(" – ")
            writer.field(material.category, listOf(product, material.notes).filter { it.isNotBlank() }.joinToString(" | "))
        }

        writer.section("RAKENTEET JA MITAT")
        writer.field("Lattialämmitys", report.floorHeating)
        writer.field("Kynnys / viemärin nosto (mm)", report.thresholdOrDrainLiftMm)
        writer.field("Lattian kaadot", report.floorSlopes)

        writer.section("PINTA-ALAT JA VEDENERISTEEN MENEKKI")
        writer.field("Seinien pinta-ala (m²)", report.wallAreaM2)
        writer.field("Seinien vedeneristeen menekki (l)", report.wallWaterproofingConsumptionLitres)
        writer.field("Lattian pinta-ala (m²)", report.floorAreaM2)
        writer.field("Lattian vedeneristeen menekki (l)", report.floorWaterproofingConsumptionLitres)

        writer.section("KUIVAKALVON PAKSUUS")
        writer.field("Seinät – mitattu paksuus (mm)", report.wallDryFilmThicknessMm)
        writer.field("Seinät – vaadittu vähimmäispaksuus (mm)", report.wallRequiredMinimumThicknessMm)
        writer.field("Lattia – mitattu paksuus (mm)", report.floorDryFilmThicknessMm)
        writer.field("Lattia – vaadittu vähimmäispaksuus (mm)", report.floorRequiredMinimumThicknessMm)
        writer.field("Lattiakaivon alue – mitattu paksuus (mm)", report.drainZoneDryFilmThicknessMm)
        writer.field("Lattiakaivon alue – vaadittu vähimmäispaksuus (mm)", report.drainZoneRequiredMinimumThicknessMm)

        writer.section("TARKASTUKSEN TULOS")
        writer.field("Tulos", report.inspectionResult)
        writer.field("Lisähuomiot", report.additionalNotes)

        writer.section("TARKASTAJA")
        writer.field("Nimi", report.inspectorName)
        writer.field("Sertifikaattinumero", report.inspectorCertificateNumber)
        writer.field("Puhelin", report.inspectorPhone)
        writer.field("Sähköposti", report.inspectorEmail)
        writer.field("Tarkastuspäivä", report.inspectionDate)

        if (report.photos.isNotEmpty()) {
            writer.section("VALOKUVAT")
            report.photos.forEachIndexed { index, photo ->
                writer.photo(index + 1, photo.filePath, photo.caption)
            }
        }

        writer.finish()
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = report.siteAddress
            .replace(Regex("[^A-Za-z0-9ÄÖÅäöå_-]+"), "_")
            .trim('_')
            .ifBlank { "kohde" }
        val output = File(exportDirectory, "Valvontapoytakirja_$safeName.pdf")
        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
        return output
    }

    private class PdfWriter(private val document: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 42f
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(40, 45, 50)
            textSize = 10.5f
        }
        private val boldPaint = Paint(bodyPaint).apply { typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
        private val titlePaint = Paint(boldPaint).apply { textSize = 20f; color = android.graphics.Color.rgb(20, 35, 48) }
        private val sectionPaint = Paint(boldPaint).apply { textSize = 13f; color = android.graphics.Color.rgb(35, 75, 95) }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private var pageNumber = 0
        private var page: PdfDocument.Page
        private var y = 0f

        init {
            page = startPage()
        }

        private fun startPage(): PdfDocument.Page {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val newPage = document.startPage(info)
            newPage.canvas.drawText("VLADI MTV  •  VALVONTAPÖYTÄKIRJA", margin, 24f, Paint(boldPaint).apply { textSize = 8f; color = android.graphics.Color.GRAY })
            y = 52f
            return newPage
        }

        private fun nextPage() {
            document.finishPage(page)
            page = startPage()
        }

        private fun ensureSpace(height: Float) {
            if (y + height > pageHeight - 45f) nextPage()
        }

        fun title(value: String) {
            ensureSpace(38f)
            page.canvas.drawText(value, margin, y + 17f, titlePaint)
            y += 32f
            page.canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply {
                color = android.graphics.Color.rgb(170, 185, 195)
                strokeWidth = 1f
            })
            y += 13f
        }

        fun section(value: String) {
            ensureSpace(30f)
            y += 7f
            page.canvas.drawText(value, margin, y + 12f, sectionPaint)
            y += 22f
        }

        fun field(label: String, value: String) {
            val text = "$label: ${value.ifBlank { "—" }}"
            drawWrapped(text, bodyPaint, 14f)
        }

        private fun drawWrapped(value: String, paint: Paint, lineHeight: Float) {
            val maxWidth = pageWidth - margin * 2
            val words = value.replace("\n", " ").split(Regex("\\s+"))
            val lines = mutableListOf<String>()
            var line = ""
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    lines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) lines += line
            lines.forEach { lineText ->
                ensureSpace(lineHeight + 2f)
                page.canvas.drawText(lineText, margin, y + lineHeight, paint)
                y += lineHeight
            }
        }

        fun photo(number: Int, path: String, caption: String) {
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                field("Kuva $number", "Kuvaa ei voitu lukea: $path")
                return
            }
            val boxWidth = 240f
            val boxHeight = 150f
            ensureSpace(boxHeight + 35f)
            val left = margin
            val top = y + 4f
            val ratio = minOf(boxWidth / bitmap.width, boxHeight / bitmap.height)
            val drawWidth = bitmap.width * ratio
            val drawHeight = bitmap.height * ratio
            val rect = RectF(
                left + (boxWidth - drawWidth) / 2f,
                top + (boxHeight - drawHeight) / 2f,
                left + (boxWidth + drawWidth) / 2f,
                top + (boxHeight + drawHeight) / 2f
            )
            page.canvas.drawBitmap(bitmap, null, rect, imagePaint)
            page.canvas.drawRect(left, top, left + boxWidth, top + boxHeight, Paint().apply {
                style = Paint.Style.STROKE
                color = android.graphics.Color.LTGRAY
                strokeWidth = 0.7f
            })
            y += boxHeight + 5f
            drawWrapped("Kuva $number: ${caption.ifBlank { "Ei kuvatekstiä" }}", bodyPaint, 13f)
            y += 10f
            bitmap.recycle()
        }

        fun finish() {
            document.finishPage(page)
        }
    }
}

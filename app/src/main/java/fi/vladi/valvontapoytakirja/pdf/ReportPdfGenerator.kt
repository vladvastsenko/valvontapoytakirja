package fi.vladi.valvontapoytakirja.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import fi.vladi.valvontapoytakirja.model.AppSettings
import fi.vladi.valvontapoytakirja.model.InspectionReport
import fi.vladi.valvontapoytakirja.model.ReportPhoto
import fi.vladi.valvontapoytakirja.model.SignatureStroke
import java.io.File
import java.io.FileOutputStream

object ReportPdfGenerator {
    fun generate(context: Context, report: InspectionReport, settings: AppSettings): File {
        val selectedLanguage = report.pdfLanguage.ifBlank { fi.vladi.tarkastuspoytakirja.AppLanguage.selected(context) }
        val firstPass = render(report, settings, totalPages = null, language = selectedLanguage)
        val totalPages = firstPass.second
        firstPass.first.close()

        val finalPass = render(report, settings, totalPages, language = selectedLanguage)
        val document = finalPass.first
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(exportDirectory, PdfFileNames.name(report.pdfLanguage.ifBlank { selectedLanguage }, "valvonta", report.siteAddress))
        try {
            FileOutputStream(output).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return output
    }

    private fun render(
        report: InspectionReport,
        settings: AppSettings,
        totalPages: Int?,
        language: String
    ): Pair<PdfDocument, Int> {
        val document = PdfDocument()
        val writer = PdfWriter(document, settings.logoPath, settings.headerText, settings.footerText, totalPages, language)

            writer.title("VALVONTAPÖYTÄKIRJA")
            if (report.folderPath.isNotBlank()) {
                writer.field("Kansio / alikansio", report.folderPath)
            }
            writer.field("Tilaaja", report.customerName)
            writer.field("Y-tunnus", report.customerBusinessId)
            writer.field("Tilaajan osoite", listOf(report.customerAddress, report.customerPostalCode, report.customerCity).filter { it.isNotBlank() }.joinToString(", "))
            writer.field("Kohde", listOf(report.siteName, report.siteAddress).filter { it.isNotBlank() }.joinToString(" – "))
            report.siteFieldValues.filter { it.label.isNotBlank() && it.value.isNotBlank() }.forEach { writer.field(it.label, it.value) }
            writer.field("Tilaaja / kohteen edustaja", report.clientOrRepresentative)
            writer.field("Tilaajan puhelinnumero", report.clientPhone)
            writer.field("Tilaajan sähköposti", report.clientEmail)
            writer.field("Vedeneristäjän nimi", report.installerName)
            writer.field("Sertifikaattinumero", report.installerCertificateNumber)
            writer.field("Puhelin", report.installerPhone)
            writer.field("Sähköposti", report.installerEmail)
            writer.field(
                "Asennustyön ajankohta",
                listOf(report.workStartDate, report.workEndDate)
                    .filter { it.isNotBlank() }
                    .joinToString(" – ")
            )
    
            writer.section("ALUSTAT JA TYÖOLOSUHTEET")
            writer.field("Seinien alusta", report.wallSubstrate)
            writer.field("Lattian alusta", report.floorSubstrate)
            writer.field("Työohjeet annettu ja luettu", if (report.workInstructionsProvidedAndRead) "Kyllä" else "Ei")
            writer.field("Lämpötila (°C)", report.roomTemperatureC)
            writer.field("Suhteellinen kosteus (%)", report.relativeHumidityPercent)
            writer.field("Alustan kosteus (%)", report.substrateMoisturePercent)
    
            writer.section("KÄYTETYT MATERIAALIT")
            report.materials.forEach { material ->
                val product = listOf(material.manufacturer, ProductDisplay.name(material.productName, language))
                    .filter { it.isNotBlank() }
                    .joinToString(" – ")
                val localizer = PdfLabels(language)
                val label = if (material.surface.isNotBlank()) "${localizer.text(material.category)} – ${localizer.text(material.surface)}" else localizer.text(material.category)
                val specs = listOf(
                    material.consumptionPerM2.takeIf { it.isNotBlank() }?.let { "${localizer.text("Menekki")}: $it" },
                    material.dryFilmThicknessMm.takeIf { it.isNotBlank() }?.let { "${localizer.text("Kuivakalvo")}: $it mm" }
                ).filterNotNull()
                writer.field(label, (listOf(product, material.notes) + specs).filter { it.isNotBlank() }.joinToString(" | "))
            }
    
            writer.section("RAKENTEET JA MITAT")
            writer.field("Lattialämmitys", report.floorHeating)
            writer.field("Kynnys / viemärin nosto (mm)", report.thresholdOrDrainLiftMm)
            writer.field(
                "Lattian kaadot",
                listOf(
                    report.floorSlopeRatios.joinToString(", "),
                    report.floorSlopes
                ).filter { it.isNotBlank() }.joinToString(" – ")
            )
    
            writer.section("PINTA-ALAT JA VEDENERISTEEN MENEKKI")
            writer.field("Seinien pinta-ala (m²)", report.wallAreaM2)
            writer.field("Seinät – valmistajan vaatima menekki (kg/m² tai l/m²)", report.wallRequiredWaterproofingConsumptionPerM2)
            writer.field("Seinät – käytetty kokonaismäärä (kg tai l)", report.wallWaterproofingConsumptionLitres)
            writer.field("Lattian pinta-ala (m²)", report.floorAreaM2)
            writer.field("Lattia – valmistajan vaatima menekki (kg/m² tai l/m²)", report.floorRequiredWaterproofingConsumptionPerM2)
            writer.field("Lattia – käytetty kokonaismäärä (kg tai l)", report.floorWaterproofingConsumptionLitres)
            if (report.waterproofingSpecSource.isNotBlank()) {
                writer.field("Valmistajan tuotetietojen lähde", report.waterproofingSpecSource)
            }
    
            writer.section("KUIVAKALVON PAKSUUS")
            writer.field("Seinät – mitattu kuivakalvon paksuus (mm)", report.wallDryFilmThicknessMm)
            writer.field("Seinät – valmistajan vaatima kuivakalvon vähimmäispaksuus (mm)", report.wallRequiredMinimumThicknessMm)
            writer.field("Lattia – mitattu kuivakalvon paksuus (mm)", report.floorDryFilmThicknessMm)
            writer.field("Lattia – valmistajan vaatima kuivakalvon vähimmäispaksuus (mm)", report.floorRequiredMinimumThicknessMm)
    
            writer.section("TARKASTUKSEN TULOS")
            writer.field("Tulos", PdfLabels(language).text(report.inspectionResult))
            writer.field("Lisähuomiot", report.additionalNotes)
    
            // New reports carry the chosen inspector's own snapshot. Legacy reports
            // without a snapshot continue to use the original settings fallback.
            val inspectorName = if (report.settingsSnapshotCreated) report.inspectorName else report.inspectorName.ifBlank { settings.inspectorName }
            val inspectorCertificate = if (report.settingsSnapshotCreated) report.inspectorCertificateNumber else report.inspectorCertificateNumber.ifBlank { settings.inspectorCertificateNumber }
            val inspectorPhone = if (report.settingsSnapshotCreated) report.inspectorPhone else report.inspectorPhone.ifBlank { settings.inspectorPhone }
            val inspectorEmail = if (report.settingsSnapshotCreated) report.inspectorEmail else report.inspectorEmail.ifBlank { settings.inspectorEmail }
            val inspectorStrokes = if (report.settingsSnapshotCreated) report.signatureStrokes else report.signatureStrokes.ifEmpty { settings.signatureStrokes }
            val inspectorImage = if (report.settingsSnapshotCreated) report.signatureImagePath else report.signatureImagePath.ifBlank { settings.signatureImagePath }
            val hasInspectorDetails = inspectorName.isNotBlank() ||
                inspectorCertificate.isNotBlank() || inspectorPhone.isNotBlank() ||
                inspectorEmail.isNotBlank() || report.inspectionDate.isNotBlank() ||
                inspectorStrokes.isNotEmpty() || inspectorImage.isNotBlank()
            if (hasInspectorDetails) {
                writer.spacer(28f)
                writer.section("TARKASTAJA")
                if (inspectorName.isNotBlank()) writer.field("Nimi", inspectorName)
                if (inspectorCertificate.isNotBlank()) writer.field("Sertifikaattinumero", inspectorCertificate)
                if (inspectorPhone.isNotBlank()) writer.field("Puhelin", inspectorPhone)
                if (inspectorEmail.isNotBlank()) writer.field("Sähköposti", inspectorEmail)
                if (report.inspectionDate.isNotBlank()) writer.field("Tarkastuspäivä", report.inspectionDate)
                writer.signature(inspectorStrokes, inspectorImage)
            }
    
            if (report.photos.isNotEmpty()) {
                writer.photos(report.photos)
            }
    
            writer.finish()
        return document to writer.pageCount
    }

    private class PdfWriter(
        private val document: PdfDocument,
        private val logoPath: String,
        private val headerText: String,
        private val footerText: String,
        private val totalPages: Int?,
        language: String
    ) {
        private val labels = PdfLabels(language)
        val pageCount: Int get() = pageNumber
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 42f
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 45, 50)
            textSize = 10.5f
        }
        private val boldPaint = Paint(bodyPaint).apply {
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        private val titlePaint = Paint(boldPaint).apply {
            textSize = 20f
            color = Color.rgb(20, 35, 48)
        }
        private val sectionPaint = Paint(boldPaint).apply {
            textSize = 13f
            color = Color.rgb(35, 75, 95)
        }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private var pageNumber = 0
        private var page: PdfDocument.Page
        private var y = 0f

        init {
            page = startPage()
            drawFirstPageLogo()
        }

        private fun startPage(): PdfDocument.Page {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val newPage = document.startPage(info)
            y = 42f
            if (headerText.isNotBlank()) {
                val headerPaint = Paint(bodyPaint).apply { textSize = 8.5f; color = Color.DKGRAY }
                val headerWidth = if (logoPath.isNotBlank() && pageNumber == 1) pageWidth / 2f - margin else pageWidth - margin * 2
                val lines = wrapCaption(headerText, headerPaint, headerWidth).take(1)
                lines.forEach { line ->
                    val x = if (logoPath.isNotBlank() && pageNumber == 1) pageWidth - margin - headerPaint.measureText(line) else margin
                    newPage.canvas.drawText(line, x, 27f, headerPaint)
                }
                y = 48f
            }
            return newPage
        }

        private fun drawFirstPageLogo() {
            if (logoPath.isBlank()) return
            val bitmap = BitmapFactory.decodeFile(logoPath) ?: return
            val maxWidth = 170f
            val maxHeight = 58f
            val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val destination = RectF(
                margin,
                22f,
                margin + width,
                22f + height
            )
            page.canvas.drawBitmap(bitmap, null, destination, imagePaint)
            y = maxOf(y, 92f)
            bitmap.recycle()
        }

        private fun finishCurrentPage() {
            val footerPaint = Paint(bodyPaint).apply {
                textSize = 8.5f
                color = Color.DKGRAY
            }
            page.canvas.drawLine(
                margin,
                pageHeight - 34f,
                pageWidth - margin,
                pageHeight - 34f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    strokeWidth = 0.6f
                }
            )
            if (footerText.isNotBlank()) {
                val lines = wrapCaption(footerText, footerPaint, pageWidth - margin * 2 - 70f).take(2)
                lines.forEachIndexed { index, line ->
                    page.canvas.drawText(line, margin, pageHeight - 18f + index * 10f, footerPaint)
                }
            }
            val pageLabel = "$pageNumber/${totalPages ?: pageNumber}"
            page.canvas.drawText(
                pageLabel,
                pageWidth - margin - footerPaint.measureText(pageLabel),
                pageHeight - 18f,
                footerPaint
            )
            document.finishPage(page)
        }

        private fun nextPage() {
            finishCurrentPage()
            page = startPage()
        }

        private fun ensureSpace(height: Float) {
            if (y + height > pageHeight - 45f) nextPage()
        }

        fun title(value: String) {
            ensureSpace(38f)
            page.canvas.drawText(labels.text(value), margin, y + 17f, titlePaint)
            y += 32f
            page.canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply {
                color = Color.rgb(170, 185, 195)
                strokeWidth = 1f
            })
            y += 13f
        }

        fun spacer(height: Float) {
            ensureSpace(height)
            y += height
        }

        fun section(value: String) {
            // Reserve the heading and the first content line together.
            // Add vertical breathing room only within the current page.
            ensureSpace(48f)
            y += 18f
            page.canvas.drawText(labels.text(value), margin, y + 12f, sectionPaint)
            y += 22f
        }

        fun field(label: String, value: String) {
            if (label.isBlank() || value.isBlank()) return
            drawWrapped("${labels.text(label)}: $value", bodyPaint, 14f)
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

        fun signature(strokes: List<SignatureStroke>, imagePath: String = "") {
            if (strokes.isEmpty() && imagePath.isBlank()) return
            ensureSpace(125f)

            page.canvas.drawText(labels.text("Allekirjoitus"), margin, y + 12f, boldPaint)
            val bitmap = Bitmap.createBitmap(1800, 540, Bitmap.Config.ARGB_8888)
            val signatureCanvas = Canvas(bitmap)
            val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 15f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            if (imagePath.isNotBlank()) {
                val imported = SignatureImage.prepare(imagePath)
                if (imported != null) {
                    val target = RectF(margin, y + 20f, margin + 320f, y + 115f)
                    val scale = minOf(target.width() / imported.width, target.height() / imported.height)
                    val width = imported.width * scale
                    val height = imported.height * scale
                    val destination = RectF(target.left, target.top, target.left + width, target.top + height)
                    page.canvas.drawBitmap(imported, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    imported.recycle()
                    y += 120f
                    return
                }
            }
            strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val path = Path()
                    stroke.points.forEachIndexed { index, point ->
                        val px = point.x.coerceIn(0f, 1f) * bitmap.width
                        val py = point.y.coerceIn(0f, 1f) * bitmap.height
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    if (stroke.points.size == 1) {
                        val point = stroke.points.first()
                        signatureCanvas.drawCircle(
                            point.x.coerceIn(0f, 1f) * bitmap.width,
                            point.y.coerceIn(0f, 1f) * bitmap.height,
                            8f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
                        )
                    } else {
                        signatureCanvas.drawPath(path, signaturePaint)
                    }
                }
            }

            // The bitmap stays transparent: no grey or coloured signature background.
            page.canvas.drawBitmap(
                bitmap,
                null,
                RectF(margin, y + 18f, margin + 230f, y + 87f),
                imagePaint
            )
            y += 96f
            bitmap.recycle()
        }

        fun photos(photos: List<ReportPhoto>) {
            photos.chunked(9).forEach { pagePhotos ->
                nextPage()

                // Always reserve the same 3 x 3 layout, regardless of the number of photos.
                val columns = 3
                val rows = 3
                val gap = 8f
                val topStart = 40f
                val bottomReserved = 42f
                val cellWidth = (pageWidth - margin * 2 - gap * (columns - 1)) / columns.toFloat()
                val cellHeight = (pageHeight - topStart - bottomReserved - gap * (rows - 1)) / rows.toFloat()

                pagePhotos.forEachIndexed { index, photo ->
                    val column = index % columns
                    val row = index / columns
                    val left = margin + column * (cellWidth + gap)
                    val top = topStart + row * (cellHeight + gap)
                    drawPhotoCell(photo, left, top, cellWidth, cellHeight)
                }
            }
        }

        private fun drawPhotoCell(
            photo: ReportPhoto,
            left: Float,
            top: Float,
            cellWidth: Float,
            cellHeight: Float
        ) {
            val canvas = page.canvas
            // Reserve identical caption space for every cell so image areas stay equal-sized.
            val captionSpace = 25f
            val imageRect = RectF(left, top, left + cellWidth, top + cellHeight - captionSpace)
            val bitmap = BitmapFactory.decodeFile(photo.filePath)
            if (bitmap != null) {
                val ratio = minOf(imageRect.width() / bitmap.width, imageRect.height() / bitmap.height)
                val drawWidth = bitmap.width * ratio
                val drawHeight = bitmap.height * ratio
                val destination = RectF(
                    imageRect.centerX() - drawWidth / 2f,
                    imageRect.centerY() - drawHeight / 2f,
                    imageRect.centerX() + drawWidth / 2f,
                    imageRect.centerY() + drawHeight / 2f
                )
                canvas.drawBitmap(bitmap, null, destination, imagePaint)
                bitmap.recycle()
            }

            if (photo.caption.isNotBlank()) {
                val captionPaint = Paint(bodyPaint).apply { textSize = 9.5f }
                val lines = wrapCaption(photo.caption, captionPaint, cellWidth).take(2)
                lines.forEachIndexed { lineIndex, line ->
                    canvas.drawText(line, left, top + cellHeight - 15f + lineIndex * 10f, captionPaint)
                }
            }
        }

        private fun wrapCaption(value: String, paint: Paint, maxWidth: Float): List<String> {
            val lines = mutableListOf<String>()
            var line = ""
            value.replace("\n", " ").split(Regex("\\s+")).forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    lines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines
        }

        fun finish() {
            finishCurrentPage()
        }
    }
}

package fi.vladi.tarkastuspoytakirja.pdf

import fi.vladi.tarkastuspoytakirja.storage.PublicArchive
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import fi.vladi.tarkastuspoytakirja.model.AppSettings
import fi.vladi.tarkastuspoytakirja.model.InspectionObservation
import fi.vladi.tarkastuspoytakirja.model.InspectionReport
import fi.vladi.tarkastuspoytakirja.model.ReportPhoto
import fi.vladi.tarkastuspoytakirja.model.SignatureStroke
import java.io.File
import java.io.FileOutputStream

object ReportPdfGenerator {
    fun generate(context: Context, report: InspectionReport, settings: AppSettings = AppSettings()): File {
        val firstPass = render(report, settings, totalPages = null)
        val totalPages = firstPass.second
        firstPass.first.close()

        val finalPass = render(report, settings, totalPages)
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = report.siteAddress
            .replace(Regex("[^A-Za-z0-9ÄÖÅäöå_-]+"), "_")
            .trim('_')
            .ifBlank { "kohde" }
        val output = File(exportDirectory, "tarkastuspöytäkirja_${safeName}.pdf")
        FileOutputStream(output).use { finalPass.first.writeTo(it) }
        finalPass.first.close()
        PublicArchive.copyPdf(context, output)
        return output
    }

    private fun render(report: InspectionReport, settings: AppSettings, totalPages: Int?): Pair<PdfDocument, Int> {
        val document = PdfDocument()
        val logoPath = if (report.settingsSnapshotCreated) report.logoPath else report.logoPath.ifBlank { settings.logoPath }
        val headerText = if (report.settingsSnapshotCreated) report.headerText else report.headerText.ifBlank { settings.headerText }
        val footerText = if (report.settingsSnapshotCreated) report.footerText else report.footerText.ifBlank { settings.footerText }
        val writer = PdfWriter(document, logoPath, headerText, footerText, totalPages)
        writer.title("TARKASTUSPÖYTÄKIRJA")
        if (report.customerName.isNotBlank()) writer.field("Tilaaja", report.customerName)
        if (report.customerBusinessId.isNotBlank()) writer.field("Y-tunnus", report.customerBusinessId)
        if (report.customerAddress.isNotBlank() || report.customerPostalCode.isNotBlank() || report.customerCity.isNotBlank()) {
            writer.field("Tilaajan osoite", listOf(report.customerAddress, report.customerPostalCode, report.customerCity).filter { it.isNotBlank() }.joinToString(", "))
        }
        writer.field("Kohde", listOf(report.siteName, report.siteAddress).filter { it.isNotBlank() }.joinToString(" – "))
        writer.field("Yhteyshenkilö", report.clientOrRepresentative)
        writer.field("Tilaajan puhelinnumero", report.clientPhone)
        writer.field("Tilaajan sähköposti", report.clientEmail)

        if (report.purposeAndScope.isNotBlank()) {
            writer.section("TARKASTUKSEN TARKOITUS JA LAAJUUS")
            writer.field("Tarkastuksen tarkoitus ja laajuus", report.purposeAndScope)
        }
        if (report.generalObservations.isNotBlank()) {
            writer.section("YLEISET HUOMIOT")
            writer.field("Yleiset huomiot", report.generalObservations)
        }
        val filledCustomFields = report.customFields.filter { it.label.isNotBlank() && it.value.isNotBlank() }
        if (filledCustomFields.isNotEmpty()) {
            if (report.customFieldsTitle.isNotBlank()) writer.section(report.customFieldsTitle.trim())
            filledCustomFields.forEach { field -> writer.field(field.label, field.value) }
        }
        val filledObservations = report.observations.filter {
            it.location.isNotBlank() || it.description.isNotBlank() || it.recommendedAction.isNotBlank() || it.photos.isNotEmpty()
        }
        if (filledObservations.isNotEmpty()) {
            writer.section("HAVAITUT PUUTTEET JA VIRHEET")
            filledObservations.forEachIndexed { index, observation -> writer.observation(observation, index + 1) }
        }
        if (report.summary.isNotBlank()) {
            writer.section("YHTEENVETO")
            writer.field("Johtopäätökset ja tarvittavat toimenpiteet", report.summary)
        }

        val inspectorName = if (report.settingsSnapshotCreated) report.inspectorName else report.inspectorName.ifBlank { settings.inspectorName }
        val inspectorCertificate = if (report.settingsSnapshotCreated) report.inspectorCertificateNumber else report.inspectorCertificateNumber.ifBlank { settings.inspectorCertificateNumber }
        val inspectorPhone = if (report.settingsSnapshotCreated) report.inspectorPhone else report.inspectorPhone.ifBlank { settings.inspectorPhone }
        val inspectorEmail = if (report.settingsSnapshotCreated) report.inspectorEmail else report.inspectorEmail.ifBlank { settings.inspectorEmail }
        val signature = if (report.settingsSnapshotCreated) report.signatureStrokes else report.signatureStrokes.ifEmpty { settings.signatureStrokes }
        if (inspectorName.isNotBlank() || inspectorCertificate.isNotBlank() || inspectorPhone.isNotBlank() || inspectorEmail.isNotBlank() || report.inspectionDate.isNotBlank() || signature.isNotEmpty()) {
            writer.spacer(18f)
            writer.section("TARKASTAJA")
            writer.field("Nimi", inspectorName)
            writer.field("Sertifikaattinumero", inspectorCertificate)
            writer.field("Puhelin", inspectorPhone)
            writer.field("Sähköposti", inspectorEmail)
            writer.field("Tarkastuspäivä", report.inspectionDate)
            writer.signature(signature)
        }
        writer.finish()
        return document to writer.pageCount
    }
    private class PdfWriter(
        private val document: PdfDocument,
        private val logoPath: String,
        private val headerText: String,
        private val footerText: String,
        private val totalPages: Int?
    ) {
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
                    val x = if (logoPath.isNotBlank() && pageNumber == 1) pageWidth - margin - headerPaint.measureText(line)
                        else margin
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
            val destination = RectF(margin, 22f, margin + width, 22f + height)
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
                margin, pageHeight - 34f, pageWidth - margin, pageHeight - 34f,
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
            page.canvas.drawText(value, margin, y + 17f, titlePaint)
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
            ensureSpace(30f)
            y += 7f
            page.canvas.drawText(value, margin, y + 12f, sectionPaint)
            y += 22f
        }

        fun field(label: String, value: String) {
            if (value.isBlank()) return
            drawWrapped("$label: $value", bodyPaint, 14f)
        }

        fun observation(observation: InspectionObservation, number: Int) {
            section("HAVAINTO $number")
            field("Tila / sijainti", observation.location)
            field("Havaitun puutteen kuvaus", observation.description)
            field("Suositeltu korjaustoimenpide", observation.recommendedAction)
            if (observation.photos.isNotEmpty()) {
                var photoIndex = 0
                var photoPageIndex = 0
                while (photoIndex < observation.photos.size) {
                    val remaining = observation.photos.size - photoIndex
                    val columns = 2
                    val maxRowsPerPage = 2
                    val remainingRows = (remaining + columns - 1) / columns
                    val availableHeight = pageHeight - 45f - y - 25f - 10f
                    val rowsThatFitAtMinimumSize = kotlin.math.floor(
                        (availableHeight + 8f) / (205f + 8f)
                    ).toInt().coerceAtLeast(0).coerceAtMost(maxRowsPerPage)
                    if (rowsThatFitAtMinimumSize == 0) {
                        nextPage()
                        continue
                    }
                    val rowsOnThisPage = minOf(remainingRows, rowsThatFitAtMinimumSize, maxRowsPerPage)
                    val verticalGaps = (rowsOnThisPage - 1).coerceAtLeast(0) * 8f
                    val cellHeight = minOf(300f, (availableHeight - verticalGaps) / rowsOnThisPage)
                    val photosOnThisPage = minOf(remaining, rowsOnThisPage * columns)
                    val pagePhotos = observation.photos.subList(photoIndex, photoIndex + photosOnThisPage)
                    photoGridHeader(number, ++photoPageIndex, 0)
                    drawPhotoGrid(pagePhotos, cellHeight, columns)
                    photoIndex += photosOnThisPage
                    if (photoIndex < observation.photos.size) nextPage()
                }
            }
            spacer(8f)
        }

        private fun photoGridHeader(observationNumber: Int, pageIndex: Int, pageCount: Int) {
            val suffix = if (pageCount > 1) " ($pageIndex/$pageCount)" else ""
            page.canvas.drawText("HAVAINTO $observationNumber – VALOKUVAT$suffix", margin, y + 16f, sectionPaint)
            y += 25f
        }

        private fun drawPhotoGrid(photos: List<ReportPhoto>, cellHeight: Float, columns: Int) {
            val rows = (photos.size + columns - 1) / columns
            val horizontalGap = 8f
            val verticalGap = 8f
            val cellWidth = (pageWidth - margin * 2 - horizontalGap * (columns - 1)) / columns.toFloat()
            photos.forEachIndexed { index, photo ->
                val row = index / columns
                val column = index % columns
                val left = margin + column * (cellWidth + horizontalGap)
                val top = y + row * (cellHeight + verticalGap)
                drawObservationPhoto(photo, left, top, cellWidth, cellHeight)
            }
            y += rows * cellHeight + (rows - 1).coerceAtLeast(0) * verticalGap + 10f
        }

        private fun drawObservationPhoto(photo: ReportPhoto, left: Float, top: Float, width: Float, height: Float) {
            val captionHeight = if (photo.caption.isBlank()) 0f else 27f
            val imageRect = RectF(left, top, left + width, top + height - captionHeight)
            val displayPath = photo.annotatedPath.takeIf { it.isNotBlank() && File(it).exists() } ?: photo.filePath
            val bitmap = BitmapFactory.decodeFile(displayPath)
            if (bitmap != null) {
                val scale = minOf(imageRect.width() / bitmap.width, imageRect.height() / bitmap.height)
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val destination = RectF(
                    imageRect.centerX() - drawWidth / 2f,
                    imageRect.centerY() - drawHeight / 2f,
                    imageRect.centerX() + drawWidth / 2f,
                    imageRect.centerY() + drawHeight / 2f
                )
                page.canvas.drawBitmap(bitmap, null, destination, imagePaint)
                bitmap.recycle()
            }
            if (photo.caption.isNotBlank()) {
                val captionPaint = Paint(bodyPaint).apply { textSize = 8.5f }
                wrapCaption(photo.caption, captionPaint, width).take(2).forEachIndexed { index, line ->
                    page.canvas.drawText(line, left, top + height - 12f + index * 9f, captionPaint)
                }
            }
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

        fun signature(strokes: List<SignatureStroke>) {
            if (strokes.isEmpty()) return
            ensureSpace(100f)
            page.canvas.drawText("Allekirjoitus", margin, y + 12f, boldPaint)
            val bitmap = Bitmap.createBitmap(1800, 540, Bitmap.Config.ARGB_8888)
            val signatureCanvas = Canvas(bitmap)
            val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 15f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
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
            page.canvas.drawBitmap(
                bitmap, null,
                RectF(margin, y + 18f, margin + 230f, y + 87f),
                imagePaint
            )
            y += 96f
            bitmap.recycle()
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

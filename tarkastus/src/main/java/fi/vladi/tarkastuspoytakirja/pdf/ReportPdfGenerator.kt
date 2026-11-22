package fi.vladi.tarkastuspoytakirja.pdf

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
        val selectedLanguage = report.pdfLanguage.ifBlank { fi.vladi.tarkastuspoytakirja.AppLanguage.selected(context) }
        val firstPass = render(report, settings, totalPages = null, language = selectedLanguage)
        val totalPages = firstPass.second
        firstPass.first.close()

        val finalPass = render(report, settings, totalPages, language = selectedLanguage)
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(exportDirectory, PdfFileNames.name(report.pdfLanguage.ifBlank { selectedLanguage }, "tarkastus", report.siteAddress))
        FileOutputStream(output).use { finalPass.first.writeTo(it) }
        finalPass.first.close()
        return output
    }

    private fun render(report: InspectionReport, settings: AppSettings, totalPages: Int?, language: String): Pair<PdfDocument, Int> {
        val document = PdfDocument()
        val logoPath = if (report.settingsSnapshotCreated) report.logoPath else report.logoPath.ifBlank { settings.logoPath }
        val headerText = if (report.settingsSnapshotCreated) report.headerText else report.headerText.ifBlank { settings.headerText }
        val footerText = if (report.settingsSnapshotCreated) report.footerText else report.footerText.ifBlank { settings.footerText }
        val writer = PdfWriter(document, logoPath, headerText, footerText, totalPages, language)
        writer.title("TARKASTUSPÖYTÄKIRJA")
        if (report.customerName.isNotBlank()) writer.labeledField("Tilaaja", report.customerName)
        if (report.customerBusinessId.isNotBlank()) writer.labeledField("Y-tunnus", report.customerBusinessId)
        if (report.customerAddress.isNotBlank() || report.customerPostalCode.isNotBlank() || report.customerCity.isNotBlank()) {
            writer.labeledField("Tilaajan osoite", listOf(report.customerAddress, report.customerPostalCode, report.customerCity).filter { it.isNotBlank() }.joinToString(", "))
        }
        writer.labeledField("Kohde", listOf(report.siteName, report.siteAddress).filter { it.isNotBlank() }.joinToString(" – "))
        writer.labeledField("Yhteyshenkilö", report.clientOrRepresentative)
        writer.labeledField("Tilaajan puhelinnumero", report.clientPhone)
        writer.labeledField("Tilaajan sähköposti", report.clientEmail)

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
            if (report.customFieldsTitle.isNotBlank()) writer.section(if (report.customFieldsTitle.trim() == "OMAT LISÄKENTÄT") PdfLabels(language).text("OMAT LISÄKENTÄT") else report.customFieldsTitle.trim())
            filledCustomFields.forEach { field -> writer.field(field.label, field.value) }
        }
        val filledObservations = report.observations.filter {
            it.location.isNotBlank() || it.description.isNotBlank() || it.recommendedAction.isNotBlank() || it.photos.isNotEmpty()
        }
        if (filledObservations.isNotEmpty()) {
            writer.section("TARKASTUSKOHTEET")
            filledObservations.forEach { observation -> writer.observation(observation) }
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
        val signatureImagePath = if (report.settingsSnapshotCreated) report.signatureImagePath else report.signatureImagePath.ifBlank { settings.signatureImagePath }
        if (inspectorName.isNotBlank() || inspectorCertificate.isNotBlank() || inspectorPhone.isNotBlank() || inspectorEmail.isNotBlank() || report.inspectionDate.isNotBlank() || signature.isNotEmpty() || signatureImagePath.isNotBlank()) {
            writer.spacer(18f)
            writer.section("TARKASTAJA")
            writer.labeledField("Nimi", inspectorName)
            writer.labeledField("Sertifikaattinumero", inspectorCertificate)
            writer.labeledField("Puhelin", inspectorPhone)
            writer.labeledField("Sähköposti", inspectorEmail)
            writer.labeledField("Tarkastuspäivä", report.inspectionDate)
            writer.signature(signature, signatureImagePath)
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
            ensureSpace(30f)
            y += 7f
            page.canvas.drawText(labels.text(value), margin, y + 12f, sectionPaint)
            y += 22f
        }

        fun field(label: String, value: String) {
            if (value.isBlank()) return
            drawWrapped(value, bodyPaint, 14f)
        }

        fun labeledField(label: String, value: String) {
            if (value.isBlank()) return
            drawWrapped("${labels.text(label)}: $value", bodyPaint, 14f)
        }

        private fun highlightedField(label: String, value: String) {
            if (value.isBlank()) return
            val emphasisPaint = Paint(sectionPaint).apply { textSize = 13f }
            drawWrapped(value, emphasisPaint, 16f)
        }

        fun observation(observation: InspectionObservation) {
            highlightedField("Tila / sijainti", observation.location)
            field("Kuvaus / tilanne", observation.description)
            field("Toimenpiteet / lisätiedot", observation.recommendedAction)
            if (observation.photos.isNotEmpty()) {
                // Keep a clear three-line separation between the location/details and photos.
                spacer(42f)
                var photoIndex = 0
                while (photoIndex < observation.photos.size) {
                    val remaining = observation.photos.size - photoIndex
                    val columns = 2
                    val maxRowsPerPage = 2
                    val remainingRows = (remaining + columns - 1) / columns
                    val availableHeight = pageHeight - 45f - y - 25f - 10f
                    // Keep photo dimensions identical on every page. Use only as many
                    // complete rows as fit; never shrink photos to squeeze in four.
                    val cellHeight = 300f
                    val rowsThatFitAtFixedSize = kotlin.math.floor(
                        (availableHeight + 8f) / (cellHeight + 8f)
                    ).toInt().coerceAtLeast(0).coerceAtMost(maxRowsPerPage)
                    if (rowsThatFitAtFixedSize == 0) {
                        nextPage()
                        continue
                    }
                    val rowsOnThisPage = minOf(remainingRows, rowsThatFitAtFixedSize, maxRowsPerPage)
                    val photosOnThisPage = minOf(remaining, rowsOnThisPage * columns)
                    val pagePhotos = observation.photos.subList(photoIndex, photoIndex + photosOnThisPage)
                    drawPhotoGrid(pagePhotos, cellHeight, columns)
                    photoIndex += photosOnThisPage
                    if (photoIndex < observation.photos.size) nextPage()
                }
            }
            spacer(8f)
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

        fun signature(strokes: List<SignatureStroke>, imagePath: String) {
            if (strokes.isEmpty() && imagePath.isBlank()) return
            ensureSpace(125f)
            page.canvas.drawText(labels.text("Allekirjoitus"), margin, y + 12f, boldPaint)
            if (imagePath.isNotBlank()) {
                val signatureImage = SignatureImage.prepare(imagePath)
                if (signatureImage != null) {
                    val sourceWidth = signatureImage.width.toFloat().coerceAtLeast(1f)
                    val sourceHeight = signatureImage.height.toFloat().coerceAtLeast(1f)
                    val scale = minOf(320f / sourceWidth, 95f / sourceHeight)
                    val width = sourceWidth * scale
                    val height = sourceHeight * scale
                    page.canvas.drawBitmap(
                        signatureImage, null,
                        RectF(margin, y + 18f, margin + width, y + 18f + height),
                        imagePaint
                    )
                    signatureImage.recycle()
                    y += 125f
                    return
                }
            }
            if (strokes.isEmpty()) return
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

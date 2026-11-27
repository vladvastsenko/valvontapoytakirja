package fi.vladi.valvontapoytakirja

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
import java.io.File

internal fun exportCustomPdf(context: Context, store: CustomStore, report: CustomDocument): android.net.Uri {
    val settings = fi.vladi.valvontapoytakirja.data.ReportRepository(context).loadSettings()
    val labels = customLabels(report.pdfLanguage.ifBlank { fi.vladi.tarkastuspoytakirja.AppLanguage.selected(context) })
    val pdf = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
    var pageNumber = 0
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(595,842,++pageNumber).create())
    var y = 44f
    fun finish() { paint.typeface = Typeface.DEFAULT; paint.textSize = 9f
        val footer = settings.footerText.replace("\n"," ")
        if (report.useSharedSettings && footer.isNotBlank()) page.canvas.drawText(footer.take(paint.breakText(footer,true,490f,null)),42f,815f,paint)
        paint.textSize = 10f; page.canvas.drawText(pageNumber.toString(), 550f, 815f, paint); pdf.finishPage(page) }
    fun newPage() { finish(); page = pdf.startPage(PdfDocument.PageInfo.Builder(595,842,++pageNumber).create()); y = 44f }
    fun space(height: Float) { if (y + height > 790.01f) newPage() }
    var afterDedicatedPhotos = false
    fun resumeText() { if (afterDedicatedPhotos) { newPage(); afterDedicatedPhotos = false } }
    fun line(text: String, bold: Boolean = false) {
        resumeText()
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textSize = if (bold) 15f else 12f
        for (paragraph in text.split("\n")) {
            var rest = paragraph
            do {
                paint.textSize = if (bold) 15f else 12f
                val n = if (rest.isEmpty()) 0 else paint.breakText(rest,true,510f,null).coerceAtLeast(1)
                val cut = if (n < rest.length) rest.take(n).lastIndexOf(' ').takeIf { it > 0 } ?: n else n
                space(22f); paint.textSize = if (bold) 15f else 12f
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                page.canvas.drawText(rest.take(cut),42f,y,paint); y += 20f
                rest = rest.drop(cut).trimStart()
            } while (rest.isNotEmpty())
        }
    }
    fun table(f: CustomField, value: String) {
        val columns = customColumns(f)
        val rows = customRows(value,columns.size).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return
        // Split wide tables into readable groups of at most four columns.
        for (start in columns.indices step 4) {
            val end = minOf(start+4,columns.size)
            val width = 510f/(end-start)
            fun wrapCell(text: String): List<String> {
                paint.textSize = 10f
                return text.split("\n").flatMap { paragraph ->
                    val lines = mutableListOf<String>(); var rest = paragraph
                    do { val n = if (rest.isEmpty()) 0 else paint.breakText(rest,true,width-12f,null).coerceAtLeast(1); lines.add(rest.take(n)); rest = rest.drop(n) } while (rest.isNotEmpty())
                    lines
                }
            }
            fun drawRow(cells: List<List<String>>, offset: Int, count: Int) {
                val height = count*14f+10f
                cells.forEachIndexed { i,lines ->
                    val x = 42f+i*width
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.6f
                    page.canvas.drawRect(x,y,x+width,y+height,paint)
                    paint.style = Paint.Style.FILL; paint.textSize = 10f
                    lines.drop(offset).take(count).forEachIndexed { j,t -> page.canvas.drawText(t,x+6,y+14+j*14,paint) }
                }
                y += height
            }
            val header = columns.subList(start,end).map { wrapCell(it) }
            val headerCount = minOf(header.maxOf { it.size }, 8)
            fun heading() { space(headerCount*14f+10f); drawRow(header,0,headerCount) }
            heading()
            for (row in rows) {
                val cells = row.subList(start,end).map { wrapCell(it) }
                val total = cells.maxOf { it.size }
                var offset = 0
                while (offset < total) {
                    if (y+24 > 790f) { space(24f); heading() }
                    val count = minOf(total-offset,((790f-y-10f)/14f).toInt().coerceAtLeast(1))
                    drawRow(cells,offset,count); offset += count
                }
            }
            y += 12f
        }
    }
    fun photoGrid(f: CustomField, names: List<String>) {
        if (names.isEmpty()) return
        val cols = f.gridColumns()
        val rows = f.gridRows()
        val gap = 8f
        val width = (510f-gap*(cols-1))/cols
        // Fixed geometry for every page, including the final partial page.
        val height = (716f-gap*(rows-1))/rows
        val title = f.label.ifBlank { labels[25+f.kind.ordinal] }
        resumeText()
        if (f.separatePhotoPages && y > 44f || y+20f+height > 790.01f) newPage()
        line(title,true)
        for ((rowIndex,row) in names.chunked(cols).withIndex()) {
            if (rowIndex > 0 && (y+height > 790.01f || (f.separatePhotoPages && rowIndex % rows == 0))) { newPage(); line(title,true) }
            row.forEachIndexed { index,name ->
                val file = store.image(name)
                val bitmap = decodeCustomImage(file,1600) ?: error("Unreadable image")
                try {
                    val scale = minOf(width/bitmap.width,height/bitmap.height)
                    val w = bitmap.width*scale
                    val h = bitmap.height*scale
                    val x = 42f+index*(width+gap)+(width-w)/2
                    val top = y+(height-h)/2
                    page.canvas.drawBitmap(bitmap,null,RectF(x,top,x+w,top+h),paint)
                } finally { bitmap.recycle() }
            }
            y += height+gap
        }
        if (f.separatePhotoPages) afterDedicatedPhotos = true
    }
    try {
        if (report.useSharedSettings && settings.logoPath.isNotBlank()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(settings.logoPath,bounds)
            var sample = 1
            while (bounds.outWidth/sample > 1200 || bounds.outHeight/sample > 1200) sample *= 2
            BitmapFactory.decodeFile(settings.logoPath,BitmapFactory.Options().apply { inSampleSize = sample })?.let { bitmap ->
                try { val scale = minOf(180f/bitmap.width,65f/bitmap.height); val w = bitmap.width*scale; val h = bitmap.height*scale
                    page.canvas.drawBitmap(bitmap,null,RectF(42f,y,42f+w,y+h),paint); y += h+20f
                } finally { bitmap.recycle() }
            }
        }
        if (report.useSharedSettings && settings.headerText.isNotBlank()) line(settings.headerText)
        line(report.template.title,true)
        if (report.useSharedSettings) {
            listOf(report.customerText, report.siteText, report.inspectorText.ifBlank { listOf(settings.inspectorName, settings.inspectorCertificateNumber, settings.inspectorPhone, settings.inspectorEmail).filter { it.isNotBlank() }.joinToString("\n") }).filter { it.isNotBlank() }.forEach { line(it) }
        }
        for (s in report.template.sections) {
            val fields = s.fields.filter { f ->
                val value = report.values[f.id] ?: f.defaultValue
                when (f.kind) {
                    CustomKind.HEADING -> true
                    CustomKind.PHOTOS, CustomKind.SIGNATURE -> !report.attachments[f.id].isNullOrEmpty()
                    CustomKind.TABLE -> customRows(value,customColumns(f).size).any { row -> row.any { it.isNotBlank() } }
                    else -> value.isNotBlank()
                }
            }
            if (fields.isEmpty()) continue
            if (s.title.isNotBlank()) line(s.title,true)
            for (f in fields) {
                if (f.kind == CustomKind.PHOTOS) {
                    photoGrid(f,report.attachments[f.id].orEmpty())
                    continue
                }
                line(f.label.ifBlank { labels[25+f.kind.ordinal] },true)
                val value = report.values[f.id] ?: f.defaultValue
                if (f.kind == CustomKind.TABLE) table(f,value) else if (f.kind != CustomKind.HEADING && value.isNotBlank()) line(if (f.kind == CustomKind.CHECKBOX) (if (value == "true") "☑" else "☐") else value)
                for (name in report.attachments[f.id].orEmpty()) {
                    val file = store.image(name)
                    val bitmap = decodeCustomImage(file,1600) ?: error("Unreadable image")
                    try { val scale = minOf(510f/bitmap.width,300f/bitmap.height,1f); val w = bitmap.width*scale; val height = bitmap.height*scale; space(height+16); page.canvas.drawBitmap(bitmap,null,RectF(42f,y,42f+w,y+height),paint); y += height+16 } finally { bitmap.recycle() }
                }
            }
        }
        if (report.useSharedSettings) {
            val inspector = fi.vladi.valvontapoytakirja.data.ReportRepository(context).listInspectors().firstOrNull { it.id == report.inspectorId }
            val signaturePath = if (report.inspectorId.isBlank()) settings.signatureImagePath else inspector?.signatureImagePath.orEmpty()
            val strokes = if (report.inspectorId.isBlank()) settings.signatureStrokes else inspector?.signatureStrokes.orEmpty()
            if (signaturePath.isNotBlank()) {
                decodeCustomImage(File(signaturePath), 1200)?.let { bitmap ->
                    try {
                        resumeText()
                        val scale = minOf(200f / bitmap.width, 90f / bitmap.height)
                        val w = bitmap.width * scale
                        val h = bitmap.height * scale
                        space(h + 16f)
                        page.canvas.drawBitmap(bitmap, null, RectF(42f,y,42f+w,y+h),paint)
                        y += h + 16f
                    } finally { bitmap.recycle() }
                }
            } else if (strokes.any { it.points.isNotEmpty() }) {
                resumeText()
                space(100f)
                val points = strokes.flatMap { it.points }
                val minX = points.minOf { it.x }; val minY = points.minOf { it.y }
                val scale = minOf(200f / (points.maxOf { it.x } - minX).coerceAtLeast(1f), 80f / (points.maxOf { it.y } - minY).coerceAtLeast(1f))
                paint.strokeWidth = 1.5f
                strokes.forEach { stroke -> stroke.points.zipWithNext().forEach { (a,b) ->
                    page.canvas.drawLine(42f+(a.x-minX)*scale,y+(a.y-minY)*scale,42f+(b.x-minX)*scale,y+(b.y-minY)*scale,paint)
                } }
                y += 100f
            }
        }
        finish()
        val file = File.createTempFile("custom_", ".pdf", context.cacheDir)
        try { file.outputStream().use { pdf.writeTo(it) }; return checkNotNull(SharedDocumentsArchive.archiveFile(context,file,"Raportit/PDF","custom_"+report.id+"_"+java.util.UUID.randomUUID()+".pdf","application/pdf", overwrite = false)) } finally { file.delete() }
    } finally { pdf.close() }
}

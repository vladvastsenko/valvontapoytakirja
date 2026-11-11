package fi.vladi.tarkastuspoytakirja

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fi.vladi.tarkastuspoytakirja.data.ReportRepository
import fi.vladi.tarkastuspoytakirja.model.CustomReportField
import fi.vladi.tarkastuspoytakirja.model.InspectionReport
import fi.vladi.tarkastuspoytakirja.model.ReportPhoto
import fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TarkastusApp()
                }
            }
        }
    }
}

@Composable
private fun TarkastusApp() {
    val context = LocalContext.current
    val repository = remember { ReportRepository(context) }
    var reports by remember { mutableStateOf(repository.listReports()) }
    var templates by remember { mutableStateOf(repository.listTemplates()) }
    var currentReport by remember { mutableStateOf<InspectionReport?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun saveReport(report: InspectionReport) {
        currentReport = report
        repository.save(report)
        reports = repository.listReports()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val report = currentReport
        if (success && file != null && report != null && file.exists() && file.length() > 0) {
            saveReport(report.copy(photos = report.photos + ReportPhoto(filePath = file.absolutePath)))
        } else if (file != null) {
            file.delete()
        }
        pendingCameraFile = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        val report = currentReport ?: return@rememberLauncherForActivityResult
        val imported = uris.mapNotNull { uri ->
            repository.importPhoto(report.id, uri)?.let { ReportPhoto(filePath = it.absolutePath) }
        }
        if (imported.isNotEmpty()) saveReport(report.copy(photos = report.photos + imported))
    }

    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val report = currentReport ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            repository.importLogo(report.id, uri)?.let { logo ->
                saveReport(report.copy(logoPath = logo.absolutePath))
            }
        }
    }

    val active = currentReport
    if (active == null) {
        HomeScreen(
            reports = reports,
            templates = templates,
            onCreateFromTemplate = { template -> saveReport(template.copy(id = java.util.UUID.randomUUID().toString(), createdAt = LocalDate.now().toString(), templateName = "", photos = emptyList(), signatureStrokes = emptyList())) },
            onCreate = {
                val newReport = InspectionReport(
                    inspectionDate = LocalDate.now().toString(),
                    )
                saveReport(newReport)
            },
            onOpen = { currentReport = it }
        )
    } else {
        EditorScreen(
            report = active,
            onChange = ::saveReport,
            onBack = {
                repository.save(active)
                reports = repository.listReports()
                currentReport = null
            },
            onCamera = {
                val file = repository.createCameraPhotoFile(active.id)
                pendingCameraFile = file
                cameraLauncher.launch(repository.cameraUri(file))
            },
            onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPickLogo = { logoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSaveTemplate = { name -> repository.saveTemplate(active, name); templates = repository.listTemplates() },
            onRemoveLogo = {
                if (active.logoPath.isNotBlank()) repository.deletePhoto(active.logoPath)
                saveReport(active.copy(logoPath = ""))
            },
            onExport = {
                val pdf = ReportPdfGenerator.generate(context, active)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Jaa tarkastuspöytäkirja"))
            },
            onDeletePhoto = { path ->
                repository.deletePhoto(path)
                saveReport(active.copy(photos = active.photos.filterNot { it.filePath == path }))
            }
        )
    }
}

@Composable
private fun HomeScreen(
    reports: List<InspectionReport>,
    templates: List<InspectionReport>,
    onCreateFromTemplate: (InspectionReport) -> Unit,
    onCreate: () -> Unit,
    onOpen: (InspectionReport) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tarkastuspöytäkirja", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Yleiset tarkastusraportit")
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text("Uusi pöytäkirja")
        }
        if (templates.isNotEmpty()) {
            Text("Pöytäkirjamallit", style = MaterialTheme.typography.titleLarge)
            templates.forEach { template ->
                OutlinedButton(onClick = { onCreateFromTemplate(template) }, modifier = Modifier.fillMaxWidth()) {
                    Text(template.templateName + " • " + template.siteAddress)
                }
            }
        }
        Text("Tallennetut pöytäkirjat", style = MaterialTheme.typography.titleLarge)
        if (reports.isEmpty()) {
            Text("Ei vielä tallennettuja pöytäkirjoja.")
        } else {
            reports.forEach { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(report) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(report.siteAddress.ifBlank { "Uusi pöytäkirja" }, fontWeight = FontWeight.SemiBold)
                        if (report.folderPath.isNotBlank()) Text("Kansio: " + report.folderPath, style = MaterialTheme.typography.bodySmall)
                        Text("Tarkastuspäivä: ${report.inspectionDate.ifBlank { "—" }}")
                        Text("Tulos: ${report.inspectionResult.ifBlank { "Ei valittu" }}")
                        Text("${report.photos.size} valokuvaa", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Tarkastuspöytäkirja 1.0.0 • Tallennus tapahtuu tässä puhelimessa.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditorScreen(
    report: InspectionReport,
    onChange: (InspectionReport) -> Unit,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onExport: () -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("← Takaisin") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onExport) { Text("Luo PDF") }
        }
        Text("TARKASTUSPÖYTÄKIRJA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Täytä tiedot. Muutokset tallentuvat automaattisesti tähän puhelimeen.", style = MaterialTheme.typography.bodySmall)

        SectionTitle("PDF:N ULKOASU")
        Text("Oma logo ensimmäisen sivun yläosaan")
        if (report.logoPath.isNotBlank()) {
            val logoBitmap = remember(report.logoPath) {
                BitmapFactory.decodeFile(report.logoPath)?.asImageBitmap()
            }
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Oma logo",
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickLogo, modifier = Modifier.weight(1f)) {
                Text(if (report.logoPath.isBlank()) "Valitse logo" else "Vaihda logo")
            }
            if (report.logoPath.isNotBlank()) {
                OutlinedButton(onClick = onRemoveLogo) { Text("Poista logo") }
            }
        }
        Field("Alatunniste PDF:n viimeisellä sivulla", report.footerText, minLines = 2) {
            onChange(report.copy(footerText = it))
        }

        SectionTitle("1. TYÖMAA JA OSAPUOLET")
        Field("Kansio / alikansio (esim. Asiakkaat/Helsinki/Kohde)", report.folderPath) {
            onChange(report.copy(folderPath = it.trim('/')))
        }
        Field("Kohteen osoite", report.siteAddress) { onChange(report.copy(siteAddress = it)) }
        Field("Tilaaja / kohteen edustaja", report.clientOrRepresentative) { onChange(report.copy(clientOrRepresentative = it)) }
        Field("Tilaajan puhelinnumero", report.clientPhone) { onChange(report.copy(clientPhone = it)) }
        Field("Tilaajan sähköposti", report.clientEmail) { onChange(report.copy(clientEmail = it)) }
        SectionTitle("2. TARKASTUKSEN SISÄLTÖ")
        Field("Tarkastuksen kuvaus", report.description, minLines = 5) {
            onChange(report.copy(description = it))
        }
        report.customFields.forEach { customField ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Field("Kentän otsikko", customField.label) { value ->
                        onChange(report.copy(customFields = report.customFields.map {
                            if (it.id == customField.id) it.copy(label = value) else it
                        }))
                    }
                    Field("Sisältö", customField.value, minLines = 3) { value ->
                        onChange(report.copy(customFields = report.customFields.map {
                            if (it.id == customField.id) it.copy(value = value) else it
                        }))
                    }
                    TextButton(onClick = {
                        onChange(report.copy(customFields = report.customFields.filterNot { it.id == customField.id }))
                    }) { Text("Poista kenttä") }
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(report.copy(customFields = report.customFields + CustomReportField(label = "Uusi kenttä"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Lisää kenttä") }

        SectionTitle("3. VALOKUVAT")
        Text("Voit ottaa kuvan kameralla tai valita useita kuvia galleriasta.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) { Text("Avaa kamera") }
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) { Text("Galleria") }
        }
        report.photos.forEach { photo ->
            PhotoCard(
                photo = photo,
                onCaptionChange = { caption ->
                    onChange(report.copy(photos = report.photos.map { if (it.id == photo.id) it.copy(caption = caption) else it }))
                },
                onDelete = { onDeletePhoto(photo.filePath) }
            )
        }

        SectionTitle("4. TARKASTUKSEN TULOS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (report.inspectionResult == "HYVÄKSYTTY") {
                Button(
                    onClick = { onChange(report.copy(inspectionResult = "HYVÄKSYTTY")) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF168447))
                ) { Text("Hyväksytty") }
            } else {
                OutlinedButton(
                    onClick = { onChange(report.copy(inspectionResult = "HYVÄKSYTTY")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Hyväksytty") }
            }
            if (report.inspectionResult == "HYLÄTTY") {
                Button(
                    onClick = { onChange(report.copy(inspectionResult = "HYLÄTTY")) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFC62828))
                ) { Text("Hylätty") }
            } else {
                OutlinedButton(
                    onClick = { onChange(report.copy(inspectionResult = "HYLÄTTY")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Hylätty") }
            }
        }
        Field("Lisähuomiot", report.additionalNotes, minLines = 3) { onChange(report.copy(additionalNotes = it)) }

        SectionTitle("5. TARKASTAJA")
        Field("Tarkastajan nimi", report.inspectorName) { onChange(report.copy(inspectorName = it)) }
        Field("Sertifikaattinumero", report.inspectorCertificateNumber) { onChange(report.copy(inspectorCertificateNumber = it)) }
        Field("Puhelin", report.inspectorPhone) { onChange(report.copy(inspectorPhone = it)) }
        Field("Sähköposti", report.inspectorEmail) { onChange(report.copy(inspectorEmail = it)) }
        DateField("Tarkastuspäivä", report.inspectionDate) {
            onChange(report.copy(inspectionDate = it))
        }
        SignaturePad(
            strokes = report.signatureStrokes,
            onChange = { onChange(report.copy(signatureStrokes = it)) }
        )

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onSaveTemplate(report.siteAddress.ifBlank { "Uusi malli" }) }, modifier = Modifier.fillMaxWidth()) {
            Text("Tallenna raportti malliksi")
        }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Luo ja jaa PDF-raportti") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Tallenna ja sulje") }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: String,
    onDateSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val initialMillis = remember(value) {
        runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
    val displayDate = remember(value) {
        runCatching { LocalDate.parse(value).format(dateFormatter) }.getOrDefault("")
    }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(displayDate.ifBlank { "Valitse päivämäärä" })
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDateSelected(selectedDate.toString())
                        }
                        showPicker = false
                    }
                ) {
                    Text("Valitse")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Peruuta")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = minLines == 1
    )
}

@Composable
private fun PhotoCard(
    photo: ReportPhoto,
    onCaptionChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val bitmap = remember(photo.filePath) {
        BitmapFactory.decodeFile(photo.filePath)?.asImageBitmap()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Valokuva",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("Kuvaa ei voitu näyttää: ${File(photo.filePath).name}")
            }
            Field("Kommentti (valinnainen)", photo.caption, minLines = 2, onValueChange = onCaptionChange)
            TextButton(onClick = onDelete) { Text("Poista kuva") }
        }
    }
}

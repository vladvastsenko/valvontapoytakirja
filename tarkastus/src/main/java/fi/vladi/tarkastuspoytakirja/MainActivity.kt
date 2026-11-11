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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import fi.vladi.tarkastuspoytakirja.model.InspectionObservation
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
    var pendingCameraObservationId by remember { mutableStateOf<String?>(null) }
    var pendingGalleryObservationId by remember { mutableStateOf<String?>(null) }

    fun saveReport(report: InspectionReport) {
        currentReport = report
        repository.save(report)
        reports = repository.listReports()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val observationId = pendingCameraObservationId
        val report = currentReport
        if (success && file != null && observationId != null && report != null && file.exists() && file.length() > 0) {
            val updated = report.copy(observations = report.observations.map { observation ->
                if (observation.id == observationId) {
                    observation.copy(photos = observation.photos + ReportPhoto(filePath = file.absolutePath))
                } else observation
            })
            saveReport(updated)
        } else if (file != null) {
            file.delete()
        }
        pendingCameraFile = null
        pendingCameraObservationId = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        val report = currentReport
        val observationId = pendingGalleryObservationId
        if (report != null && observationId != null) {
            val imported = uris.mapNotNull { uri ->
                repository.importPhoto(report.id, uri)?.let { ReportPhoto(filePath = it.absolutePath) }
            }
            if (imported.isNotEmpty()) {
                saveReport(report.copy(observations = report.observations.map { observation ->
                    if (observation.id == observationId) {
                        observation.copy(photos = observation.photos + imported)
                    } else observation
                }))
            }
        }
        pendingGalleryObservationId = null
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
            onCreateFromTemplate = { template ->
                saveReport(template.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    createdAt = LocalDate.now().toString(),
                    templateName = "",
                    observations = if (template.observations.isEmpty()) listOf(InspectionObservation())
                        else template.observations.map { it.copy(photos = emptyList()) },
                    signatureStrokes = emptyList()
                ))
            },
            onCreate = {
                saveReport(InspectionReport(
                    inspectionDate = LocalDate.now().toString(),
                    observations = listOf(InspectionObservation())
                ))
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
            onCamera = { observationId ->
                val file = repository.createCameraPhotoFile(active.id)
                pendingCameraFile = file
                pendingCameraObservationId = observationId
                cameraLauncher.launch(repository.cameraUri(file))
            },
            onGallery = { observationId ->
                pendingGalleryObservationId = observationId
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickLogo = { logoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSaveTemplate = { name ->
                repository.saveTemplate(active, name)
                templates = repository.listTemplates()
            },
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
            onDeletePhoto = { observationId, path ->
                repository.deletePhoto(path)
                saveReport(active.copy(observations = active.observations.map { observation ->
                    if (observation.id == observationId) {
                        observation.copy(photos = observation.photos.filterNot { it.filePath == path })
                    } else observation
                }))
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(report.siteAddress.ifBlank { "Uusi pöytäkirja" }, fontWeight = FontWeight.SemiBold)
                        if (report.folderPath.isNotBlank()) Text("Kansio: " + report.folderPath, style = MaterialTheme.typography.bodySmall)
                        Text("Tarkastuspäivä: ${report.inspectionDate.ifBlank { "—" }}")
                        Text("${report.observations.size} havaintoa", style = MaterialTheme.typography.bodySmall)
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
    onCamera: (String) -> Unit,
    onGallery: (String) -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onExport: () -> Unit,
    onDeletePhoto: (String, String) -> Unit
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

        SectionTitle("2. TARKASTUKSEN TARKOITUS JA LAAJUUS")
        Field("Tarkastuksen tarkoitus ja laajuus", report.purposeAndScope, minLines = 4) {
            onChange(report.copy(purposeAndScope = it))
        }

        SectionTitle("3. YLEISET HUOMIOT")
        Field("Yleiset huomiot", report.generalObservations, minLines = 4) {
            onChange(report.copy(generalObservations = it))
        }

        SectionTitle("4. OMAT LISÄKENTÄT")
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
        ) { Text("+ Lisää oma kenttä") }

        SectionTitle("5. HAVAITUT PUUTTEET JA VIRHEET")
        report.observations.forEachIndexed { index, observation ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HAVAinto ${index + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Field("Tila / sijainti", observation.location) { value ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == observation.id) it.copy(location = value) else it
                        }))
                    }
                    Field("Havaitun puutteen kuvaus", observation.description, minLines = 2) { value ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == observation.id) it.copy(description = value) else it
                        }))
                    }
                    Field("Suositeltu korjaustoimenpide", observation.recommendedAction, minLines = 2) { value ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == observation.id) it.copy(recommendedAction = value) else it
                        }))
                    }
                    observation.photos.forEach { photo ->
                        PhotoCard(
                            photo = photo,
                            onCaptionChange = { caption ->
                                onChange(report.copy(observations = report.observations.map { item ->
                                    if (item.id == observation.id) {
                                        item.copy(photos = item.photos.map { if (it.id == photo.id) it.copy(caption = caption) else it })
                                    } else item
                                }))
                            },
                            onDelete = { onDeletePhoto(observation.id, photo.filePath) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onCamera(observation.id) }, modifier = Modifier.weight(1f)) {
                            Text("Ota kuva")
                        }
                        OutlinedButton(onClick = { onGallery(observation.id) }, modifier = Modifier.weight(1f)) {
                            Text("Galleriasta")
                        }
                        OutlinedButton(
                            onClick = {
                                observation.photos.forEach { onDeletePhoto(observation.id, it.filePath) }
                                onChange(report.copy(observations = report.observations.filterNot { it.id == observation.id }))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Poista") }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(report.copy(observations = report.observations + InspectionObservation())) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Lisää havainto") }

        SectionTitle("6. YHTEENVETO")
        Field("Johtopäätökset ja tarvittavat toimenpiteet", report.summary, minLines = 4) {
            onChange(report.copy(summary = it))
        }

        SectionTitle("7. TARKASTAJA")
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
                    contentDescription = "Havaintokuva",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("Kuvaa ei voitu näyttää: ${File(photo.filePath).name}")
            }
            Field("Kuvan kommentti (valinnainen)", photo.caption, minLines = 2, onValueChange = onCaptionChange)
            TextButton(onClick = onDelete) { Text("Poista kuva") }
        }
    }
}

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

    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(displayDate.ifBlank { "Valitse päivämäärä" })
        }
    }

    if (showPicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(selectedDate.toString())
                    }
                    showPicker = false
                }) { Text("Valitse") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Peruuta") } }
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }
}

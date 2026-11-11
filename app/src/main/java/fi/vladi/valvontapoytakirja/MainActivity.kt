package fi.vladi.valvontapoytakirja

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
import fi.vladi.valvontapoytakirja.data.MaterialCatalog
import fi.vladi.valvontapoytakirja.data.ReportRepository
import fi.vladi.valvontapoytakirja.model.InspectionReport
import fi.vladi.valvontapoytakirja.model.MaterialEntry
import fi.vladi.valvontapoytakirja.model.ReportPhoto
import fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val WATERPROOFING_FAMILY_CATEGORIES = setOf(
    "pohjuste (primer)",
    "vedeneriste",
    "kaivolaippa / lattiakaivovahvike",
    "nurkkavahvike / vahvikenauha",
    "läpivientivahvike",
    "läpivientien tiivistysmassa",
    "kiinnityslaasti"
)

private fun isWaterproofingFamilyCategory(category: String): Boolean =
    category.trim().lowercase() in WATERPROOFING_FAMILY_CATEGORIES

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ValvontaApp()
                }
            }
        }
    }
}

@Composable
private fun ValvontaApp() {
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
                    workStartDate = LocalDate.now().toString()
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
                context.startActivity(Intent.createChooser(sendIntent, "Jaa valvontapöytäkirja"))
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
        Text("Valvoja", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Märkätilojen vedeneristyksen tarkastusraportit")
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text("Uusi pöytäkirja")
        }
        if (templates.isNotEmpty()) {
            Text("Pöytäkirjamallit", style = MaterialTheme.typography.titleLarge)
            templates.forEach { template ->
                OutlinedButton(onClick = { onCreateFromTemplate(template) }, modifier = Modifier.fillMaxWidth()) {
                    Text(template.templateName + " • " + template.siteAddress + " • " + (template.materials.firstOrNull { it.manufacturer.isNotBlank() }?.manufacturer ?: "Valmistaja määrittämättä"))
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
        Text("Valvoja 0.4.5 • Tallennus tapahtuu tässä puhelimessa.", style = MaterialTheme.typography.bodySmall)
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
        Text("VALVONTAPÖYTÄKIRJA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        Field("Tilaajan sähköposti", report.clientEmail) { onChange(report.copy(clientEmail = it)) }
        Field("Vedeneristäjän nimi", report.installerName) { onChange(report.copy(installerName = it)) }
        Field("Sertifikaattinumero", report.installerCertificateNumber) { onChange(report.copy(installerCertificateNumber = it)) }
        Field("Vedeneristäjän puhelin", report.installerPhone) { onChange(report.copy(installerPhone = it)) }
        Field("Vedeneristäjän sähköposti", report.installerEmail) { onChange(report.copy(installerEmail = it)) }
        DateField("Asennustyön aloituspäivä", report.workStartDate) {
            onChange(report.copy(workStartDate = it))
        }
        DateField("Asennustyön päättymispäivä", report.workEndDate) {
            onChange(report.copy(workEndDate = it))
        }

        SectionTitle("2. ALUSTAT JA TYÖOLOSUHTEET")
        Field("Seinien alusta / rakenne", report.wallSubstrate) { onChange(report.copy(wallSubstrate = it)) }
        Field("Lattian alusta / rakenne", report.floorSubstrate) { onChange(report.copy(floorSubstrate = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Työohjeet annettu ja luettu", modifier = Modifier.weight(1f))
            Switch(
                checked = report.workInstructionsProvidedAndRead,
                onCheckedChange = { onChange(report.copy(workInstructionsProvidedAndRead = it)) }
            )
        }
        Field("Työtilan lämpötila (°C)", report.roomTemperatureC) { onChange(report.copy(roomTemperatureC = it)) }
        Field("Suhteellinen kosteus (%)", report.relativeHumidityPercent) { onChange(report.copy(relativeHumidityPercent = it)) }
        Field("Alustan kosteus (%)", report.substrateMoisturePercent) { onChange(report.copy(substrateMoisturePercent = it)) }

        SectionTitle("3. KÄYTETYT MATERIAALIT")
        Text("Valitse sama valmistaja koko vedeneristysjärjestelmälle: pohjuste, vedeneriste, kaivolaippa, vahvikenauhat, läpivientivahvikkeet, tiivistysmassa ja kiinnityslaasti.")
        val knownFamilyManufacturers = MaterialCatalog.manufacturersFor("vedeneriste")
        val familyManufacturer = report.materials.firstOrNull {
            isWaterproofingFamilyCategory(it.category) && it.manufacturer in knownFamilyManufacturers
        }?.manufacturer.orEmpty()
        val familyAnchorIndex = report.materials.indexOfFirst {
            isWaterproofingFamilyCategory(it.category) &&
                it.manufacturer == familyManufacturer &&
                familyManufacturer.isNotBlank() &&
                MaterialCatalog.manufacturersFor(it.category).containsAll(knownFamilyManufacturers)
        }.takeIf { it >= 0 } ?: report.materials.indexOfFirst {
            isWaterproofingFamilyCategory(it.category) &&
                it.manufacturer == familyManufacturer &&
                familyManufacturer.isNotBlank()
        }

        // Clean up legacy reports that may contain products from another manufacturer.
        LaunchedEffect(report.id, familyManufacturer, report.materials) {
            if (familyManufacturer.isNotBlank()) {
                val normalizedMaterials = report.materials.map { item ->
                    if (isWaterproofingFamilyCategory(item.category)) {
                        val validProduct = MaterialCatalog.productsFor(
                            item.category,
                            familyManufacturer
                        ).any { it.name == item.productName }
                        item.copy(
                            manufacturer = familyManufacturer,
                            productName = if (validProduct) item.productName else ""
                        )
                    } else item
                }
                if (normalizedMaterials != report.materials) {
                    onChange(report.copy(materials = normalizedMaterials))
                }
            }
        }

        report.materials.forEachIndexed { index, material ->
            MaterialCard(
                material = material,
                familyManufacturer = familyManufacturer,
                isFamilyAnchor = index == familyAnchorIndex,
                onChange = { changed ->
                    val updatedMaterials = report.materials.toMutableList()
                    updatedMaterials[index] = changed

                    val selectedFamilyManufacturer = when {
                        isWaterproofingFamilyCategory(changed.category) &&
                            changed.manufacturer in knownFamilyManufacturers -> changed.manufacturer
                        familyManufacturer.isNotBlank() -> familyManufacturer
                        else -> ""
                    }

                    if (selectedFamilyManufacturer.isNotBlank() &&
                        isWaterproofingFamilyCategory(changed.category)
                    ) {
                        updatedMaterials.indices.forEach { familyIndex ->
                            val familyItem = updatedMaterials[familyIndex]
                            if (isWaterproofingFamilyCategory(familyItem.category)) {
                                val productBelongsToFamily = MaterialCatalog.productsFor(
                                    familyItem.category,
                                    selectedFamilyManufacturer
                                ).any { it.name == familyItem.productName }
                                val keepPosition = familyIndex == index || productBelongsToFamily
                                updatedMaterials[familyIndex] = familyItem.copy(
                                    manufacturer = if (keepPosition) selectedFamilyManufacturer else "",
                                    productName = if (keepPosition) familyItem.productName else ""
                                )
                            }
                        }
                    }
                    onChange(report.copy(materials = updatedMaterials))
                },
                onRemove = {
                    onChange(report.copy(materials = report.materials.filterNot { it.id == material.id }))
                }
            )
        }
        OutlinedButton(
            onClick = { onChange(report.copy(materials = report.materials + MaterialEntry(category = "Lisää:"))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Lisää materiaali") }

        SectionTitle("4. RAKENTEET JA MITAT")
        Field("Lattialämmitys", report.floorHeating) { onChange(report.copy(floorHeating = it)) }
        Field("Kynnys / viemärin nosto (mm)", report.thresholdOrDrainLiftMm) { onChange(report.copy(thresholdOrDrainLiftMm = it)) }
        Text("Lattian kaato")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("1:50", "1:100").forEach { ratio ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = ratio in report.floorSlopeRatios,
                        onCheckedChange = { checked ->
                            val updated = if (checked) {
                                (report.floorSlopeRatios + ratio).distinct()
                            } else {
                                report.floorSlopeRatios.filterNot { it == ratio }
                            }
                            onChange(report.copy(floorSlopeRatios = updated))
                        }
                    )
                    Text(ratio)
                }
            }
        }
        Field("Kaatojen sijainnit / lisätiedot", report.floorSlopes, minLines = 2) {
            onChange(report.copy(floorSlopes = it))
        }

        SectionTitle("5. PINTA-ALAT JA MENEKKI")
        Field("Seinien pinta-ala (m²)", report.wallAreaM2) { onChange(report.copy(wallAreaM2 = it)) }
        Field("Seinät – valmistajan vaatima vedeneristeen menekki (kg/m² tai l/m²)", report.wallRequiredWaterproofingConsumptionPerM2) {
            onChange(report.copy(wallRequiredWaterproofingConsumptionPerM2 = it))
        }
        Field("Seinät – käytetty vedeneristeen kokonaismäärä (kg tai l)", report.wallWaterproofingConsumptionLitres) {
            onChange(report.copy(wallWaterproofingConsumptionLitres = it))
        }
        Field("Lattian pinta-ala (m²)", report.floorAreaM2) { onChange(report.copy(floorAreaM2 = it)) }
        Field("Lattia – valmistajan vaatima vedeneristeen menekki (kg/m² tai l/m²)", report.floorRequiredWaterproofingConsumptionPerM2) {
            onChange(report.copy(floorRequiredWaterproofingConsumptionPerM2 = it))
        }
        Field("Lattia – käytetty vedeneristeen kokonaismäärä (kg tai l)", report.floorWaterproofingConsumptionLitres) {
            onChange(report.copy(floorWaterproofingConsumptionLitres = it))
        }

        SectionTitle("6. KUIVAKALVON PAKSUUS")
        Field("Seinät – mitattu kuivakalvon paksuus (mm)", report.wallDryFilmThicknessMm) { onChange(report.copy(wallDryFilmThicknessMm = it)) }
        Field("Seinät – valmistajan vaatima kuivakalvon vähimmäispaksuus (mm)", report.wallRequiredMinimumThicknessMm) { onChange(report.copy(wallRequiredMinimumThicknessMm = it)) }
        Field("Lattia – mitattu kuivakalvon paksuus (mm)", report.floorDryFilmThicknessMm) { onChange(report.copy(floorDryFilmThicknessMm = it)) }
        Field("Lattia – valmistajan vaatima kuivakalvon vähimmäispaksuus (mm)", report.floorRequiredMinimumThicknessMm) { onChange(report.copy(floorRequiredMinimumThicknessMm = it)) }

        SectionTitle("7. VALOKUVAT")
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

        SectionTitle("8. TARKASTUKSEN TULOS")
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

        SectionTitle("9. TARKASTAJA")
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
private fun MaterialCard(
    material: MaterialEntry,
    familyManufacturer: String,
    isFamilyAnchor: Boolean,
    onChange: (MaterialEntry) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember(material.id) { mutableStateOf(false) }
    var manufacturerExpanded by remember(material.id) { mutableStateOf(false) }
    val isFamilyMaterial = isWaterproofingFamilyCategory(material.category)
    val lockedFamilyManufacturer = familyManufacturer.isNotBlank() && isFamilyMaterial
    val effectiveManufacturer = if (lockedFamilyManufacturer) familyManufacturer else material.manufacturer
    val manufacturers = MaterialCatalog.manufacturersFor(material.category).let { options ->
        if (lockedFamilyManufacturer && !isFamilyAnchor) listOf(familyManufacturer) else options
    }
    val productOptions = MaterialCatalog.productsFor(material.category, effectiveManufacturer)
    val selectedProductName = material.productName.takeIf { name ->
        productOptions.any { it.name == name }
    }.orEmpty()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(material.category, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("Poista") }
            }
            if (lockedFamilyManufacturer && !isFamilyAnchor) {
                Text("Valmistaja: $familyManufacturer • sama järjestelmä")
            } else if (manufacturers.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { manufacturerExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(effectiveManufacturer.ifBlank { "Valitse valmistaja" })
                    }
                    DropdownMenu(expanded = manufacturerExpanded, onDismissRequest = { manufacturerExpanded = false }) {
                        manufacturers.forEach { manufacturer ->
                            DropdownMenuItem(text = { Text(manufacturer) }, onClick = {
                                onChange(material.copy(manufacturer = manufacturer, productName = ""))
                                manufacturerExpanded = false
                            })
                        }
                    }
                }
            }
            if (productOptions.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(selectedProductName.ifBlank { "Valitse tuote" })
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        productOptions.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = {
                                    onChange(material.copy(manufacturer = effectiveManufacturer.ifBlank { product.manufacturer }, productName = product.name))
                                    expanded = false
                                }
                            )
                        }
                        if (!isFamilyMaterial && !lockedFamilyManufacturer) {
                            DropdownMenuItem(
                                text = { Text("Muu / oma tuote") },
                                onClick = {
                                    onChange(material.copy(manufacturer = "", productName = ""))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            if (!isFamilyMaterial) {
                Field("Valmistaja", material.manufacturer) { onChange(material.copy(manufacturer = it)) }
                Field("Tuotteen nimi", material.productName) { onChange(material.copy(productName = it)) }
            } else if (productOptions.isEmpty()) {
                Text("Tälle valmistajalle ei ole tuotetta tässä kategoriassa. Valitse vain valmistajan järjestelmään kuuluva tuote toisesta sopivasta kohdasta.")
            }
            Field("Lisätiedot", material.notes) { onChange(material.copy(notes = it)) }
        }
    }
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

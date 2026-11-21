package fi.vladi.valvontapoytakirja

import android.content.Context
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fi.vladi.tarkastuspoytakirja.AppLanguage
import fi.vladi.tarkastuspoytakirja.MainActivity as TarkastusMainActivity
import fi.vladi.valvontapoytakirja.cloud.CloudBackupScreen
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
import fi.vladi.valvontapoytakirja.data.MaterialCatalog
import fi.vladi.valvontapoytakirja.data.ReportRepository
import fi.vladi.valvontapoytakirja.model.AppSettings
import fi.vladi.valvontapoytakirja.model.CustomerProfile
import fi.vladi.valvontapoytakirja.model.CustomerSite
import fi.vladi.valvontapoytakirja.model.InspectionReport
import fi.vladi.valvontapoytakirja.model.MaterialEntry
import fi.vladi.valvontapoytakirja.model.ReportPhoto
import fi.vladi.valvontapoytakirja.model.SiteFieldValue
import fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CloudAuthGate { signOut -> UnifiedLauncher(onSignOut = signOut) }
                }
            }
        }
    }
}

@Composable
private fun UnifiedLauncher(onSignOut: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { SharedDocumentsArchive.ensureFolder(context) }
    }
    var showValvoja by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }
    var showManagement by remember { mutableStateOf(false) }
    var showCloudBackup by remember { mutableStateOf(false) }

    if (showCloudBackup) {
        CloudBackupScreen(onBack = { showCloudBackup = false })
    } else if (showValvoja) {
        ValvontaApp(
            initialShowSettings = openSettings,
            onBackToLauncher = {
                showValvoja = false
                openSettings = false
            },
            onBackFromSettings = {
                showValvoja = false
                openSettings = false
            }
        )
    } else if (showManagement) {
        ManagementScreen(
            onSettings = {
                openSettings = true
                showValvoja = true
            },
            onCloudBackup = { showCloudBackup = true },
            onBack = { showManagement = false },
            onSignOut = onSignOut
        )
    } else {
        UnifiedStartScreen(
            onValvoja = {
                openSettings = false
                showValvoja = true
            },
            onTarkastus = {
                context.startActivity(Intent(context, TarkastusMainActivity::class.java))
            },
            onManagement = { showManagement = true }
        )
    }
}

@Composable
private fun UnifiedStartScreen(
    onValvoja: () -> Unit,
    onTarkastus: () -> Unit,
    onManagement: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tarkastus",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.IconButton(onClick = onManagement) {
                Text("⚙", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.choose_report))
        Button(onClick = onValvoja, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.report_valvonta))
        }
        Button(onClick = onTarkastus, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.report_tarkastus))
        }
    }
}

@Composable
private fun ManagementScreen(
    onSettings: () -> Unit,
    onCloudBackup: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var languageMenu by remember { mutableStateOf(false) }
    var pdfLanguageMenu by remember { mutableStateOf(false) }
    var defaultPdfLanguage by remember { mutableStateOf(context.getSharedPreferences("tarkastus_pdf_language", android.content.Context.MODE_PRIVATE).getString("default", "fi") ?: "fi") }
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.settings_sync), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(when (AppLanguage.selected(context)) { "ru" -> "Русский"; "sv" -> "Svenska"; "en" -> "English"; else -> "Suomi" } + " ▾")
            }
            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                listOf("fi" to "Suomi", "sv" to "Svenska", "ru" to "Русский", "en" to "English").forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        languageMenu = false
                        if (AppLanguage.selected(context) != code) {
                            AppLanguage.save(context, code)
                            (context as? android.app.Activity)?.recreate()
                        }
                    })
                }
            }
        }
        Text(stringResource(R.string.pdf_language_default), fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { pdfLanguageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(when (defaultPdfLanguage) { "ru" -> "Русский"; "sv" -> "Svenska"; "en" -> "English"; else -> "Suomi" } + " ▾")
            }
            DropdownMenu(expanded = pdfLanguageMenu, onDismissRequest = { pdfLanguageMenu = false }) {
                listOf("fi" to "Suomi", "sv" to "Svenska", "ru" to "Русский", "en" to "English").forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        pdfLanguageMenu = false
                        defaultPdfLanguage = code
                        context.getSharedPreferences("tarkastus_pdf_language", android.content.Context.MODE_PRIVATE)
                            .edit().putString("default", code).apply()
                    })
                }
            }
        }
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shared_settings))
        }
        Button(onClick = onCloudBackup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cloud_sync))
        }
        OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_out))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← " + stringResource(R.string.back))
        }
    }
}

@Composable
private fun ValvontaApp(
    initialShowSettings: Boolean = false,
    onBackToLauncher: () -> Unit = {},
    onBackFromSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { ReportRepository(context) }
    LaunchedEffect(repository) {
        withContext(Dispatchers.IO) { repository.archiveExistingData() }
    }
    var reports by remember { mutableStateOf(repository.listReports()) }
    var templates by remember { mutableStateOf(repository.listTemplates()) }
    var appSettings by remember { mutableStateOf(repository.loadSettings()) }
    var customers by remember { mutableStateOf(repository.listCustomers()) }
    var sites by remember { mutableStateOf(repository.listSites()) }
    var showSettings by remember { mutableStateOf(initialShowSettings) }
    var currentReport by remember { mutableStateOf<InspectionReport?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var pendingSaveJob by remember { mutableStateOf<Job?>(null) }
    var pendingGalleryJob by remember { mutableStateOf<Job?>(null) }
    var pendingSettingsSaveJob by remember { mutableStateOf<Job?>(null) }

    fun saveAppSettings(settings: AppSettings) {
        appSettings = settings
        pendingSettingsSaveJob?.cancel()
        pendingSettingsSaveJob = coroutineScope.launch {
            delay(300)
            withContext(Dispatchers.IO) { repository.saveSettings(settings) }
        }
    }

    fun applySettingsSnapshot(report: InspectionReport): InspectionReport = report.copy(
        logoPath = appSettings.logoPath,
        headerText = appSettings.headerText,
        footerText = appSettings.footerText,
        settingsSnapshotCreated = true
    )

    fun saveReport(report: InspectionReport) {
        val isNewReport = currentReport == null
        currentReport = report
        if (isNewReport) reports = listOf(report) + reports.filterNot { it.id == report.id }
        pendingSaveJob?.cancel()
        pendingSaveJob = coroutineScope.launch {
            delay(350)
            val latestReports = withContext(Dispatchers.IO) {
                repository.save(report)
                repository.listReports()
            }
            reports = latestReports
        }
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
        if (uris.isNotEmpty()) {
            pendingGalleryJob = coroutineScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        repository.importPhoto(report.id, uri)?.let { ReportPhoto(filePath = it.absolutePath) }
                    }
                }
                val latest = currentReport
                if (imported.isNotEmpty() && latest != null && latest.id == report.id) {
                    saveReport(latest.copy(photos = latest.photos + imported))
                }
            }
        }
    }

    val settingsLogoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            repository.importSettingsLogo(uri)?.let { logo ->
                saveAppSettings(appSettings.copy(logoPath = logo.absolutePath))
            }
        }
    }

    val settingsSignatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) repository.importSettingsSignature(uri)?.let { signature ->
            saveAppSettings(appSettings.copy(signatureImagePath = signature.absolutePath, signatureStrokes = emptyList()))
        }
    }

    val active = currentReport
    if (active == null && showSettings) {
        SettingsScreen(
            settings = appSettings,
            customers = customers,
            sites = sites,
            onSettingsChange = ::saveAppSettings,
            onSaveCustomer = { customer ->
                repository.saveCustomer(customer)
                customers = repository.listCustomers()
            },
            onDeleteCustomer = { id ->
                repository.deleteCustomer(id)
                customers = repository.listCustomers()
                sites = repository.listSites()
            },
            onSaveSite = { site ->
                repository.saveSite(site)
                sites = repository.listSites()
            },
            onDeleteSite = { id ->
                repository.deleteSite(id)
                sites = repository.listSites()
            },
            onPickLogo = {
                settingsLogoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemoveLogo = { saveAppSettings(appSettings.copy(logoPath = "")) },
            onPickSignature = { settingsSignatureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveSignature = { saveAppSettings(appSettings.copy(signatureImagePath = "")) },
            onBack = onBackFromSettings
        )
    } else if (active == null) {
        HomeScreen(
            reports = reports,
            templates = templates,
            onCreateFromTemplate = { template ->
                saveReport(applySettingsSnapshot(template.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    createdAt = LocalDate.now().toString(),
                    templateName = "",
                    photos = emptyList(),
                    signatureStrokes = emptyList()
                )))
            },
            onCreate = {
                val newReport = InspectionReport(
                    inspectionDate = LocalDate.now().toString(),
                    workStartDate = LocalDate.now().toString()
                )
                saveReport(applySettingsSnapshot(newReport))
            },
            onOpen = { currentReport = it },
            onDelete = { report ->
                repository.deleteReport(report.id)
                reports = repository.listReports()
            },
            onBackToLauncher = onBackToLauncher
        )
    } else {
        EditorScreen(
            report = active,
            customers = customers,
            sites = sites,
            onChange = ::saveReport,
            onBack = {
                coroutineScope.launch {
                    pendingGalleryJob?.join()
                    pendingSaveJob?.cancel()
                    val latest = currentReport ?: active
                    val latestReports = withContext(Dispatchers.IO) {
                        repository.save(latest)
                        repository.listReports()
                    }
                    reports = latestReports
                    currentReport = null
                }
            },
            onCamera = {
                val file = repository.createCameraPhotoFile(active.id)
                pendingCameraFile = file
                cameraLauncher.launch(repository.cameraUri(file))
            },
            onGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onSaveTemplate = { name ->
                repository.saveTemplate(active, name)
                templates = repository.listTemplates()
            },
            onPreview = {
                coroutineScope.launch {
                    pendingGalleryJob?.join()
                    val latest = currentReport ?: active
                    val pdf = withContext(Dispatchers.IO) {
                        ReportPdfGenerator.generate(context, latest, appSettings)
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(viewIntent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(context, context.getString(R.string.ui_preview_unavailable), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onExport = {
                coroutineScope.launch {
                    pendingGalleryJob?.join()
                    pendingSaveJob?.cancel()
                    val latest = currentReport ?: active
                    val pdf = withContext(Dispatchers.IO) {
                        repository.save(latest)
                        ReportPdfGenerator.generate(context, latest, appSettings)
                    }
                    withContext(Dispatchers.IO) {
                        SharedDocumentsArchive.archiveFile(
                            context,
                            pdf,
                            "Raportit/PDF/Valvontapoytakirja",
                            pdf.name,
                            "application/pdf"
                        )
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_report)))
                }
            },
            onDeletePhoto = { photoPath ->
                repository.deletePhoto(photoPath)
                saveReport(active.copy(photos = active.photos.filterNot { it.filePath == photoPath }))
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
    onOpen: (InspectionReport) -> Unit,
    onDelete: (InspectionReport) -> Unit,
    onBackToLauncher: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Valvoja", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.loc3_waterproof_intro))
        TextButton(onClick = onBackToLauncher) { Text(stringResource(R.string.ui_back_reports)) }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_new_report))
        }
        if (templates.isNotEmpty()) {
            Text(stringResource(R.string.loc_templates), style = MaterialTheme.typography.titleLarge)
            templates.forEach { template ->
                OutlinedButton(onClick = { onCreateFromTemplate(template) }, modifier = Modifier.fillMaxWidth()) {
                    Text(template.templateName + " • " + template.siteAddress + " • " + (template.materials.firstOrNull { it.manufacturer.isNotBlank() }?.manufacturer ?: stringResource(R.string.manufacturer_missing)))
                }
            }
        }
        Text(stringResource(R.string.loc_saved), style = MaterialTheme.typography.titleLarge)
        if (reports.isEmpty()) {
            Text(stringResource(R.string.ui_no_reports))
        } else {
            reports.forEach { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(report) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(report.siteAddress.ifBlank { stringResource(R.string.new_report_fallback) }, fontWeight = FontWeight.SemiBold)
                        if (report.folderPath.isNotBlank()) Text(stringResource(R.string.loc3_folder) + report.folderPath, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.loc4_inspection_date_prefix, report.inspectionDate.ifBlank { "—" }))
                        Text(stringResource(R.string.loc4_result_prefix, report.inspectionResult.ifBlank { stringResource(R.string.ui_not_selected) }))
                        Text(stringResource(R.string.loc4_photo_count, report.photos.size), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDelete(report) }) { Text(stringResource(R.string.ui_delete)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.loc4_local_save_valvoja), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditorScreen(
    report: InspectionReport,
    customers: List<CustomerProfile>,
    sites: List<CustomerSite>,
    onChange: (InspectionReport) -> Unit,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onExport: () -> Unit,
    onPreview: () -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    var customerMenuExpanded by remember(report.id) { mutableStateOf(false) }
    var siteMenuExpanded by remember(report.id) { mutableStateOf(false) }
    val selectedCustomer = customers.firstOrNull { it.id == report.customerId }
    val availableSites = sites.filter { it.customerId == report.customerId }

    val defaultTemplateName = stringResource(R.string.new_template_fallback)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var reportLanguageMenu by remember(report.id) { mutableStateOf(false) }
        val pdfContext = LocalContext.current
        val reportLanguage = report.pdfLanguage.ifBlank {
            pdfContext.getSharedPreferences("tarkastus_pdf_language", android.content.Context.MODE_PRIVATE).getString("default", "fi") ?: "fi"
        }
        Text(stringResource(R.string.report_pdf_language), fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { reportLanguageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(when (reportLanguage) { "ru" -> "Русский"; "sv" -> "Svenska"; "en" -> "English"; else -> "Suomi" } + " ▾")
            }
            DropdownMenu(expanded = reportLanguageMenu, onDismissRequest = { reportLanguageMenu = false }) {
                listOf("fi" to "Suomi", "sv" to "Svenska", "ru" to "Русский", "en" to "English").forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        reportLanguageMenu = false
                        onChange(report.copy(pdfLanguage = code))
                    })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.ui_back)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onExport) { Text(stringResource(R.string.ui_create_pdf)) }
        }
        Text(stringResource(R.string.loc2_pdf_title_valvonta), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.loc_fill), style = MaterialTheme.typography.bodySmall)

        SectionTitle(stringResource(R.string.loc2_site_parties))
        Box {
            OutlinedButton(onClick = { customerMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedCustomer?.name ?: stringResource(R.string.ui_select_saved_customer))
            }
            DropdownMenu(expanded = customerMenuExpanded, onDismissRequest = { customerMenuExpanded = false }) {
                customers.forEach { customer ->
                    DropdownMenuItem(text = { Text(customer.name) }, onClick = {
                        onChange(report.copy(
                            customerId = customer.id,
                            siteId = "",
                            customerName = customer.name,
                            customerBusinessId = customer.businessId,
                            customerAddress = customer.address,
                            customerPostalCode = customer.postalCode,
                            customerCity = customer.city,
                            siteName = "",
                            siteAddress = "",
                            clientOrRepresentative = customer.contactPerson,
                            clientPhone = customer.phone,
                            clientEmail = customer.email,
                            siteFieldValues = emptyList()
                        ))
                        customerMenuExpanded = false
                    })
                }
            }
        }
        if (selectedCustomer != null) {
            Box {
                OutlinedButton(
                    onClick = { siteMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(report.siteName.ifBlank { stringResource(R.string.choose_site_fallback) })
                        report.siteAddress.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                DropdownMenu(expanded = siteMenuExpanded, onDismissRequest = { siteMenuExpanded = false }) {
                    availableSites.forEach { site ->
                        DropdownMenuItem(text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(site.name, fontWeight = FontWeight.Medium)
                                if (site.address.isNotBlank()) {
                                    Text(site.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }, onClick = {
                            val previousValues = report.siteFieldValues.associateBy { it.definitionId }
                            onChange(report.copy(
                                siteId = site.id,
                                siteName = site.name,
                                siteAddress = site.address,
                                siteFieldValues = site.fieldDefinitions.map { definition ->
                                    SiteFieldValue(
                                        definitionId = definition.id,
                                        label = definition.label,
                                        value = previousValues[definition.id]?.value.orEmpty()
                                    )
                                }
                            ))
                            siteMenuExpanded = false
                        })
                    }
                }
            }
            if (report.siteFieldValues.isNotEmpty()) {
                Text(stringResource(R.string.loc_site_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                report.siteFieldValues.forEachIndexed { index, field ->
                    Field(field.label, field.value) { value ->
                        onChange(report.copy(siteFieldValues = report.siteFieldValues.mapIndexed { fieldIndex, item ->
                            if (fieldIndex == index) item.copy(value = value) else item
                        }))
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.loc_site_parties))
        Field(stringResource(R.string.loc4_folder_path), report.folderPath) {
            onChange(report.copy(folderPath = it.trim('/')))
        }
        Field(stringResource(R.string.loc_site_address), report.siteAddress) { onChange(report.copy(siteAddress = it)) }
        Field(stringResource(R.string.loc4_client_rep), report.clientOrRepresentative) { onChange(report.copy(clientOrRepresentative = it)) }
        Field(stringResource(R.string.loc_phone), report.clientPhone) { onChange(report.copy(clientPhone = it)) }
        Field(stringResource(R.string.loc_email), report.clientEmail) { onChange(report.copy(clientEmail = it)) }
        Field(stringResource(R.string.loc2_installer), report.installerName) { onChange(report.copy(installerName = it)) }
        Field(stringResource(R.string.loc2_certificate), report.installerCertificateNumber) { onChange(report.copy(installerCertificateNumber = it)) }
        Field(stringResource(R.string.loc2_installer_phone), report.installerPhone) { onChange(report.copy(installerPhone = it)) }
        Field(stringResource(R.string.loc2_installer_email), report.installerEmail) { onChange(report.copy(installerEmail = it)) }
        DateField(stringResource(R.string.loc2_start_date), report.workStartDate) {
            onChange(report.copy(workStartDate = it))
        }
        DateField(stringResource(R.string.loc2_end_date), report.workEndDate) {
            onChange(report.copy(workEndDate = it))
        }

        SectionTitle(stringResource(R.string.loc2_surfaces))
        Field(stringResource(R.string.loc4_wall_base), report.wallSubstrate) { onChange(report.copy(wallSubstrate = it)) }
        Field(stringResource(R.string.loc4_floor_base), report.floorSubstrate) { onChange(report.copy(floorSubstrate = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loc2_instructions), modifier = Modifier.weight(1f))
            Switch(
                checked = report.workInstructionsProvidedAndRead,
                onCheckedChange = { onChange(report.copy(workInstructionsProvidedAndRead = it)) }
            )
        }
        Field(stringResource(R.string.loc2_temperature), report.roomTemperatureC) { onChange(report.copy(roomTemperatureC = it)) }
        Field(stringResource(R.string.loc2_humidity), report.relativeHumidityPercent) { onChange(report.copy(relativeHumidityPercent = it)) }
        Field(stringResource(R.string.loc2_substrate_moisture), report.substrateMoisturePercent) { onChange(report.copy(substrateMoisturePercent = it)) }

        SectionTitle(stringResource(R.string.loc_materials))
        Text(stringResource(R.string.loc4_family_hint))
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

        // Migrate old reports with one Tasoite entry into separate wall and floor entries.
        LaunchedEffect(report.id, report.materials) {
            val screedEntries = report.materials.filter { it.category.equals("tasoite", ignoreCase = true) }
            if (screedEntries.size == 1) {
                val existing = screedEntries.first()
                val updated = report.materials.map {
                    if (it.id == existing.id) it.copy(surface = "Seinä") else it
                } + existing.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    surface = "Lattia",
                    productName = "",
                    notes = ""
                )
                onChange(report.copy(materials = updated))
            } else if (screedEntries.size > 1 && screedEntries.any { it.surface.isBlank() }) {
                var assigned = 0
                val updated = report.materials.map { item ->
                    if (item.category.equals("tasoite", ignoreCase = true) && item.surface.isBlank()) {
                        item.copy(surface = if (assigned++ == 0) "Seinä" else "Lattia")
                    } else item
                }
                onChange(report.copy(materials = updated))
            }
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

        val waterproofingMaterial = report.materials.firstOrNull {
            it.category.trim().equals("vedeneriste", ignoreCase = true)
        }
        val waterproofingSpecKey = waterproofingMaterial
            ?.takeIf { it.productName.isNotBlank() }
            ?.let { "${it.manufacturer.trim()}|${it.productName.trim()}" }
            .orEmpty()

        // Apply manufacturer figures when the selected membrane product changes.
        // User edits remain intact until a different product is selected.
        LaunchedEffect(report.id, waterproofingSpecKey, report.waterproofingSpecProductKey) {
            if (waterproofingSpecKey.isNotBlank() &&
                waterproofingSpecKey != report.waterproofingSpecProductKey
            ) {
                val selected = waterproofingMaterial
                val spec = selected?.let {
                    MaterialCatalog.waterproofingSpec(it.manufacturer, it.productName)
                }
                onChange(
                    report.copy(
                        wallRequiredWaterproofingConsumptionPerM2 = spec?.wallConsumptionPerM2.orEmpty(),
                        floorRequiredWaterproofingConsumptionPerM2 = spec?.floorConsumptionPerM2.orEmpty(),
                        wallRequiredMinimumThicknessMm = spec?.wallDryFilmThicknessMm.orEmpty(),
                        floorRequiredMinimumThicknessMm = spec?.floorDryFilmThicknessMm.orEmpty(),
                        waterproofingSpecProductKey = waterproofingSpecKey,
                        waterproofingSpecSource = spec?.source.orEmpty()
                    )
                )
            } else if (waterproofingSpecKey.isBlank() && report.waterproofingSpecProductKey.isNotBlank()) {
                onChange(report.copy(waterproofingSpecProductKey = "", waterproofingSpecSource = ""))
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
        ) { Text(stringResource(R.string.ui_add_material)) }

        SectionTitle(stringResource(R.string.loc2_structures))
        Field(stringResource(R.string.loc2_heating), report.floorHeating) { onChange(report.copy(floorHeating = it)) }
        Field(stringResource(R.string.loc4_threshold), report.thresholdOrDrainLiftMm) { onChange(report.copy(thresholdOrDrainLiftMm = it)) }
        Text(stringResource(R.string.ui_floor_slope))
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
        Field(stringResource(R.string.loc4_slopes_info), report.floorSlopes, minLines = 2) {
            onChange(report.copy(floorSlopes = it))
        }

        SectionTitle(stringResource(R.string.loc2_areas))
        if (report.waterproofingSpecSource.isNotBlank()) {
            Text(
                stringResource(R.string.ui_prefilled_material_warning, report.waterproofingSpecSource),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Field(stringResource(R.string.loc2_wall_area), report.wallAreaM2) { onChange(report.copy(wallAreaM2 = it)) }
        Field(stringResource(R.string.loc4_wall_consumption), report.wallRequiredWaterproofingConsumptionPerM2) {
            onChange(report.copy(wallRequiredWaterproofingConsumptionPerM2 = it))
        }
        Field(stringResource(R.string.loc3_wall_coating), report.wallWaterproofingConsumptionLitres) {
            onChange(report.copy(wallWaterproofingConsumptionLitres = it))
        }
        Field(stringResource(R.string.loc2_floor_area), report.floorAreaM2) { onChange(report.copy(floorAreaM2 = it)) }
        Field(stringResource(R.string.loc4_floor_consumption), report.floorRequiredWaterproofingConsumptionPerM2) {
            onChange(report.copy(floorRequiredWaterproofingConsumptionPerM2 = it))
        }
        Field(stringResource(R.string.loc3_floor_coating), report.floorWaterproofingConsumptionLitres) {
            onChange(report.copy(floorWaterproofingConsumptionLitres = it))
        }

        SectionTitle(stringResource(R.string.loc2_film))
        Field(stringResource(R.string.loc3_wall_film), report.wallDryFilmThicknessMm) { onChange(report.copy(wallDryFilmThicknessMm = it)) }
        Field(stringResource(R.string.loc4_wall_minfilm), report.wallRequiredMinimumThicknessMm) { onChange(report.copy(wallRequiredMinimumThicknessMm = it)) }
        Field(stringResource(R.string.loc3_floor_film), report.floorDryFilmThicknessMm) { onChange(report.copy(floorDryFilmThicknessMm = it)) }
        Field(stringResource(R.string.loc4_floor_minfilm), report.floorRequiredMinimumThicknessMm) { onChange(report.copy(floorRequiredMinimumThicknessMm = it)) }

        SectionTitle(stringResource(R.string.loc_photos_section))
        Text(stringResource(R.string.loc2_pdf_photo_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_camera)) }
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_gallery)) }
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

        SectionTitle(stringResource(R.string.loc_result_section))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (report.inspectionResult == "HYVÄKSYTTY") {
                Button(
                    onClick = { onChange(report.copy(inspectionResult = "HYVÄKSYTTY")) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF168447))
                ) { Text(stringResource(R.string.ui_approved)) }
            } else {
                OutlinedButton(
                    onClick = { onChange(report.copy(inspectionResult = "HYVÄKSYTTY")) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.ui_approved)) }
            }
            if (report.inspectionResult == "HYLÄTTY") {
                Button(
                    onClick = { onChange(report.copy(inspectionResult = "HYLÄTTY")) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFC62828))
                ) { Text(stringResource(R.string.ui_rejected)) }
            } else {
                OutlinedButton(
                    onClick = { onChange(report.copy(inspectionResult = "HYLÄTTY")) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.ui_rejected)) }
            }
        }
        Field(stringResource(R.string.loc2_additional_notes), report.additionalNotes, minLines = 3) { onChange(report.copy(additionalNotes = it)) }

        SectionTitle(stringResource(R.string.loc2_inspection_date_section))
        DateField(stringResource(R.string.loc_date), report.inspectionDate) {
            onChange(report.copy(inspectionDate = it))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onSaveTemplate(report.siteAddress.ifBlank { defaultTemplateName }) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_save_template))
        }
        OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_preview_pdf)) }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_share_pdf)) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_back)) }
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
            Text(displayDate.ifBlank { stringResource(R.string.ui_choose_date) })
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
                    Text(stringResource(R.string.ui_select))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.ui_cancel))
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
private fun localizedMaterialValue(value: String): String {
    val language = AppLanguage.selected(LocalContext.current)
    if (language == "fi") return value
    val labels = mapOf(
        "tasoite" to listOf("Шпаклёвка / выравнивающая смесь", "Levelling compound", "Spackel / avjämningsmassa"),
        "pohjuste (primer)" to listOf("Грунтовка (праймер)", "Primer", "Primer"),
        "vedeneriste" to listOf("Гидроизоляция", "Waterproofing membrane", "Tätskikt"),
        "kaivotyyppi" to listOf("Тип трапа", "Floor drain type", "Golvbrunnstyp"),
        "kaivolaippa / lattiakaivovahvike" to listOf("Манжета / усиление трапа", "Drain flange / reinforcement", "Brunnsmanschett / förstärkning"),
        "nurkkavahvike / vahvikenauha" to listOf("Угловое усиление / армирующая лента", "Corner reinforcement / reinforcing tape", "Hörnförstärkning / armeringsband"),
        "läpivientivahvike" to listOf("Манжета для проходок", "Pipe penetration reinforcement", "Rörgenomföringsförstärkning"),
        "läpivientien tiivistysmassa" to listOf("Герметик для проходок", "Penetration sealant", "Tätningsmassa för genomföringar"),
        "kiinnityslaasti" to listOf("Плиточный клей", "Tile adhesive", "Fästmassa"),
        "laatat" to listOf("Плитка", "Tiles", "Plattor"),
        "seinäsaumaus" to listOf("Затирка швов стен", "Wall tile grouting", "Fogning av väggplattor"),
        "lattiasaumaus" to listOf("Затирка швов пола", "Floor tile grouting", "Fogning av golvplattor"),
        "elastinen saumausmassa" to listOf("Эластичный герметик", "Elastic joint sealant", "Elastisk fogmassa"),
        "saumalaasti" to listOf("Затирочная смесь", "Grout", "Fogmassa"),
        "seinä" to listOf("Стена", "Wall", "Vägg"),
        "seinät" to listOf("Стены", "Walls", "Väggar"),
        "lattia" to listOf("Пол", "Floor", "Golv"),
        "lattiat" to listOf("Полы", "Floors", "Golv")
    )
    val index = when (language) { "ru" -> 0; "en" -> 1; "sv" -> 2; else -> return value }
    return labels[value.trim().lowercase()]?.get(index) ?: value
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
                Text(localizedMaterialValue(material.category), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text(stringResource(R.string.ui_delete)) }
            }
            if (lockedFamilyManufacturer && !isFamilyAnchor) {
                Text(stringResource(R.string.manufacturer_family, familyManufacturer))
            } else if (manufacturers.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { manufacturerExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(effectiveManufacturer.ifBlank { stringResource(R.string.ui_choose_manufacturer) })
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
                        Text(selectedProductName.ifBlank { stringResource(R.string.ui_choose_product) })
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
                                text = { Text(stringResource(R.string.loc4_other_product)) },
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
                Field(stringResource(R.string.loc_manufacturer), material.manufacturer) { onChange(material.copy(manufacturer = it)) }
                Field(stringResource(R.string.loc_product_name), material.productName) { onChange(material.copy(productName = it)) }
            } else if (productOptions.isEmpty()) {
                Text(stringResource(R.string.loc4_family_missing))
            }
            if (material.category.equals("vedeneriste", ignoreCase = true) ||
                material.category.equals("tasoite", ignoreCase = true)
            ) {
                Text(if (material.surface.isBlank()) stringResource(R.string.ui_surface_unspecified) else localizedMaterialValue(material.surface))
            }
            Field(stringResource(R.string.loc_additional), material.notes) { onChange(material.copy(notes = it)) }
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
                Text(stringResource(R.string.loc4_image_failed, File(photo.filePath).name))
            }
            Field(stringResource(R.string.loc_comment), photo.caption, minLines = 2, onValueChange = onCaptionChange)
            TextButton(onClick = onDelete) { Text(stringResource(R.string.ui_remove_photo)) }
        }
    }
}

package fi.vladi.valvontapoytakirja

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage

private var activeCustomWorker: kotlinx.coroutines.Job? = null
private val customSaveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

private fun <T> List<T>.replaceAt(i: Int, v: T) = toMutableList().apply { this[i] = v }.toList()
private fun <T> List<T>.moveAt(i: Int, delta: Int): List<T> = if (i+delta !in indices) this else toMutableList().apply { add(i+delta, removeAt(i)) }
@Composable
internal fun CustomReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = AppLanguage.selected(context)
    val l = remember(language) { customLabels(language) }
    val store = remember { CustomStore(context.applicationContext) }
    val loadedState by produceState<Result<Pair<CustomLibrary, String?>>?>(null, store) {
        activeCustomWorker?.join()
        value = withContext(Dispatchers.IO) { runCatching { store.load() to store.activeEditor() } }
    }
    val loaded = loadedState
    if (loaded == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        return
    }
    var library by remember { mutableStateOf(loaded.getOrNull()?.first ?: CustomLibrary()) }
    var template by remember { mutableStateOf(library.templates.firstOrNull { "t:" + it.id == loaded.getOrNull()?.second }) }
    var document by remember { mutableStateOf(library.documents.firstOrNull { "d:" + it.id == loaded.getOrNull()?.second }) }
    var message by remember { mutableStateOf(if (loaded.isFailure) l[24] else "") }
    val scope = rememberCoroutineScope()
    val writer = remember { kotlinx.coroutines.sync.Mutex() }
    var pendingWrites by remember { mutableStateOf(0) }
    val queued = remember { CustomWriteQueue<suspend () -> Unit>() }
    val failedKeys = remember { mutableStateMapOf<String, Boolean>() }
    var worker by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun enqueue(action: () -> Unit, key: String = customId(), done: () -> Unit = {}) {
        queued.put(key) { withContext(Dispatchers.IO) { action() }; failedKeys.remove(key); done() }
        pendingWrites = queued.size + 1
        if (worker?.isActive == true) return
        worker = customSaveScope.launch {
            while (queued.isNotEmpty()) {
                kotlinx.coroutines.delay(120)
                val entry = queued.take() ?: break
                val task = entry.second
                try {
                    writer.lock()
                    try { task() } finally { writer.unlock() }
                } catch (e: Exception) {
                    failedKeys[entry.first] = true
                    message = l[24]
                }
                pendingWrites = queued.size + 1
            }
            pendingWrites = 0
        }
        activeCustomWorker = worker
    }
    fun persist(next: CustomLibrary) {
        if (loaded.isFailure) { message = l[24]; return }
        library = next
        enqueue({ store.save(next) }) { message = l[23] }
    }
    LaunchedEffect(Unit) {
        if (loaded.isSuccess) withContext(Dispatchers.IO) { runCatching { store.cleanUnused(loaded.getOrThrow().first) } }
    }
    // Persist each committed editor change atomically. Do not overwrite unreadable libraries.
    fun updateDocument(next: CustomDocument) {
        if (loaded.isFailure) { message = l[24]; return }
        document = next
        enqueue({ store.saveDocument(next); store.rememberEditor("d:" + next.id) }, key = "document:" + next.id)
    }
    fun updateTemplate(next: CustomTemplate) {
        if (loaded.isFailure) { message = l[24]; return }
        template = next
        enqueue({ store.saveTemplate(next); store.rememberEditor("t:" + next.id) }, key = "template:" + next.id)
    }
    var busy by remember { mutableStateOf(false) }
    var imports by remember { mutableStateOf(0) }
    var deletion by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val extra = when (language.substringBefore("-")) {
        "fi" -> listOf("Käytä yhteisiä asetuksia", "Poistetaanko?")
        "ru" -> listOf("Использовать общие настройки", "Удалить выбранный элемент?")
        "sv" -> listOf("Använd gemensamma inställningar", "Ta bort objektet?")
        "nb" -> listOf("Bruk felles innstillinger", "Slette elementet?")
        "da" -> listOf("Brug fælles indstillinger", "Slet elementet?")
        "et" -> listOf("Kasuta ühiseid seadeid", "Kas kustutada?")
        "lv" -> listOf("Izmantot kopīgos iestatījumus", "Vai dzēst?")
        "lt" -> listOf("Naudoti bendrus nustatymus", "Ar ištrinti?")
        "fr" -> listOf("Utiliser les paramètres communs", "Supprimer cet élément ?")
        "es" -> listOf("Usar ajustes comunes", "¿Eliminar este elemento?")
        "it" -> listOf("Usa impostazioni comuni", "Eliminare questo elemento?")
        "de" -> listOf("Gemeinsame Einstellungen verwenden", "Element löschen?")
        "pl" -> listOf("Użyj wspólnych ustawień", "Usunąć element?")
        else -> listOf("Use shared settings", "Delete this item?")
    }
    deletion?.let { (isTemplate, id) ->
        AlertDialog(onDismissRequest = { deletion = null }, title = { Text(extra[1]) },
            confirmButton = { TextButton(onClick = {
                deletion = null
                enqueue({
                    if (isTemplate) store.deleteTemplate(id) else store.deleteDocument(id)
                }) {
                    library = if (isTemplate) library.copy(templates = library.templates.filterNot { it.id == id })
                        else library.copy(documents = library.documents.filterNot { it.id == id })
                }
            }) { Text(l[17]) } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text(l[16]) } })
    }
    fun back() {
        if (busy || pendingWrites > 0 || imports > 0) return
        val d = document
        val t = template
        if (d != null) {
            if (loaded.isFailure) { message = l[24]; return }
            val next = library.copy(documents = library.documents.filterNot { it.id == d.id } + d)
            enqueue({ store.saveDocument(d) }, key = "document:" + d.id) { if (document == d) { library = next; document = null; enqueue({ store.rememberEditor("") }) } }
        } else if (t != null) {
            if (t.title.isBlank()) { enqueue({ store.deleteTemplate(t.id); store.rememberEditor("") }) { library = library.copy(templates = library.templates.filterNot { it.id == t.id }); template = null }; return }
            if (loaded.isFailure) { message = l[24]; return }
            val next = library.copy(templates = library.templates.filterNot { it.id == t.id } + t)
            enqueue({ store.saveTemplate(t) }, key = "template:" + t.id) { if (template == t) { library = next; template = null; enqueue({ store.rememberEditor("") }) } }
        } else onBack()
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(l[0], style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(enabled = !busy && imports == 0 && pendingWrites == 0, onClick = { back() }) { Text(l[16]) }
        if (message.isNotBlank()) Text(message)
        if (pendingWrites > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
        val d = document
        val t = template
        if (d != null) {
            CustomPdfLanguage(d.pdfLanguage.ifBlank { AppLanguage.selected(context) }) { language -> document?.let { updateDocument(it.copy(pdfLanguage = language)) } }
            Text(d.template.title, style = MaterialTheme.typography.titleLarge)
            Row { Switch(d.useSharedSettings, { enabled -> document?.let { updateDocument(it.copy(useSharedSettings = enabled)) } }); Text(extra[0]) }
            if (d.useSharedSettings) {
                val sharedLabels = when (language.substringBefore("-")) {
                    "fi" -> listOf("Tilaaja", "Kohde", "Tarkastaja")
                    "ru" -> listOf("Клиент", "Объект", "Контролёр")
                    "sv" -> listOf("Beställare", "Objekt", "Kontrollant")
                    "nb" -> listOf("Kunde", "Objekt", "Kontrollør")
                    "da" -> listOf("Kunde", "Objekt", "Kontrollør")
                    "et" -> listOf("Tellija", "Objekt", "Kontrollija")
                    "lv" -> listOf("Pasūtītājs", "Objekts", "Inspektors")
                    "lt" -> listOf("Užsakovas", "Objektas", "Inspektorius")
                    "fr" -> listOf("Client", "Site", "Inspecteur")
                    "es" -> listOf("Cliente", "Obra", "Inspector")
                    "it" -> listOf("Cliente", "Sito", "Ispettore")
                    "de" -> listOf("Auftraggeber", "Objekt", "Prüfer")
                    "pl" -> listOf("Klient", "Obiekt", "Inspektor")
                    else -> listOf("Customer", "Site", "Inspector")
                }
                val repository = remember(context) { fi.vladi.valvontapoytakirja.data.ReportRepository(context) }
                val customers = remember(repository) { repository.listCustomers() }
                val sites = remember(repository, d.customerId) { repository.listSites(d.customerId.takeIf { it.isNotBlank() }) }
                val inspectors = remember(repository) { repository.listInspectors() }
                CustomSharedChoice(sharedLabels[0], d.customerText, customers.map { it.id to it.name }) { id ->
                    val selected = customers.firstOrNull { it.id == id }
                    document?.let { updateDocument(it.copy(customerId = id, customerText = selected?.let { p -> listOf(p.name,p.businessId,p.address,listOf(p.postalCode,p.city).filter { v -> v.isNotBlank() }.joinToString(" "),p.contactPerson,p.phone,p.email).filter { v -> v.isNotBlank() }.joinToString("\n") }.orEmpty(),siteId = "",siteText = "")) }
                }
                CustomSharedChoice(sharedLabels[1], d.siteText, sites.map { it.id to it.name }) { id ->
                    val selected = sites.firstOrNull { it.id == id }
                    document?.let { updateDocument(it.copy(siteId = id, siteText = selected?.let { p -> listOf(p.name,p.address).filter { v -> v.isNotBlank() }.joinToString("\n") }.orEmpty())) }
                }
                CustomSharedChoice(sharedLabels[2], d.inspectorText, inspectors.map { it.id to it.name }) { id ->
                    val selected = inspectors.firstOrNull { it.id == id }
                    document?.let { updateDocument(it.copy(inspectorId = id, inspectorText = selected?.let { p -> listOf(p.name,p.certificateNumber,p.phone,p.email).filter { v -> v.isNotBlank() }.joinToString("\n") }.orEmpty())) }
                }
            }
            for (section in d.template.sections) {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                for (field in section.fields) key(field.id) {
                    val value = d.values[field.id] ?: field.defaultValue
                    val updateLatest by rememberUpdatedState<(CustomDocument) -> Unit>({ updateDocument(it) })
                    val onValue = remember(field.id) { { v: String -> document?.let { updateLatest(it.copy(values = it.values + (field.id to v))) }; Unit } }
                    val onImages = remember(field.id) { { v: List<String> -> document?.let { updateLatest(it.copy(attachments = it.attachments + (field.id to v))) }; Unit } }
                    CustomValueEditor(field, value, d.attachments[field.id].orEmpty(), l, store,
                        onValue = onValue,
                        onImages = onImages,
                        onError = { message = l[24] }, onImport = { started -> imports += if (started) 1 else -1 })
                }
            }
            fun valid(): Boolean = d.template.sections.flatMap { it.fields }.all { f ->
                !f.required || f.kind == CustomKind.HEADING || if (f.kind in listOf(CustomKind.PHOTOS, CustomKind.SIGNATURE)) !d.attachments[f.id].isNullOrEmpty() else if (f.kind == CustomKind.TABLE) customRows(d.values[f.id] ?: f.defaultValue, customColumns(f).size).any { row -> row.any { it.isNotBlank() } } else (d.values[f.id] ?: f.defaultValue).isNotBlank()
            }
            Button(onClick = { enqueue({ store.saveDocument(d); store.rememberEditor("d:" + d.id) }, key = "document:" + d.id) { message = l[23] } }) { Text(l[14]) }
            Button(enabled = !busy && imports == 0 && pendingWrites == 0 && failedKeys.isEmpty(), onClick = {
                if (!valid()) message = l[22] else {
                    busy = true
                    scope.launch {
                        try { withContext(Dispatchers.IO) { exportCustomPdf(context, store, d) }; message = l[23] }
                        catch (e: Exception) { android.util.Log.e("CustomReport", "PDF export failed", e); message = l[24] }
                        finally { busy = false }
                    }
                }
            }) { Text(l[15]) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (t != null) {
            OutlinedTextField(t.title, { updateTemplate(t.copy(title = it)) }, label = { Text(l[4]) }, modifier = Modifier.fillMaxWidth())
            for ((si, section) in t.sections.withIndex()) key(section.id) {
                fun update(s: CustomSection) { updateTemplate(t.copy(sections = t.sections.replaceAt(si,s))) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(section.title, { update(section.copy(title = it)) }, label = { Text(l[5]) })
                    Row { TextButton(onClick = { updateTemplate(t.copy(sections = t.sections.moveAt(si,-1))) }) { Text("↑") }; TextButton(onClick = { updateTemplate(t.copy(sections = t.sections.moveAt(si,1))) }) { Text("↓") }; TextButton(onClick = { updateTemplate(t.copy(sections = t.sections.filterNot { it.id == section.id })) }) { Text(l[17]) } }
                    for ((fi, f) in section.fields.withIndex()) key(f.id) {
                        fun change(v: CustomField) { update(section.copy(fields = section.fields.replaceAt(fi,v))) }
                        OutlinedTextField(f.label, { change(f.copy(label = it)) }, label = { Text(l[8]) })
                        var menu by remember { mutableStateOf(false) }
                        Box { OutlinedButton(onClick = { menu = true }) { Text(l[25+f.kind.ordinal]) }; DropdownMenu(menu,{ menu = false }) { CustomKind.entries.forEach { k -> DropdownMenuItem(text = { Text(l[25+k.ordinal]) }, onClick = { change(f.copy(kind = k)); menu = false }) } } }
                        if (f.kind == CustomKind.PHOTOS) CustomPhotoLayout(f, { change(it) })
                        if (f.kind != CustomKind.HEADING) {
                            Row { Checkbox(f.required,{ change(f.copy(required = it)) }); Text(l[9]) }
                            if (f.kind in listOf(CustomKind.SINGLE,CustomKind.MULTIPLE,CustomKind.TABLE)) OutlinedTextField(f.options.joinToString("\n"), { change(f.copy(options = it.split("\n"))) }, label = { Text(l[10]) })
                            if (f.kind !in listOf(CustomKind.PHOTOS,CustomKind.SIGNATURE,CustomKind.TABLE)) OutlinedTextField(f.defaultValue,{ change(f.copy(defaultValue = it)) },label = { Text(l[11]) })
                        }
                        Row { TextButton(onClick = { update(section.copy(fields = section.fields.moveAt(fi,-1))) }) { Text("↑") }; TextButton(onClick = { update(section.copy(fields = section.fields.moveAt(fi,1))) }) { Text("↓") }; }
                        Row { TextButton(onClick = { update(section.copy(fields = section.fields + f.copy(id = customId()))) }) { Text(l[18]) }; TextButton(onClick = { update(section.copy(fields = section.fields.filterNot { it.id == f.id })) }) { Text(l[17]) } }
                        HorizontalDivider()
                    }
                    OutlinedButton(onClick = { update(section.copy(fields = section.fields + CustomField())) }) { Text(l[7]) }
                } }
            }
            OutlinedButton(onClick = { updateTemplate(t.copy(sections = t.sections + CustomSection())) }) { Text(l[6]) }
            Button(onClick = { persist(library.copy(templates = library.templates.filterNot { it.id == t.id } + t)) }, enabled = t.title.isNotBlank()) { Text(l[12]) }
            Button(onClick = { library = library.copy(templates = library.templates.filterNot { it.id == t.id } + t); template = null; updateDocument(CustomDocument(template = t, pdfLanguage = AppLanguage.selected(context))) }, enabled = t.title.isNotBlank()) { Text(l[13]) }
        } else {
            Button(onClick = { updateTemplate(CustomTemplate()) }) { Text(l[1]) }
            CustomTemplateTransfer(library.templates, { imported -> persist(library.copy(templates = library.templates + imported.map { it.copy(id = customId()) })) }, { message = l[24] }, { message = l[23] })
            Text(l[2], style = MaterialTheme.typography.titleMedium)
            val editLabel = when (language.substringBefore("-")) {
                "ru" -> "Редактировать"; "fi" -> "Muokkaa"; "sv" -> "Redigera"; "nb" -> "Rediger"; "da" -> "Rediger"; "et" -> "Muuda"; "lv" -> "Rediģēt"; "lt" -> "Redaguoti"; "fr" -> "Modifier"; "es" -> "Editar"; "it" -> "Modifica"; "de" -> "Bearbeiten"; "pl" -> "Edytuj"; else -> "Edit"
            }
            val ready = pendingWrites == 0 && !busy && loaded.isSuccess
            for (item in library.templates) key(item.id) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title.ifBlank { l[4] }, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(enabled = ready, onClick = { updateTemplate(item) }, modifier = Modifier.fillMaxWidth()) { Text(editLabel) }
                        OutlinedButton(enabled = ready && item.title.isNotBlank(), onClick = { updateDocument(CustomDocument(template = item, pdfLanguage = AppLanguage.selected(context))) }, modifier = Modifier.fillMaxWidth()) { Text(l[13]) }
                        Row {
                            TextButton(enabled = ready && item.title.isNotBlank(), onClick = { updateTemplate(item.copy(id = customId())) }) { Text(l[18]) }
                            TextButton(enabled = ready, onClick = { deletion = true to item.id }) { Text(l[17], color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            Text(l[3], style = MaterialTheme.typography.titleMedium)
            for (item in library.documents) key(item.id) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedButton(enabled = ready, onClick = { updateDocument(item) }, modifier = Modifier.fillMaxWidth()) { Text(item.template.title.ifBlank { l[4] }) }
                        TextButton(enabled = ready, onClick = { deletion = false to item.id }) { Text(l[17], color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
@Composable
private fun CustomValueEditor(f: CustomField, value: String, images: List<String>, l: List<String>, store: CustomStore, onValue: (String)->Unit, onImages: (List<String>)->Unit, onError: ()->Unit, onImport: (Boolean)->Unit) {
    Text(f.label + if (f.required) " *" else "")
    when (f.kind) {
        CustomKind.HEADING -> Unit
        CustomKind.CHECKBOX -> Checkbox(value == "true", { onValue(it.toString()) })
        CustomKind.SINGLE, CustomKind.MULTIPLE -> f.options.filter { it.isNotBlank() }.distinct().forEach { option ->
            val selected = if (f.kind == CustomKind.SINGLE) value == option else option in value.split("\n")
            Row { Checkbox(selected, { checked -> onValue(if (f.kind == CustomKind.SINGLE) { if (checked) option else "" } else { val set = value.split("\n").filter { it.isNotBlank() }.toMutableSet(); if (checked) set.add(option) else set.remove(option); set.joinToString("\n") }) }); Text(option) }
        }
        CustomKind.DATE_TIME -> CustomDateTime(value, onValue)
        CustomKind.TABLE -> CustomTable(f, value, onValue, l)
        CustomKind.SIGNATURE -> CustomSignature(images, store, onImages, onError, l)
        CustomKind.PHOTOS -> CustomPhotos(images, store, onImages, onError, l, onImport)
        else -> OutlinedTextField(value,onValue, modifier = Modifier.fillMaxWidth(), singleLine = f.kind !in listOf(CustomKind.LONG_TEXT,CustomKind.TABLE), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (f.kind == CustomKind.NUMBER) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text))
    }
}

@Composable
private fun CustomSharedChoice(label: String, selected: String, options: List<Pair<String,String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label + "  " + selected.substringBefore("\n").ifBlank { "—" }) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("—") }, onClick = { open = false; onSelect("") })
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(id) }) }
        }
    }
}

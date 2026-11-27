package fi.vladi.valvontapoytakirja

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class TemplateExchange(val version: Int = 1, val templates: List<CustomTemplate>)

private val languages = linkedMapOf("en" to "English","fi" to "Suomi","sv" to "Svenska","nb" to "Norsk","da" to "Dansk","et" to "Eesti","lv" to "Latviešu","lt" to "Lietuvių","ru" to "Русский","fr" to "Français","es" to "Español","it" to "Italiano","de" to "Deutsch","pl" to "Polski")
private fun transferLabels(language: String) = (mapOf(
"en" to "PDF language|Import templates|Export / backup templates",
"fi" to "PDF:n kieli|Tuo mallipohjat|Vie / varmuuskopioi mallipohjat",
"sv" to "PDF-språk|Importera mallar|Exportera / säkerhetskopiera mallar",
"nb" to "PDF-språk|Importer maler|Eksporter / sikkerhetskopier maler",
"da" to "PDF-sprog|Importér skabeloner|Eksportér / sikkerhedskopiér skabeloner",
"et" to "PDF-i keel|Impordi mallid|Ekspordi / varunda mallid",
"lv" to "PDF valoda|Importēt veidnes|Eksportēt / dublēt veidnes",
"lt" to "PDF kalba|Importuoti šablonus|Eksportuoti / kopijuoti šablonus",
"ru" to "Язык PDF|Импорт шаблонов|Экспорт / резервная копия шаблонов",
"fr" to "Langue du PDF|Importer les modèles|Exporter / sauvegarder les modèles",
"es" to "Idioma del PDF|Importar plantillas|Exportar / respaldar plantillas",
"it" to "Lingua PDF|Importa modelli|Esporta / salva modelli",
"de" to "PDF-Sprache|Vorlagen importieren|Vorlagen exportieren / sichern",
"pl" to "Język PDF|Importuj szablony|Eksportuj / archiwizuj szablony"
)[language] ?: "PDF language|Import templates|Export / backup templates").split("|")

@Composable
internal fun CustomPdfLanguage(value: String, onChange: (String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Text(transferLabels(AppLanguage.selected(LocalContext.current))[0])
    Box {
        OutlinedButton(onClick = { open = true }) { Text(languages[value] ?: value) }
        DropdownMenu(open,{ open = false }) {
            languages.forEach { (code,name) -> DropdownMenuItem(text = { Text(name) },onClick = { onChange(code); open = false }) }
        }
    }
}
@Composable
internal fun CustomTemplateTransfer(templates: List<CustomTemplate>, onImport: (List<CustomTemplate>)->Unit, onError: ()->Unit, onSuccess: ()->Unit) {
    val context = LocalContext.current
    val labels = transferLabels(AppLanguage.selected(context))
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val latest by rememberUpdatedState(templates)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { java.io.ByteArrayOutputStream().use { out -> val buffer = ByteArray(8192); var total = 0; while (true) { val n = it.read(buffer); if (n < 0) break; total += n; require(total <= 5_000_000); out.write(buffer,0,n) }; out.toByteArray() } } ?: error("Cannot open")
            require(bytes.size <= 5_000_000)
            val exchange = json.decodeFromString<TemplateExchange>(bytes.decodeToString())
            require(exchange.version == 1)
            exchange.templates.forEach { t ->
                require(t.title.isNotBlank())
                val ids = t.sections.flatMap { it.fields }.map { it.id }
                require(ids.distinct().size == ids.size)
                require(t.sections.map { it.id }.distinct().size == t.sections.size)
            }
            exchange.templates
        }.onSuccess(onImport).onFailure { onError() }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.encodeToString(TemplateExchange(templates = latest)).encodeToByteArray()) } ?: error("Cannot write")
        }.onSuccess { onSuccess() }.onFailure { onError() }
    }
    OutlinedButton(onClick = { importer.launch(arrayOf("application/json","text/plain","application/octet-stream")) }) { Text(labels[1]) }
    OutlinedButton(onClick = { exporter.launch("TarkastusCloud_templates.json") },enabled = templates.isNotEmpty()) { Text(labels[2]) }
}

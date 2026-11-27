package fi.vladi.valvontapoytakirja

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage

internal fun CustomField.gridColumns() = photoColumns.coerceIn(1,4)
internal fun CustomField.gridRows() = photoRows.coerceIn(1,6)

@Composable
internal fun CustomPhotoLayout(f: CustomField, onChange: (CustomField)->Unit) {
    val language = AppLanguage.selected(LocalContext.current)
    val labels = (mapOf(
        "en" to "Columns|Rows|Separate photo pages|Photos per page",
        "fi" to "Sarakkeet|Rivit|Erilliset kuvasivut|Kuvia sivulla",
        "sv" to "Kolumner|Rader|Separata fotosidor|Foton per sida",
        "nb" to "Kolonner|Rader|Separate bildesider|Bilder per side",
        "da" to "Kolonner|Rækker|Separate fotosider|Fotos pr. side",
        "et" to "Veerud|Read|Eraldi fotolehed|Fotosid lehel",
        "lv" to "Kolonnas|Rindas|Atsevišķas foto lapas|Foto lapā",
        "lt" to "Stulpeliai|Eilutės|Atskiri nuotraukų puslapiai|Nuotraukų puslapyje",
        "ru" to "Столбцы|Ряды|Отдельные фотостраницы|Фотографий на странице",
        "fr" to "Colonnes|Lignes|Pages photo séparées|Photos par page",
        "es" to "Columnas|Filas|Páginas de fotos separadas|Fotos por página",
        "it" to "Colonne|Righe|Pagine foto separate|Foto per pagina",
        "de" to "Spalten|Zeilen|Separate Fotoseiten|Fotos pro Seite",
        "pl" to "Kolumny|Wiersze|Oddzielne strony zdjęć|Zdjęcia na stronie"
    )[language] ?: "Columns|Rows|Separate photo pages|Photos per page").split("|")
    val cols = f.gridColumns()
    val rows = f.gridRows()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoGridChoice(labels[0],cols,4) { onChange(f.copy(photoColumns = it)) }
            PhotoGridChoice(labels[1],rows,6) { onChange(f.copy(photoRows = it)) }
        }
        Row { Checkbox(f.separatePhotoPages,{ onChange(f.copy(separatePhotoPages = it)) }); Text(labels[2]) }
        Text(labels[3] + ": " + cols*rows)
        val color = MaterialTheme.colorScheme.primaryContainer
        Canvas(Modifier.width(110.dp).height(150.dp)) {
            val w = size.width/cols
            val h = size.height/rows
            for (r in 0 until rows) for (c in 0 until cols) {
                drawRect(color,Offset(c*w+2,r*h+2),Size(w-4,h-4))
            }
        }
    }
}
@Composable
private fun PhotoGridChoice(label: String, value: Int, max: Int, onChange: (Int)->Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(value.toString()) }
            DropdownMenu(open,{ open = false }) {
                for (n in 1..max) DropdownMenuItem(text = { Text(n.toString()) },onClick = { open = false; onChange(n) })
            }
        }
    }
}

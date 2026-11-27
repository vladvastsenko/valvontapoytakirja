package fi.vladi.valvontapoytakirja

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fi.vladi.valvontapoytakirja.model.SignatureStroke
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray

internal fun customColumns(f: CustomField) = f.options.filter { it.isNotBlank() }.ifEmpty { listOf("1", "2") }
internal fun customRows(value: String, columns: Int): List<List<String>> {
    if (value.isBlank()) return emptyList()
    return runCatching {
        val a = JSONArray(value)
        List(a.length()) { i -> val r = a.getJSONArray(i); List(columns) { j -> r.optString(j,"") } }
    }.getOrElse { value.lines().map { line -> List(columns) { j -> if (j == 0) line else "" } } }
}
private fun encodeRows(rows: List<List<String>>) = JSONArray().apply { rows.forEach { row -> put(JSONArray(row)) } }.toString()

@Composable
internal fun CustomTable(f: CustomField, value: String, onValue: (String)->Unit, l: List<String>) {
    val columns = customColumns(f)
    val rows = customRows(value,columns.size)
    Column {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row { columns.forEach { Text(it,Modifier.width(150.dp).padding(4.dp)) } }
            rows.forEachIndexed { i,row ->
                Row {
                    row.forEachIndexed { j,cell ->
                        OutlinedTextField(cell,{ v -> onValue(encodeRows(rows.mapIndexed { ri,r -> if (ri == i) r.mapIndexed { ci,c -> if (ci == j) v else c } else r })) },modifier = Modifier.width(150.dp))
                    }
                    TextButton(onClick = { onValue(encodeRows(rows.filterIndexed { index,_ -> index != i })) }) { Text(l[17]) }
                }
            }
        }
        OutlinedButton(onClick = { onValue(encodeRows(rows + listOf(List(columns.size) { "" }))) }) { Text("+") }
    }
}

@Composable
internal fun CustomDateTime(value: String, onValue: (String)->Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value.substringBefore(" ")) }.getOrNull()
    val time = runCatching { LocalTime.parse(value.substringAfter(" ","")) }.getOrNull()
    Row {
        OutlinedButton(onClick = {
            val d = date ?: LocalDate.now()
            DatePickerDialog(context,{ _,y,m,day -> onValue(LocalDate.of(y,m+1,day).toString()+" "+(time ?: LocalTime.now().withSecond(0).withNano(0))) },d.year,d.monthValue-1,d.dayOfMonth).show()
        }) { Text(date?.toString() ?: "📅") }
        OutlinedButton(onClick = {
            val t = time ?: LocalTime.now()
            TimePickerDialog(context,{ _,hour,minute -> onValue((date ?: LocalDate.now()).toString()+" "+LocalTime.of(hour,minute)) },t.hour,t.minute,true).show()
        }) { Text(time?.toString() ?: "🕒") }
        TextButton(onClick = { onValue("") }) { Text("×") }
    }
}

@Composable
private fun CustomPreview(name: String, store: CustomStore) {
    val bitmap by produceState<Bitmap?>(null,name) {
        value = withContext(Dispatchers.IO) { runCatching { decodeCustomImage(store.image(name), 480) }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(),contentDescription = null,modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) }
}
@Composable
internal fun CustomSignature(images: List<String>, store: CustomStore, onImages: (List<String>)->Unit, onError: ()->Unit, l: List<String>) {
    var strokes by remember { mutableStateOf(emptyList<SignatureStroke>()) }
    if (images.isNotEmpty() && strokes.isEmpty()) images.firstOrNull()?.let { CustomPreview(it,store) }
    SignaturePad(strokes) { next -> strokes = next }
    if (strokes.isNotEmpty()) OutlinedButton(onClick = {
        runCatching {
            val bitmap = Bitmap.createBitmap(1600,600,Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                strokes.forEach { stroke ->
                    stroke.points.zipWithNext().forEach { (a,b) -> canvas.drawLine(a.x*1600,a.y*600,b.x*1600,b.y*600,paint) }
                    if (stroke.points.size == 1) stroke.points.first().let { canvas.drawCircle(it.x*1600,it.y*600,3f,paint) }
                }
                store.saveBitmap(bitmap)
            } finally { bitmap.recycle() }
        }.onSuccess { onImages(listOf(it)); strokes = emptyList() }.onFailure { onError() }
    }) { Text(l[14]) }
    if (images.isNotEmpty()) TextButton(onClick = { strokes = emptyList(); onImages(emptyList()) }) { Text(l[17]) }
}
@Composable
internal fun CustomPhotos(images: List<String>, store: CustomStore, onImages: (List<String>)->Unit, onError: ()->Unit, l: List<String>, onImport: (Boolean)->Unit) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val latestImages by rememberUpdatedState(images)
    val latestOnImages by rememberUpdatedState(onImages)
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && !importing) {
            importing = true
            onImport(true)
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { uris.mapNotNull { uri -> runCatching { store.importImage(context, uri) }.getOrNull() } }
                    if (imported.isNotEmpty()) latestOnImages(latestImages + imported)
                    if (imported.size != uris.size) onError()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { onError() }
                finally { importing = false; onImport(false) }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pending?.let { File(it) }
        importing = true
        onImport(true)
        scope.launch {
            try {
                if (ok && file != null && file.length() > 0) {
                    val name = withContext(Dispatchers.IO) {
                        val uri = FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
                        store.importImage(context,uri)
                    }
                    latestOnImages(latestImages + name)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onError() }
            finally { file?.delete(); pending = null; importing = false; onImport(false) }
        }
    }
    Row {
        OutlinedButton(enabled = !importing, onClick = { gallery.launch("image/*") }) { Text(l[21]) }
        OutlinedButton(enabled = !importing, onClick = {
            runCatching {
                val file = File.createTempFile("custom_camera_",".jpg",context.cacheDir)
                pending = file.path
                camera.launch(FileProvider.getUriForFile(context,context.packageName+".fileprovider",file))
            }.onFailure { pending?.let { File(it).delete() }; pending = null; onError() }
        }) { Text("📷") }
    }
    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
    var page by rememberSaveable { mutableStateOf(0) }
    val lastPage = ((images.size - 1).coerceAtLeast(0)) / 8
    val visiblePage = page.coerceIn(0,lastPage)
    if (images.size > 8) Row {
        TextButton(enabled = visiblePage > 0, onClick = { page = visiblePage - 1 }) { Text("‹") }
        Text("${visiblePage + 1} / ${lastPage + 1}")
        TextButton(enabled = visiblePage < lastPage, onClick = { page = visiblePage + 1 }) { Text("›") }
    }
    images.drop(visiblePage * 8).take(8).forEachIndexed { localIndex,name -> key(name) {
        val i = visiblePage * 8 + localIndex
        CustomPreview(name,store)
        TextButton(enabled = !importing, onClick = { onImages(images.filterIndexed { index,_ -> index != i }) }) { Text(l[17]+" "+(i+1)) }
    } }
}

package fi.vladi.valvontapoytakirja.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BACKUP_PREFS = "tarkastus_cloud_backup"
private const val DRIVE_TREE_URI = "drive_tree_uri"

@Composable
fun CloudBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE) }
    var folderUri by remember { mutableStateOf(preferences.getString(DRIVE_TREE_URI, null)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(preferences.getString("last_backup_status", "Varmuuskopio ei ole vielä tehty.")
            ?: "Varmuuskopio ei ole vielä tehty.")
    }
    var restoreCompleted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            preferences.edit().putString(DRIVE_TREE_URI, uri.toString()).apply()
            folderUri = uri.toString()
            status = "Google Drive -kansio valittu."
        }
    }

    val archivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            busy = true
            scope.launch {
                status = try {
                    val result = withContext(Dispatchers.IO) {
                        CloudBackupManager.restoreFromUri(context, uri)
                    }
                    restoreCompleted = true
                    result
                } catch (error: Throwable) {
                    "Palautus epäonnistui: ${error.message ?: "Tuntematon virhe"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    val selectedTree = folderUri?.let(Uri::parse)

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Keep navigation visible at the top; the screen content can extend
        // beyond the viewport on phones with smaller displays.
        TextButton(onClick = onBack, enabled = !busy) { Text("← Takaisin") }
        Text("Pilvivarmuuskopio ja synkronointi", style = MaterialTheme.typography.headlineMedium)
        Text("Valitse Google Drive -sovelluksessa sama yksityinen Tarkastus-kansio kaikilla laitteilla.")
        Text(
            if (folderUri.isNullOrBlank()) "Tallennuskansiota ei ole valittu." else "Tallennuskansio on valittu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedButton(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text("Valitse Google Drive -kansio") }

        Button(
            onClick = {
                val uri = selectedTree
                if (uri == null) {
                    status = "Valitse ensin Google Drive -kansio."
                } else {
                    busy = true
                    restoreCompleted = false
                    scope.launch {
                        status = try {
                            val result = withContext(Dispatchers.IO) {
                                CloudSyncManager.sync(context, uri)
                            }
                            result.asMessage()
                        } catch (error: Throwable) {
                            "Synkronointi epäonnistui: ${error.message ?: "Tuntematon virhe"}"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && selectedTree != null
        ) { Text("Synkronoi nyt") }

        Text(
            "Synkronointi lähettää ja vastaanottaa raportit, kuvat, mallit, asetustiedostot sekä valmiit PDF-raportit kansiosta Documents/Tarkastus/Raportit/PDF. Käytä samaa Drive-kansiota molemmissa laitteissa. Synkronointi käynnistetään toistaiseksi käsin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = {
                val uri = selectedTree
                if (uri == null) {
                    status = "Valitse ensin tallennuskansio."
                } else {
                    busy = true
                    scope.launch {
                        status = try {
                            val name = withContext(Dispatchers.IO) {
                                CloudBackupManager.backupToFolder(context, uri)
                            }
                            "Varmuuskopio tallennettu: $name".also {
                                preferences.edit().putString("last_backup_status", it).apply()
                            }
                        } catch (error: Throwable) {
                            "Varmuuskopiointi epäonnistui: ${error.message ?: "Tuntematon virhe"}"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && selectedTree != null
        ) { Text("Luo erillinen varmuuskopio") }

        OutlinedButton(
            onClick = { archivePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text("Palauta varmuuskopio") }

        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator()
                Text("Käsitellään tietoja…")
            }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (restoreCompleted) {
            Button(
                onClick = { (context as? Activity)?.recreate() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Lataa palautetut tiedot uudelleen") }
        }
        Text(
            "Synkronointi käsittelee muutoksia molempiin suuntiin. Jos sama tiedosto muuttui eri tavoin molemmilla laitteilla, paikallinen versio tallennetaan Drive-kansion Tarkastus_Sync/Conflicts-kansioon ja ristiriita ilmoitetaan. Älä sulje sovellusta kesken synkronoinnin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Palautus korvaa nykyiset paikalliset tiedot valitun varmuuskopion tiedoilla. Nykyinen arkisto säilytetään palautuksen ajan suojakopiona.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

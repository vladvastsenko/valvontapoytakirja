package fi.vladi.valvontapoytakirja.cloud

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
    var status by remember { mutableStateOf("Varmuuskopio ei ole vielä tehty.") }
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
                    withContext(Dispatchers.IO) {
                        CloudBackupManager.restoreFromUri(context, uri)
                    }
                } catch (error: Throwable) {
                    "Palautus epäonnistui: ${error.message ?: "Tuntematon virhe"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pilvivarmuuskopio", style = MaterialTheme.typography.headlineMedium)
        Text("Valitse Google Drive -sovelluksessa oma yksityinen Tarkastus-kansio. Sovellus tallentaa sinne koko paikallisen työarkiston ZIP-tiedostona.")
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
                val uri = folderUri?.let(Uri::parse)
                if (uri == null) {
                    status = "Valitse ensin tallennuskansio."
                } else {
                    busy = true
                    scope.launch {
                        status = try {
                            val name = withContext(Dispatchers.IO) {
                                CloudBackupManager.backupToFolder(context, uri)
                            }
                            "Varmuuskopio tallennettu: $name"
                        } catch (error: Throwable) {
                            "Varmuuskopiointi epäonnistui: ${error.message ?: "Tuntematon virhe"}"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && !folderUri.isNullOrBlank()
        ) { Text("Varmuuskopioi nyt") }

        OutlinedButton(
            onClick = { archivePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text("Palauta varmuuskopio") }

        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator()
                Text("Käsitellään varmuuskopiota…")
            }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Palautus korvaa nykyiset paikalliset tiedot valitun varmuuskopion tiedoilla. Nykyinen arkisto säilytetään palautuksen ajan suojakopiona.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, enabled = !busy) { Text("Takaisin") }
    }
}

package fi.vladi.valvontapoytakirja.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fi.vladi.valvontapoytakirja.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private const val BACKUP_PREFS = "tarkastus_cloud_backup"
private const val DRIVE_TREE_URI = "drive_tree_uri"

@Composable
fun CloudBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE) }
    var folderUri by remember { mutableStateOf(preferences.getString(DRIVE_TREE_URI, null)) }
    var backups by remember { mutableStateOf<List<CloudBackupManager.BackupFile>>(emptyList()) }
    var selectedBackupUri by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getString(R.string.cloud_no_backups)) }
    var restoreCompleted by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri) {
        val savedUri = folderUri
        if (!savedUri.isNullOrBlank()) {
            runCatching { withContext(Dispatchers.IO) { CloudBackupManager.listBackups(context, Uri.parse(savedUri)) } }
                .onSuccess { found ->
                    backups = found
                    selectedBackupUri = found.firstOrNull()?.uri?.toString()
                    status = if (found.isEmpty()) context.getString(R.string.cloud_no_found)
                        else "Löytyi ${found.size} varmuuskopiota. Uusin on valittuna."
                }.onFailure { error -> status = "Varmuuskopioiden lukeminen epäonnistui: ${error.message ?: "Tuntematon virhe"}" }
        } else {
            backups = emptyList()
            selectedBackupUri = null
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            preferences.edit().putString(DRIVE_TREE_URI, uri.toString()).apply()
            folderUri = uri.toString()
            status = context.getString(R.string.cloud_drive_selected)
        }
    }
    val selectedTree = folderUri?.let(Uri::parse)
    val selectedBackup = backups.firstOrNull { it.uri.toString() == selectedBackupUri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.cloud_back)) }
        Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.cloud_intro))
        Text(if (folderUri.isNullOrBlank()) context.getString(R.string.cloud_folder_missing) else context.getString(R.string.cloud_folder_selected),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text(stringResource(R.string.cloud_choose_folder))
        }
        Button(
            onClick = {
                val uri = selectedTree
                if (uri == null) status = context.getString(R.string.cloud_folder_first) else {
                    busy = true
                    restoreCompleted = false
                    scope.launch {
                        status = try { withContext(Dispatchers.IO) { CloudSyncManager.sync(context, uri) }.asMessage() }
                        catch (error: Throwable) { "Synkronointi epäonnistui: ${error.message ?: "Tuntematon virhe"}" }
                        finally { busy = false }
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && selectedTree != null
        ) { Text(stringResource(R.string.cloud_sync)) }
        Text(stringResource(R.string.cloud_sync_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedButton(
            onClick = {
                val uri = selectedTree
                if (uri == null) status = context.getString(R.string.cloud_backup_first) else {
                    busy = true
                    scope.launch {
                        status = try {
                            val result = withContext(Dispatchers.IO) { CloudBackupManager.backupToFolder(context, uri) }
                            val refreshed = withContext(Dispatchers.IO) { CloudBackupManager.listBackups(context, uri) }
                            backups = refreshed
                            selectedBackupUri = refreshed.firstOrNull()?.uri?.toString()
                            "Varmuuskopio tallennettu. $result".also { preferences.edit().putString("last_backup_status", it).apply() }
                        } catch (error: Throwable) { "Varmuuskopiointi epäonnistui: ${error.message ?: "Tuntematon virhe"}" }
                        finally { busy = false }
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && selectedTree != null
        ) { Text(stringResource(R.string.cloud_backup)) }

        Text(stringResource(R.string.cloud_backups), style = MaterialTheme.typography.titleMedium)
        if (backups.isEmpty()) {
            Text(stringResource(R.string.cloud_empty))
        } else {
            backups.forEachIndexed { index, backup ->
                Card(Modifier.fillMaxWidth().clickable { selectedBackupUri = backup.uri.toString() }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedBackupUri == backup.uri.toString(), onClick = { selectedBackupUri = backup.uri.toString() })
                        Column(Modifier.weight(1f)) {
                            Text(if (index == 0) context.getString(R.string.cloud_latest) else formatBackupDate(backup.name), style = MaterialTheme.typography.titleSmall)
                            if (index == 0) Text(formatBackupDate(backup.name), style = MaterialTheme.typography.bodySmall)
                            Text("Koko: ${formatBackupSize(backup.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { showRestoreConfirmation = true }, modifier = Modifier.fillMaxWidth(),
            enabled = !busy && selectedBackup != null) { Text(stringResource(R.string.cloud_restore_selected)) }
        Text(stringResource(R.string.cloud_restore_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text(stringResource(R.string.cloud_processing)) }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (restoreCompleted) Button(onClick = { (context as? Activity)?.recreate() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cloud_reload))
        }
        Text("Synkronointi käsittelee muutoksia molempiin suuntiin. Jos sama tiedosto muuttui eri tavoin molemmilla laitteilla, paikallinen versio tallennetaan Drive-kansion Tarkastus_Sync/Conflicts-kansioon ja ristiriita ilmoitetaan. Älä sulje sovellusta kesken synkronoinnin.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }

    if (showRestoreConfirmation && selectedBackup != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showRestoreConfirmation = false },
            title = { Text(stringResource(R.string.cloud_confirm_title)) },
            text = { Text("Palautetaanko kopio ${formatBackupDate(selectedBackup.name)} (${formatBackupSize(selectedBackup.sizeBytes)})? Ennen palautusta nykyisistä tiedoista luodaan automaattisesti uusi suojakopio Google Driveen.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    showRestoreConfirmation = false
                    busy = true
                    restoreCompleted = false
                    val backupToRestore = selectedBackup
                    val tree = selectedTree
                    scope.launch {
                        status = try {
                            check(tree != null) { context.getString(R.string.cloud_drive_missing) }
                            val safetyCopy = withContext(Dispatchers.IO) { CloudBackupManager.backupToFolder(context, tree) }
                            val result = withContext(Dispatchers.IO) { CloudBackupManager.restoreFromUri(context, backupToRestore.uri) }
                            restoreCompleted = true
                            "$result\nSuojakopio ennen palautusta: $safetyCopy"
                        } catch (error: Throwable) {
                            "Palautus epäonnistui: ${error.message ?: "Tuntematon virhe"}"
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.cloud_restore)) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirmation = false }, enabled = !busy) { Text(stringResource(R.string.cloud_cancel)) } }
        )
    }
}

private fun formatBackupDate(name: String): String {
    val stamp = name.removePrefix("Tarkastus_backup_").removeSuffix(".zip")
    val date = listOf("yyyyMMdd_HHmmss_SSS", "yyyyMMdd_HHmmss").firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.US).parse(stamp) }.getOrNull()
    }
    return date?.let { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(it) } ?: context.getString(R.string.cloud_date_missing)
}

private fun formatBackupSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

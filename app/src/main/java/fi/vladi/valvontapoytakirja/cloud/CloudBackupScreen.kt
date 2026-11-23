package fi.vladi.valvontapoytakirja.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
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
    if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.guest_cloud_required))
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }
    val preferences = remember { context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE) }
    var folderUri by remember { mutableStateOf(preferences.getString(DRIVE_TREE_URI, null)) }
    var backups by remember { mutableStateOf<List<CloudBackupManager.BackupFile>>(emptyList()) }
    var selectedBackupUri by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getString(R.string.cloud_no_backups)) }
    var restoreCompleted by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showSyncConfirmation by remember { mutableStateOf(false) }
    var showShareGuide by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri) {
        val savedUri = folderUri
        if (!savedUri.isNullOrBlank()) {
            runCatching { withContext(Dispatchers.IO) { CloudBackupManager.listBackups(context, Uri.parse(savedUri)) } }
                .onSuccess { found ->
                    backups = found
                    selectedBackupUri = found.firstOrNull()?.uri?.toString()
                    status = if (found.isEmpty()) context.getString(R.string.cloud_no_found)
                        else context.getString(R.string.cloud_found_backups, found.size)
                }.onFailure { error -> status = context.getString(R.string.cloud_read_error, error.message ?: context.getString(R.string.cloud_unknown_error)) }
        } else {
            backups = emptyList()
            selectedBackupUri = null
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission && it.isWritePermission
                }
            }.getOrDefault(false)
            val directory = if (granted) androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri) else null
            if (granted && directory?.isDirectory == true && directory.canRead() && directory.canWrite()) {
                preferences.edit().putString(DRIVE_TREE_URI, uri.toString()).apply()
                folderUri = uri.toString()
                status = context.getString(R.string.cloud_drive_selected)
            } else {
                status = context.getString(R.string.cloud_folder_access_denied)
            }
        }
    }
    val hasFolderPermission = folderUri?.let { saved ->
        context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == saved && it.isReadPermission && it.isWritePermission
        }
    } == true
    val selectedTree = folderUri?.takeIf { hasFolderPermission }?.let(Uri::parse)
    val selectedBackup = backups.firstOrNull { it.uri.toString() == selectedBackupUri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.cloud_back)) }
        Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.cloud_intro))
        Text(if (folderUri.isNullOrBlank()) context.getString(R.string.cloud_folder_missing) else context.getString(R.string.cloud_folder_selected),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.cloud_team_access_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.cloud_team_access_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { showShareGuide = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) { Text(stringResource(R.string.cloud_invite_teammate)) }
        Text(stringResource(R.string.cloud_invite_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text(stringResource(R.string.cloud_choose_folder))
        }
        Button(
            onClick = { showSyncConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && selectedTree != null
        ) { Text(stringResource(R.string.cloud_sync_now)) }
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
                            context.getString(R.string.cloud_backup_saved, result).also { preferences.edit().putString("last_backup_status", it).apply() }
                        } catch (error: Throwable) { context.getString(R.string.cloud_backup_error, error.message ?: context.getString(R.string.cloud_unknown_error)) }
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
                            Text(if (index == 0) context.getString(R.string.cloud_latest) else formatBackupDate(context, backup.name), style = MaterialTheme.typography.titleSmall)
                            if (index == 0) Text(formatBackupDate(context, backup.name), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.cloud_size_label, formatBackupSize(backup.sizeBytes)), style = MaterialTheme.typography.bodySmall)
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
        Text(stringResource(R.string.cloud_sync_warning),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }

    if (showShareGuide) {
        AlertDialog(
            onDismissRequest = { showShareGuide = false },
            title = { Text(stringResource(R.string.cloud_share_guide_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.cloud_share_guide_steps))
            } },
            confirmButton = {
                TextButton(onClick = {
                    showShareGuide = false
                    val driveIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/my-drive"))
                    val appIntent = Intent(driveIntent).setPackage("com.google.android.apps.docs")
                    try { context.startActivity(appIntent) }
                    catch (_: ActivityNotFoundException) { context.startActivity(driveIntent) }
                }) { Text(stringResource(R.string.cloud_share_open_drive)) }
            },
            dismissButton = { TextButton(onClick = { showShareGuide = false }) {
                Text(stringResource(R.string.cloud_cancel))
            } }
        )
    }

    if (showSyncConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSyncConfirmation = false },
            title = { Text(stringResource(R.string.cloud_sync_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_sync_confirm_message)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    showSyncConfirmation = false
                    val uri = selectedTree ?: return@TextButton
                    busy = true
                    restoreCompleted = false
                    scope.launch {
                        status = try {
                            withContext(Dispatchers.IO) { CloudSyncManager.sync(context, uri) }.let { result ->
                                context.getString(R.string.cloud_sync_complete, result.uploaded, result.downloaded, result.deleted, result.conflicts, result.pdfFoundLocal, result.pdfFoundDrive, result.pdfUploaded, result.pdfDownloaded, result.pdfDeleted, result.pdfConflicts)
                            }
                        } catch (error: Throwable) {
                            context.getString(R.string.cloud_sync_error, error.message ?: context.getString(R.string.cloud_unknown_error))
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.cloud_sync_now)) }
            },
            dismissButton = { TextButton(onClick = { showSyncConfirmation = false }) { Text(stringResource(R.string.cloud_cancel)) } }
        )
    }

    if (showRestoreConfirmation && selectedBackup != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showRestoreConfirmation = false },
            title = { Text(stringResource(R.string.cloud_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_restore_confirm_details, formatBackupDate(context, selectedBackup.name), formatBackupSize(selectedBackup.sizeBytes))) },
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
                            context.getString(R.string.cloud_restore_safety_copy, result, safetyCopy)
                        } catch (error: Throwable) {
                            context.getString(R.string.cloud_restore_error, error.message ?: context.getString(R.string.cloud_unknown_error))
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.cloud_restore)) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirmation = false }, enabled = !busy) { Text(stringResource(R.string.cloud_cancel)) } }
        )
    }
}

private fun formatBackupDate(context: Context, name: String): String {
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

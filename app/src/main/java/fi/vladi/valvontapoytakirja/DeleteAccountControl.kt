package fi.vladi.valvontapoytakirja

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

@Composable
internal fun DeleteAccountControl(onDeleted: () -> Unit) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser ?: return
    var open by remember(user.uid) { mutableStateOf(false) }
    var busy by remember(user.uid) { mutableStateOf(false) }
    var error by remember(user.uid) { mutableStateOf<Int?>(null) }
    OutlinedButton(onClick = { error = null; open = true }, enabled = !busy,
        modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = {
                Text(stringResource(error ?: R.string.delete_account_warning))
            },
            confirmButton = {
                if (error == R.string.delete_account_relogin) {
                    TextButton(onClick = onDeleted) { Text(stringResource(R.string.sign_out)) }
                } else {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        error = null
                        user.delete().addOnCompleteListener { task ->
                            busy = false
                            if (task.isSuccessful) {
                                context.getSharedPreferences("tarkastus_biometric", android.content.Context.MODE_PRIVATE)
                                    .edit().remove("${user.uid}_enabled").remove("${user.uid}_offered").apply()
                                open = false
                                onDeleted()
                            } else {
                                error = if (task.exception is FirebaseAuthRecentLoginRequiredException)
                                    R.string.delete_account_relogin else R.string.delete_account_failed
                            }
                        }
                    }) { Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

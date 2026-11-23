package fi.vladi.valvontapoytakirja

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import fi.vladi.tarkastuspoytakirja.AppLanguage

@Composable
internal fun CloudAuthGate(content: @Composable (onSignOut: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    val guestPrefs = remember { context.getSharedPreferences("tarkastus_guest_mode", android.content.Context.MODE_PRIVATE) }
    var guest by remember { mutableStateOf(auth.currentUser == null && guestPrefs.getBoolean("enabled", false)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    val webClientId = context.getString(R.string.default_web_client_id)
    val options = remember(webClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            busy = false
        } else {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    busy = false
                    if (task.isSuccessful) {
                        guest = false
                        guestPrefs.edit().putBoolean("enabled", false).apply()
                        signedIn = true
                        error = null
                    } else {
                        error = task.exception?.localizedMessage ?: context.getString(R.string.google_failed)
                    }
                }
            } catch (exception: Exception) {
                busy = false
                error = exception.localizedMessage
            }
        }
    }
    if (guest && !signedIn) {
        content({ guest = false; guestPrefs.edit().putBoolean("enabled", false).apply() })
    } else if (signedIn) {
        BiometricSessionGate { content({
            auth.signOut()
            GoogleSignIn.getClient(context, options).signOut()
            signedIn = false
            password = ""
            error = null
            message = null
        }) }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Tarkastus Cloud", style = MaterialTheme.typography.headlineSmall)
            Box {
                OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(when (AppLanguage.selected(context)) {
                        "en" -> "English"
                        "fi" -> "Suomi"
                        "sv" -> "Svenska"
                        "nb" -> "Norsk"
                        "da" -> "Dansk"
                        "et" -> "Eesti"
                        "lv" -> "Latviešu"
                        "lt" -> "Lietuvių"
                        "ru" -> "Русский"
                        else -> "English"
                    } + " ▾")
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    listOf("en" to "English", "fi" to "Suomi", "sv" to "Svenska", "nb" to "Norsk", "da" to "Dansk", "et" to "Eesti", "lv" to "Latviešu", "lt" to "Lietuvių", "ru" to "Русский").forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            languageMenu = false
                            if (AppLanguage.selected(context) != code) {
                                AppLanguage.save(context, code)
                                (context as? Activity)?.recreate()
                            }
                        })
                    }
                }
            }
            OutlinedButton(onClick = {
                guestPrefs.edit().putBoolean("enabled", true).apply()
                guest = true
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guest_continue))
            }
            Text(stringResource(R.string.guest_explanation), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    busy = true
                    error = null
                    launcher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sign_in))
            }
            Text(stringResource(R.string.auth_title))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            message?.let { Text(it) }
            Button(onClick = {
                busy = true
                error = null
                message = null
                val address = email.trim()
                if (address.isBlank() || password.length < 6) {
                    busy = false
                    error = context.getString(R.string.password_short)
                } else {
                    val task = if (register) auth.createUserWithEmailAndPassword(address, password) else auth.signInWithEmailAndPassword(address, password)
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            guest = false
                            guestPrefs.edit().putBoolean("enabled", false).apply()
                            signedIn = true
                            if (register) auth.currentUser?.sendEmailVerification()
                        } else error = result.exception?.localizedMessage ?: context.getString(R.string.auth_failed)
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (register) stringResource(R.string.create_account) else stringResource(R.string.email_sign_in)) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { register = !register; error = null }, enabled = !busy) {
                    Text(if (register) stringResource(R.string.already_registered) else stringResource(R.string.new_user))
                }
                TextButton(onClick = {
                if (email.trim().isBlank()) error = context.getString(R.string.email_required)
                else {
                    busy = true
                    error = null
                    auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) message = context.getString(R.string.reset_sent)
                        else error = result.exception?.localizedMessage ?: context.getString(R.string.reset_failed)
                    }
                }
                }, enabled = !busy) { Text(stringResource(R.string.forgot_password)) }
            }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

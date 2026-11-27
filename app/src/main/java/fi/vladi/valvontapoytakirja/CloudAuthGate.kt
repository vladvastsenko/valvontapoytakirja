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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
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
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import fi.vladi.tarkastuspoytakirja.AppLanguage

@Composable
internal fun CloudAuthGate(content: @Composable (onSignOut: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    fi.vladi.valvontapoytakirja.billing.PremiumRefresh(context)
    var signedIn by remember { mutableStateOf(auth.currentUser?.isAnonymous == false) }
    val guestPrefs = remember { context.getSharedPreferences("tarkastus_guest_mode", android.content.Context.MODE_PRIVATE) }
    var guest by remember { mutableStateOf(auth.currentUser?.isAnonymous == true || (auth.currentUser == null && guestPrefs.getBoolean("enabled", true))) }
    androidx.compose.runtime.DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            val user = it.currentUser
            if (user?.isAnonymous == true) {
                signedIn = false
                guest = true
            } else if (user != null) {
                signedIn = true
                guest = false
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    val webClientId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName).let { if (it != 0) context.getString(it) else "" }
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
                        error = localizedAuthError(context, task.exception, R.string.google_failed)
                    }
                }
            } catch (exception: Exception) {
                busy = false
                error = localizedAuthError(context, exception, R.string.google_failed)
            }
        }
    }
    if (auth.currentUser?.isAnonymous == true || (guest && !signedIn)) {
        content({ if (auth.currentUser?.isAnonymous != true) { guest = false; guestPrefs.edit().putBoolean("enabled", false).apply() } })
    } else if (signedIn) {
        BiometricSessionGate { content({
            fi.vladi.valvontapoytakirja.billing.PremiumAccess.clear()
            guest = true
            guestPrefs.edit().putBoolean("enabled", true).apply()
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
            Text("Tarkastus Pro", style = MaterialTheme.typography.headlineSmall)
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
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (passwordVisible) R.string.password_hide else R.string.password_show)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
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
                        } else error = localizedAuthError(context, result.exception, R.string.auth_failed)
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (register) stringResource(R.string.create_account) else stringResource(R.string.email_sign_in)) }
            // Separate rows prevent long translations from clipping the password-reset action.
            TextButton(onClick = { register = !register; error = null }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) {
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
                        else error = localizedAuthError(context, result.exception, R.string.reset_failed)
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.forgot_password))
            }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun localizedAuthError(context: android.content.Context, error: Exception?, fallback: Int): String {
    val resource = when {
        error is FirebaseNetworkException -> R.string.auth_error_network
        error is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.auth_error_invalid_email
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND",
            "ERROR_USER_DISABLED", "ERROR_INVALID_LOGIN_CREDENTIALS" -> R.string.auth_error_invalid_credentials
            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_error_email_in_use
            "ERROR_WEAK_PASSWORD" -> R.string.auth_error_weak_password
            "ERROR_TOO_MANY_REQUESTS" -> R.string.auth_error_too_many
            "ERROR_NETWORK_REQUEST_FAILED" -> R.string.auth_error_network
            else -> fallback
        }
        else -> fallback
    }
    return context.getString(resource)
}

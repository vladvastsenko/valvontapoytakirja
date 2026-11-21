package fi.vladi.valvontapoytakirja

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
internal fun CloudAuthGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
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
                        signedIn = true
                        error = null
                    } else {
                        error = task.exception?.localizedMessage ?: "Google sign-in failed"
                    }
                }
            } catch (exception: Exception) {
                busy = false
                error = exception.localizedMessage
            }
        }
    }
    if (signedIn) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Tarkastus Cloud", style = MaterialTheme.typography.headlineLarge)
            Text("Sign in or create an account")
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            message?.let { Text(it) }
            Button(onClick = {
                busy = true
                error = null
                message = null
                val address = email.trim()
                if (address.isBlank() || password.length < 6) {
                    busy = false
                    error = "Enter an email and a password of at least 6 characters"
                } else {
                    val task = if (register) auth.createUserWithEmailAndPassword(address, password) else auth.signInWithEmailAndPassword(address, password)
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            signedIn = true
                            if (register) auth.currentUser?.sendEmailVerification()
                        } else error = result.exception?.localizedMessage ?: "Authentication failed"
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Create account" else "Sign in with email") }
            TextButton(onClick = { register = !register; error = null }, enabled = !busy) {
                Text(if (register) "Already registered? Sign in" else "New user? Create account")
            }
            TextButton(onClick = {
                if (email.trim().isBlank()) error = "Enter your email first"
                else {
                    busy = true
                    error = null
                    auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) message = "Password reset email sent"
                        else error = result.exception?.localizedMessage ?: "Password reset failed"
                    }
                }
            }, enabled = !busy) { Text("Forgot password?") }
            Text("Or")
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    busy = true
                    error = null
                    launcher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Google")
            }
        }
    }
}

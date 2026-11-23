package fi.vladi.valvontapoytakirja

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Optional device-local lock over an existing Firebase session.
 * BiometricPrompt does not authenticate to Firebase or store biometric data.
 */
internal object BiometricLock {
    private const val PREFS = "tarkastus_biometric"
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    private fun key(uid: String, suffix: String) = "${uid}_$suffix"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(context: Context, uid: String) = prefs(context).getBoolean(key(uid, "enabled"), false)
    fun offered(context: Context, uid: String) = prefs(context).getBoolean(key(uid, "offered"), false)
    fun markOffered(context: Context, uid: String) {
        prefs(context).edit().putBoolean(key(uid, "offered"), true).apply()
    }
    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(key(uid, "enabled"), enabled).apply()
    }
    fun available(context: Context) =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(context: Context, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity == null || !available(context)) {
            onFailure()
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure()
                }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Tarkastus Cloud")
            .setSubtitle("Vahvista henkilöllisyytesi")
            .setNegativeButtonText("Peruuta")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build())
    }
}

@Composable
internal fun BiometricSettingsControl() {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var enabled by remember(uid) { mutableStateOf(BiometricLock.enabled(context, uid)) }
    var status by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Biometrinen lukitus")
        Text("Suojaa sovelluksen avaaminen sormenjäljellä tai muulla tuetulla biometrisellä tunnisteella.")
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (enabled) "Käytössä" else "Pois käytöstä")
            Switch(checked = enabled, onCheckedChange = { requested ->
                if (!requested) {
                    BiometricLock.setEnabled(context, uid, false)
                    enabled = false
                    status = ""
                } else if (!BiometricLock.available(context)) {
                    status = "Biometrinen tunnistus ei ole käytettävissä tässä laitteessa."
                } else {
                    BiometricLock.prompt(context, {
                        BiometricLock.setEnabled(context, uid, true)
                        BiometricLock.markOffered(context, uid)
                        enabled = true
                        status = ""
                    }, { status = "Tunnistusta ei vahvistettu." })
                }
            })
        }
        if (status.isNotEmpty()) Text(status)
    }
}

@Composable
internal fun BiometricSessionGate(onUnlocked: @Composable () -> Unit) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var unlocked by remember(uid) { mutableStateOf(!BiometricLock.enabled(context, uid)) }
    var prompted by remember(uid) { mutableStateOf(false) }
    var offer by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (BiometricLock.enabled(context, uid)) {
            if (BiometricLock.available(context)) {
                BiometricLock.prompt(context, { unlocked = true }, {
                    error = "Tunnistus peruutettiin. Yritä uudelleen."
                })
            } else {
                error = "Biometria ei ole käytettävissä. Kirjaudu uudelleen tai poista lukitus sovelluksen asetuksista."
            }
        } else if (!BiometricLock.offered(context, uid) && BiometricLock.available(context)) {
            offer = true
        }
    }
    if (unlocked) {
        onUnlocked()
        if (offer) {
            AlertDialog(onDismissRequest = {
                BiometricLock.markOffered(context, uid)
                offer = false
            }, title = { Text("Biometrinen kirjautuminen") },
                text = { Text("Haluatko suojata Tarkastus Cloudin avaamisen biometrisellä tunnistuksella? Voit muuttaa valintaa asetuksissa.") },
                confirmButton = { Button(onClick = {
                    BiometricLock.markOffered(context, uid)
                    offer = false
                    BiometricLock.prompt(context, {
                        BiometricLock.setEnabled(context, uid, true)
                    }, {})
                }) { Text("Ota käyttöön") } },
                dismissButton = { OutlinedButton(onClick = {
                    BiometricLock.markOffered(context, uid)
                    offer = false
                }) { Text("Ei nyt") } })
        }
    } else {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tarkastus Cloud – biometrinen lukitus")
            if (error.isNotEmpty()) Text(error)
            Button(onClick = {
                if (BiometricLock.available(context)) {
                    BiometricLock.prompt(context, { unlocked = true; error = "" }, {
                        error = "Tunnistus peruutettiin. Yritä uudelleen."
                    })
                } else {
                    error = "Biometrinen tunnistus ei ole käytettävissä."
                }
            }) { Text("Avaa biometrialla") }
            OutlinedButton(onClick = {
                FirebaseAuth.getInstance().signOut()
                (context as? android.app.Activity)?.recreate()
            }) { Text("Kirjaudu ulos") }
        }
    }
}

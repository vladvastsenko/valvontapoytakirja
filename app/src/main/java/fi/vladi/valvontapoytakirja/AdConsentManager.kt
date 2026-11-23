package fi.vladi.valvontapoytakirja

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Refresh consent on each launch. Never load an ad unless UMP says ads may be requested.
 * Configure a GDPR message and privacy options in AdMob Privacy & messaging.
 * Keep production ad unit disabled until consent and store disclosures are reviewed.
 */
internal class AdConsentManager(private val activity: Activity) {
    private val info = UserMessagingPlatform.getConsentInformation(activity)
    var canRequestAds by mutableStateOf(false)
        private set
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    var diagnostic by mutableStateOf("Not requested")
        private set

    fun request() {
        diagnostic = "Requesting consent information…"
        // A previously valid UMP decision can be reused while the refresh completes.
        update()
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(activity, params, {
            update()
            diagnostic = "Consent information updated; checking form…"
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                update()
                diagnostic = if (formError == null) "Consent form check completed" else "Consent form error ${formError.errorCode}: ${formError.message}"
            }
        }, { error ->
            update()
            diagnostic = "Consent request error ${error.errorCode}: ${error.message}"
        })
    }

    private fun update() {
        canRequestAds = info.canRequestAds()
        privacyOptionsRequired =
            info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun statusText(): String = "Consent status: ${info.consentStatus}; ads allowed: $canRequestAds; privacy options: ${info.privacyOptionsRequirementStatus}; result: $diagnostic"

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { update() }
    }
}

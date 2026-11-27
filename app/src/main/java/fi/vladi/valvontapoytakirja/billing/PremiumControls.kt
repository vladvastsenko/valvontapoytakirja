package fi.vladi.valvontapoytakirja.billing

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.vladi.valvontapoytakirja.R
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider

@Composable
fun PremiumControls() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var offers by remember { mutableStateOf<List<ProductDetails>>(emptyList()) }
    var uid by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registered by remember { mutableStateOf(false) }
    val entitlement by PremiumAccess.changes.collectAsState()
    val billing = remember(context) {
        ProBilling(context) { result, purchases ->
            busy = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    FirebaseAuth.getInstance().currentUser?.uid?.let { owner ->
                        it.accountIdentifiers?.obfuscatedAccountId == PremiumVerification.accountId(owner)
                    } == true }
                if (purchase == null) {
                    status = if (purchases.isEmpty()) R.string.premium_none else R.string.premium_unverified
                } else {
                    busy = true
                    PremiumVerification.verify(purchase.purchaseToken) { active ->
                        busy = false
                        status = if (active) null else R.string.premium_unverified
                    }
                }
            } else status = R.string.premium_unavailable
        }
    }
    DisposableEffect(billing) { onDispose { billing.close() } }
    Text("Premium")
    if (!PremiumAccess.allowed()) {
        Button(enabled = !busy, onClick = {
            busy = true
            PremiumVerification.prepareCheckout({ owner, product ->
                uid = owner
                billing.loadOffers(product) {
                    offers = it
                    busy = false
                    status = if (it.isEmpty()) R.string.premium_unavailable else null
                }
            }, { busy = false; status = R.string.premium_unavailable })
        }) { Text("Premium") }
        offers.forEach { details ->
            details.subscriptionOfferDetails.orEmpty().forEach { offer ->
                OutlinedButton(enabled = !busy, onClick = {
                    val activity = context as? android.app.Activity
                    val owner = uid
                    if (activity != null && owner != null &&
                        FirebaseAuth.getInstance().currentUser?.uid == owner) {
                        val result = billing.launchPurchase(activity, details, offer.offerToken, owner)
                        offers = emptyList()
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) status = R.string.premium_unavailable
                    }
                }) { Text(offer.pricingPhases.pricingPhaseList.joinToString(" / ") { it.formattedPrice + " " + it.billingPeriod }) }
            }
        }
    }
    OutlinedButton(enabled = !busy, onClick = { busy = true; billing.restorePurchases() }) {
        Text(stringResource(R.string.premium_restore))
    }
    if (entitlement != null && PremiumAccess.allowed() && !registered &&
        FirebaseAuth.getInstance().currentUser?.isAnonymous == true) {
        OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) },
            singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Button(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && user.isAnonymous && PremiumAccess.allowed() && !busy) {
                busy = true
                user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password))
                    .addOnCompleteListener { task ->
                        busy = false
                        if (task.isSuccessful) {
                            registered = true
                            password = ""
                            task.result?.user?.sendEmailVerification()
                        } else status = R.string.auth_failed
                    }
            }
        }) { Text(stringResource(R.string.create_account)) }
    }
    if (busy) CircularProgressIndicator()
    status?.let { Text(stringResource(it)) }
}

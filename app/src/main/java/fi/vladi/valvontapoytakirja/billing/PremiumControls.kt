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
    val recoveryStore = remember(context) { RecoveryStore(context) }
    val initialRecovery = remember(recoveryStore) { recoveryStore.read(FirebaseAuth.getInstance().currentUser?.uid) }
    var recoveryCode by remember { mutableStateOf(initialRecovery.orEmpty()) }
    var recoveryOwner by remember { mutableStateOf<String?>(if (initialRecovery != null) FirebaseAuth.getInstance().currentUser?.uid else null) }

    var recoveryInput by remember { mutableStateOf("") }
    var savedCode by remember { mutableStateOf(false) }
    var recovering by remember { mutableStateOf(false) }
    var requestedRecoveryCode by remember { mutableStateOf("") }
    val entitlement by PremiumAccess.changes.collectAsState()
    val billing = remember(context) {
        ProBilling(context) { result, purchases ->
            busy = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    FirebaseAuth.getInstance().currentUser?.uid?.let { owner ->
                        it.accountIdentifiers?.obfuscatedAccountId == PremiumVerification.accountId(owner)
                    } == true }
                if (recovering) {
                    recovering = false
                    val requestedCode = requestedRecoveryCode
                    val candidate = purchases.singleOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    if (candidate == null) status = R.string.premium_unverified
                    else {
                        busy = true
                        PremiumVerification.recover(requestedCode, candidate.purchaseToken) { active ->
                            busy = false
                            status = if (active) null else R.string.premium_unverified
                            if (active) {
                                val owner = FirebaseAuth.getInstance().currentUser?.uid
                                val code = requestedCode
                                if (owner != null && recoveryStore.write(owner, code)) { recoveryCode = code; recoveryOwner = owner }
                                else status = R.string.premium_unavailable
                            }
                        }
                    }
                } else if (purchase == null) {
                    status = if (purchases.isEmpty()) R.string.premium_none else R.string.premium_unverified
                } else {
                    busy = true
                    PremiumVerification.verify(purchase.purchaseToken) { active ->
                        busy = false
                        status = if (active) null else R.string.premium_unverified
                    }
                }
            } else { recovering = false; status = R.string.premium_unavailable }
        }
    }
    DisposableEffect(billing) { onDispose { billing.close() } }
    Text("Premium")
    if (!PremiumAccess.allowed()) {
        Button(enabled = !busy, onClick = {
            busy = true
            PremiumVerification.prepareCheckout({ owner, product ->
                uid = owner
                fun load() { billing.loadOffers(product) {
                    offers = it
                    busy = false
                    status = if (it.isEmpty()) R.string.premium_unavailable else null
                } }
                if (!FirebaseAuth.getInstance().currentUser.hasPermanentIdentity() && (recoveryCode.isBlank() || recoveryOwner != owner)) {
                    PremiumVerification.recoveryCode { code ->
                        if (code == null) { busy = false; status = R.string.premium_unavailable }
                        else if (FirebaseAuth.getInstance().currentUser?.uid == owner && recoveryStore.write(owner, code)) { recoveryCode = code; recoveryOwner = owner; savedCode = false; load() }
                        else { busy = false; status = R.string.premium_unavailable }
                    }
                } else load()
            }, { busy = false; status = R.string.premium_unavailable })
        }) { Text("Premium") }
        if (recoveryCode.isNotBlank()) {
            androidx.compose.foundation.text.selection.SelectionContainer { Text(recoveryCode) }
            RowRecoveryConfirmation(savedCode) { savedCode = it }
        }
        offers.forEach { details ->
            details.subscriptionOfferDetails.orEmpty().forEach { offer ->
                OutlinedButton(enabled = !busy && (FirebaseAuth.getInstance().currentUser.hasPermanentIdentity() || (recoveryOwner == uid && recoveryCode.isNotBlank() && savedCode)), onClick = {
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
    OutlinedTextField(recoveryInput, { recoveryInput = it }, label = { Text("Recovery code") },
        singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    OutlinedButton(enabled = !busy && recoveryInput.trim().matches(Regex("[A-Za-z0-9_-]{43}")), onClick = {
        requestedRecoveryCode = recoveryInput.trim(); recovering = true; busy = true; billing.restorePurchases()
    }) { Text(stringResource(R.string.premium_restore)) }
    if (entitlement != null && PremiumAccess.allowed() && !registered &&
        FirebaseAuth.getInstance().currentUser?.providerData?.none { it.providerId == "password" || it.providerId == "google.com" } == true) {
        OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) },
            singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Button(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && user.providerData.none { it.providerId == "password" || it.providerId == "google.com" } && PremiumAccess.allowed() && !busy) {
                busy = true
                user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password))
                    .addOnCompleteListener { task ->
                        busy = false
                        if (task.isSuccessful) {
                            registered = true
                            password = ""
                            if (recoveryCode.isNotBlank() && recoveryOwner == task.result?.user?.uid) {
                                val finishingCode = recoveryCode
                                PremiumVerification.finishRecovery(finishingCode) { confirmed ->
                                    if (confirmed && recoveryCode == finishingCode) { recoveryStore.clear(recoveryOwner); recoveryCode = ""; recoveryInput = "" }
                                    else if (!confirmed) status = R.string.premium_unavailable
                                }
                            }
                            task.result?.user?.sendEmailVerification()
                        } else status = R.string.auth_failed
                    }
            }
        }) { Text(stringResource(R.string.create_account)) }
    }
    if (recoveryCode.isNotBlank() && FirebaseAuth.getInstance().currentUser.hasPermanentIdentity() && recoveryOwner == FirebaseAuth.getInstance().currentUser?.uid) {
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            val finishingCode = recoveryCode
            val finishingOwner = recoveryOwner
            PremiumVerification.finishRecovery(finishingCode) { confirmed ->
                busy = false
                if (confirmed && recoveryCode == finishingCode && recoveryOwner == finishingOwner) { recoveryStore.clear(finishingOwner); recoveryCode = ""; recoveryInput = ""; status = null }
                else status = R.string.premium_unavailable
            }
        }) { Text(stringResource(R.string.premium_restore)) }
    }
    if (busy) CircularProgressIndicator()
    status?.let { Text(stringResource(it)) }
}

@Composable
private fun RowRecoveryConfirmation(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row {
        Checkbox(checked, onCheckedChange = onChange)
        Text("I saved the recovery code securely")
    }
}

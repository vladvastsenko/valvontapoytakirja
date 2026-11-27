package fi.vladi.valvontapoytakirja.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

@Composable
internal fun PremiumRefresh(context: Context) {
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            val next = it.currentUser?.uid
            if (next != uid) {
                PremiumAccess.clear()
                uid = next
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val owner = uid
    if (owner != null) {
        val billing = remember(owner) {
            ProBilling(context) { result, purchases ->
                if (auth.currentUser?.uid == owner) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        PremiumAccess.clear()
                    } else {
                    val purchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isSuspended &&
                        it.accountIdentifiers?.obfuscatedAccountId == PremiumVerification.accountId(owner) }
                    if (purchase == null) PremiumAccess.clear()
                    else PremiumVerification.verify(purchase.purchaseToken) {}
                    }
                }
            }
        }
        DisposableEffect(billing) { onDispose { billing.close() } }
        LaunchedEffect(billing) {
            while (true) {
                billing.restorePurchases()
                delay(120_000)
            }
        }
    }
}

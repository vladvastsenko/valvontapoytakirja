package fi.vladi.valvontapoytakirja.billing

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.vladi.valvontapoytakirja.R
import com.android.billingclient.api.BillingClient

@Composable
fun PremiumControls() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<Int?>(null) }
    val billing = remember(context) {
        ProBilling(context) { result, purchases ->
            status = when {
                result.responseCode != BillingClient.BillingResponseCode.OK -> R.string.premium_unavailable
                purchases.isEmpty() -> R.string.premium_none
                else -> R.string.premium_unverified
            }
        }
    }
    DisposableEffect(billing) { onDispose { billing.close() } }
    Text("Premium")
    Text(stringResource(R.string.premium_unavailable))
    OutlinedButton(onClick = { billing.restorePurchases() }) { Text(stringResource(R.string.premium_restore)) }
    status?.let { Text(stringResource(it)) }
}

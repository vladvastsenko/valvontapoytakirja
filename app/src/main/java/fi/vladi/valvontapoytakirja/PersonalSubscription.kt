package fi.vladi.valvontapoytakirja

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.*

internal const val SUBSCRIPTION_PRODUCT_ID = "remove_ads_monthly"

// Client-side status is for testing; production needs server-side purchase verification.
internal class PersonalSubscription(private val activity: Activity) {
    var active by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var price by mutableStateOf("")
        private set
    var status by mutableStateOf("")
        private set
    private var product: ProductDetails? = null
    private val client = BillingClient.newBuilder(activity)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                handlePurchases(purchases)
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                status = result.debugMessage
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) { refresh(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() { ready = false; active = false }
            override fun onBillingSetupFinished(result: BillingResult) {
                ready = result.responseCode == BillingClient.BillingResponseCode.OK
                if (ready) {
                    refresh()
                    val params = QueryProductDetailsParams.newBuilder().setProductList(
                        listOf(QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(SUBSCRIPTION_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS).build())
                    ).build()
                    client.queryProductDetailsAsync(params) { response, products ->
                        if (response.responseCode == BillingClient.BillingResponseCode.OK) {
                            product = products.firstOrNull()
                            price = product?.subscriptionOfferDetails?.firstOrNull()
                                ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice.orEmpty()
                        }
                    }
                } else status = result.debugMessage
            }
        })
    }

    fun refresh() {
        if (!client.isReady) { connect(); return }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                active = false
                handlePurchases(purchases)
            } else { active = false; status = result.debugMessage }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        active = purchases.any { SUBSCRIPTION_PRODUCT_ID in it.products &&
            it.purchaseState == Purchase.PurchaseState.PURCHASED }
        purchases.filter { SUBSCRIPTION_PRODUCT_ID in it.products &&
            it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { purchase ->
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken).build()) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) status = result.debugMessage
                }
            }
    }

    fun purchase() {
        val details = product ?: run { status = activity.getString(R.string.subscription_unavailable); return }
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: run {
            status = activity.getString(R.string.subscription_unavailable); return
        }
        val param = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).setOfferToken(offer.offerToken).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(param)).build())
    }

    fun manage() {
        activity.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions")))
    }

    fun close() { client.endConnection() }
}

@Composable
internal fun SubscriptionSection(subscription: PersonalSubscription) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.subscription_title))
        Text(if (subscription.active) stringResource(R.string.subscription_active)
            else stringResource(R.string.subscription_free))
        if (!subscription.active) {
            Text(stringResource(R.string.subscription_description))
            Button(onClick = { subscription.purchase() }, enabled = subscription.ready) {
                Text(stringResource(R.string.subscription_buy) +
                    if (subscription.price.isNotBlank()) " · " + subscription.price else "")
            }
        }
        OutlinedButton(onClick = { subscription.refresh() }) {
            Text(stringResource(R.string.subscription_restore))
        }
        OutlinedButton(onClick = { subscription.manage() }) {
            Text(stringResource(R.string.subscription_manage))
        }
        if (subscription.status.isNotBlank()) Text(subscription.status)
    }
}

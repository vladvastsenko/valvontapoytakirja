package fi.vladi.valvontapoytakirja.billing

import android.content.Context
import com.android.billingclient.api.*

class ProBilling(context: Context, private val onResult: (BillingResult, List<Purchase>) -> Unit) : AutoCloseable {
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onResult(result, purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()

    fun restorePurchases() {
        if (client.isReady) query() else client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) query()
                else onResult(result, emptyList())
            }
            override fun onBillingServiceDisconnected() { PremiumAccess.clear() }
        })
    }

    private fun query() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .includeSuspendedSubscriptions(true)
            .build()) { result, purchases -> onResult(result, purchases) }
    }

    override fun close() { client.endConnection() }
}

package fi.vladi.valvontapoytakirja.billing

import android.content.Context
import com.android.billingclient.api.*

class ProBilling(context: Context, private val onResult: (BillingResult, List<Purchase>) -> Unit) : AutoCloseable {
    @Volatile private var closed = false
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> if (!closed) onResult(result, purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()

    fun restorePurchases() {
        if (client.isReady) query() else client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (closed) return
                if (result.responseCode == BillingClient.BillingResponseCode.OK) query()
                else onResult(result, emptyList())
            }
            override fun onBillingServiceDisconnected() { if (!closed) PremiumAccess.clear() }
        })
    }

    fun loadOffers(productId: String, onOffers: (List<ProductDetails>) -> Unit) {
        val action = {
            val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder().setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS).build()
            )).build()
            client.queryProductDetailsAsync(params) { result, details ->
                if (!closed) onOffers(if (result.responseCode == BillingClient.BillingResponseCode.OK) details.productDetailsList else emptyList())
            }
        }
        if (client.isReady) action() else client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (closed) return
                if (result.responseCode == BillingClient.BillingResponseCode.OK) action() else onOffers(emptyList())
            }
            override fun onBillingServiceDisconnected() { if (!closed) PremiumAccess.clear() }
        })
    }

    fun launchPurchase(activity: android.app.Activity, details: ProductDetails, offerToken: String, uid: String): BillingResult {
        require(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == uid)
        val item = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).setOfferToken(offerToken).build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(item))
            .setObfuscatedAccountId(PremiumVerification.accountId(uid)).build())
    }

    private fun query() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .includeSuspendedSubscriptions(true)
            .build()) { result, purchases -> if (!closed) onResult(result, purchases) }
    }

    override fun close() { closed = true; client.endConnection() }
}

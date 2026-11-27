package fi.vladi.valvontapoytakirja.billing

enum class SubscriptionState { UNKNOWN, FREE, PENDING, ACTIVE, SUSPENDED, EXPIRED }

data class VerifiedSubscription(val uid: String, val state: SubscriptionState, val expiresAtMillis: Long) {
    fun permits(user: String?, now: Long): Boolean =
        user != null && user == uid && state == SubscriptionState.ACTIVE && now < expiresAtMillis
}

object PremiumAccess {
    // Never infer an active entitlement from a local purchase callback.
    @Volatile private var verified: VerifiedSubscription? = null
    fun clear() { verified = null }
    internal fun acceptServerResult(result: VerifiedSubscription) { verified = result }
    fun allowed(): Boolean = verified?.permits(
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull(),
        System.currentTimeMillis()
    ) == true
}

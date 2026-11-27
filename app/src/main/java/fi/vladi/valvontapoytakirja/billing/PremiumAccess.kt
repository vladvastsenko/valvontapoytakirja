package fi.vladi.valvontapoytakirja.billing

enum class SubscriptionState { UNKNOWN, FREE, PENDING, ACTIVE, SUSPENDED, EXPIRED }

data class VerifiedSubscription(val uid: String, val state: SubscriptionState, val expiresAtMillis: Long) {
    fun permits(user: String?, now: Long): Boolean =
        user != null && user == uid && state == SubscriptionState.ACTIVE && now < expiresAtMillis
}

object PremiumAccess {
    // Never infer an active entitlement from a local purchase callback.
    private val verified = kotlinx.coroutines.flow.MutableStateFlow<VerifiedSubscription?>(null)
    val changes: kotlinx.coroutines.flow.StateFlow<VerifiedSubscription?> = verified
    internal fun markFree(uid: String) { PremiumVerification.invalidatePending(); verified.value = VerifiedSubscription(uid, SubscriptionState.FREE, 0) }
    fun clear() { PremiumVerification.invalidatePending(); verified.value = null }
    internal fun acceptServerResult(result: VerifiedSubscription) { verified.value = result }
    fun allowed(): Boolean = verified.value?.permits(
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull(),
        System.currentTimeMillis()
    ) == true
}

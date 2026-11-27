package fi.vladi.valvontapoytakirja.billing

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import java.security.MessageDigest

internal object PremiumVerification {
    private val auth get() = FirebaseAuth.getInstance()
    private val functions get() = FirebaseFunctions.getInstance("europe-west1")

    fun accountId(uid: String): String =
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun prepareCheckout(onReady: (String, String) -> Unit, onFailure: () -> Unit) {
        fun load(uid: String) {
            functions.getHttpsCallable("getTarkastusProCheckoutConfig").call()
                .addOnCompleteListener { task ->
                    val product = (task.resultOrNull()?.data as? Map<*, *>)?.get("productId") as? String
                    if (auth.currentUser?.uid == uid && !product.isNullOrBlank()) onReady(uid, product)
                    else onFailure()
                }
        }
        val existing = auth.currentUser
        if (existing != null) load(existing.uid)
        else auth.signInAnonymously().addOnCompleteListener { task ->
            val uid = if (task.isSuccessful) task.result?.user?.uid else null
            if (uid == null) onFailure() else load(uid)
        }
    }

    fun recoveryCode(onComplete: (String?) -> Unit) {
        val owner = auth.currentUser?.uid ?: run { onComplete(null); return }
        functions.getHttpsCallable("prepareTarkastusProRecovery").call().addOnCompleteListener {
            onComplete(if (auth.currentUser?.uid == owner) (it.resultOrNull()?.data as? Map<*, *>)?.get("secret") as? String else null)
        }
    }

    fun recover(secret: String, token: String, onComplete: (Boolean) -> Unit) {
        fun request() {
            val requester = auth.currentUser?.uid ?: run { onComplete(false); return }
            functions.getHttpsCallable("recoverTarkastusProAccount")
                .call(mapOf("secret" to secret, "token" to token)).addOnCompleteListener {
                    val custom = (it.resultOrNull()?.data as? Map<*, *>)?.get("customToken") as? String
                    if (custom.isNullOrBlank() || auth.currentUser?.uid != requester || auth.currentUser.hasPermanentIdentity()) onComplete(false)
                    else auth.signInWithCustomToken(custom).addOnCompleteListener { signIn ->
                        if (signIn.isSuccessful) verify(token, onComplete) else onComplete(false)
                    }
                }
        }
        if (auth.currentUser != null && !auth.currentUser.hasPermanentIdentity()) request()
        else if (auth.currentUser == null) auth.signInAnonymously().addOnCompleteListener {
            if (it.isSuccessful) request() else onComplete(false)
        } else onComplete(false)
    }

    fun finishRecovery(secret: String, onComplete: (Boolean) -> Unit = {}) {
        val owner = auth.currentUser?.uid
        if (secret.isBlank() || owner == null) { onComplete(false); return }
        functions.getHttpsCallable("finishTarkastusProRecovery").call(mapOf("secret" to secret))
            .addOnCompleteListener { task ->
                val confirmed = (task.resultOrNull()?.data as? Map<*, *>)?.get("ok") == true
                onComplete(confirmed && auth.currentUser?.uid == owner)
            }
    }

    private val verificationGeneration = java.util.concurrent.atomic.AtomicLong()

    internal fun invalidatePending() { verificationGeneration.incrementAndGet() }

    fun verify(token: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { PremiumAccess.clear(); onComplete(false); return }
        val generation = verificationGeneration.incrementAndGet()
        functions.getHttpsCallable("verifyTarkastusProSubscription")
            .call(mapOf("token" to token)).addOnCompleteListener { task ->
                val data = task.resultOrNull()?.data as? Map<*, *>
                val state = runCatching { SubscriptionState.valueOf(data?.get("state") as? String ?: "") }.getOrNull()
                val expiry = (data?.get("expiresAtMillis") as? Number)?.toLong()
                if (auth.currentUser?.uid != uid) { onComplete(false); return@addOnCompleteListener }
                if (verificationGeneration.get() != generation) { onComplete(false); return@addOnCompleteListener }
                if (data?.get("uid") != uid || state == null || expiry == null) {
                    PremiumAccess.clear()
                    onComplete(false)
                } else {
                    val result = VerifiedSubscription(uid, state, expiry)
                    PremiumAccess.acceptServerResult(result)
                    onComplete(result.permits(uid, System.currentTimeMillis()))
                }
            }
    }

    private fun <T> com.google.android.gms.tasks.Task<T>.resultOrNull(): T? =
        if (isSuccessful) result else null
}

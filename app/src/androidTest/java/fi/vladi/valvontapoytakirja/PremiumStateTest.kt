package fi.vladi.valvontapoytakirja

import fi.vladi.valvontapoytakirja.billing.*
import org.junit.Test
import org.junit.Assert.*

class PremiumStateTest {
    @Test fun allStatesFailClosedExceptVerifiedActive() {
        SubscriptionState.values().forEach { state ->
            val subscription = VerifiedSubscription("owner", state, 2000)
            assertEquals(state == SubscriptionState.ACTIVE, subscription.permits("owner", 1000))
            assertFalse(subscription.permits("other", 1000))
            assertFalse(subscription.permits(null, 1000))
            assertFalse(subscription.permits("owner", 2000))
        }
    }
}

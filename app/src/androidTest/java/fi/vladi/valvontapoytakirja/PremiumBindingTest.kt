package fi.vladi.valvontapoytakirja

import fi.vladi.valvontapoytakirja.billing.PremiumVerification
import org.junit.Test
import org.junit.Assert.*

class PremiumBindingTest {
    @Test fun accountHashMatchesServerSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PremiumVerification.accountId("abc"))
        assertNotEquals(PremiumVerification.accountId("abc"), PremiumVerification.accountId("ABC"))
    }
}

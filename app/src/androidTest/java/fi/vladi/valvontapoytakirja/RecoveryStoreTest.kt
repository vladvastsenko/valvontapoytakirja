package fi.vladi.valvontapoytakirja

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.vladi.valvontapoytakirja.billing.RecoveryStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryStoreTest {
    @Test fun encryptedCodeSurvivesReopeningAndIsScopedToOwner() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uid = java.util.UUID.randomUUID().toString()
        val other = java.util.UUID.randomUUID().toString()
        val code = "A".repeat(43)
        val store = RecoveryStore(context)
        try {
            assertTrue(store.write(uid, code))
            assertEquals(code, RecoveryStore(context).read(uid))
            assertNull(store.read(other))
            assertFalse(store.write(uid, "invalid"))
            assertEquals(code, store.read(uid))
            store.clear(uid)
            assertNull(store.read(uid))
        } finally { store.clear(uid); store.clear(other) }
    }
}

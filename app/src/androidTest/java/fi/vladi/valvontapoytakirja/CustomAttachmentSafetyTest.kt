package fi.vladi.valvontapoytakirja

import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class CustomAttachmentSafetyTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun attachmentPathsCannotEscapeStore() {
        val store = CustomStore(folder.newFolder())
        for (path in listOf("../secret.png", "/secret.png", "sub/file.png", "library.json")) {
            try { store.image(path); fail("Accepted unsafe path: " + path) }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun newDocumentsDoNotInheritSharedSettings() {
        assertFalse(CustomDocument().useSharedSettings)
    }
}

package fi.vladi.valvontapoytakirja
import android.util.AtomicFile
import java.io.File
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class CustomRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun interruptedReplacementPreservesCommittedDocument() {
        val root = folder.newFolder()
        val store = CustomStore(root)
        val doc = CustomDocument(values = mapOf("field" to "saved text"))
        store.saveDocument(doc)
        store.rememberEditor("d:" + doc.id)
        val record = File(root,"records").listFiles()!!.single { it.name.startsWith("d-") }
        val interrupted = AtomicFile(record).startWrite()
        interrupted.write("partial".toByteArray())
        interrupted.close()
        val reopened = CustomStore(root)
        assertEquals(doc,reopened.load().documents.single())
        assertEquals("d:" + doc.id,reopened.activeEditor())
    }
    @Test fun missingMigrationMarkerDoesNotOverwriteNewerRecord() {
        val root = folder.newFolder()
        val store = CustomStore(root)
        val doc = CustomDocument(values = mapOf("field" to "new"))
        store.saveDocument(doc)
        store.load()
        File(root,"records/ready").delete()
        assertEquals(doc,CustomStore(root).load().documents.single())
    }

    @Test fun sharedSelectionSurvivesReopening() {
        val root = folder.newFolder()
        val doc = CustomDocument(useSharedSettings = false, customerId = "c", customerText = "Customer", siteId = "s", siteText = "Site", inspectorId = "i", inspectorText = "Inspector")
        CustomStore(root).saveDocument(doc)
        assertEquals(doc, CustomStore(root).load().documents.single())
    }
    @Test fun deletingTemplatePreservesReportAndDeletingReportPreservesOtherTemplate() {
        val root = folder.newFolder()
        val store = CustomStore(root)
        val template = CustomTemplate(title = "Original")
        val other = CustomTemplate(title = "Other")
        val doc = CustomDocument(template = template)
        store.save(CustomLibrary(listOf(template, other), listOf(doc)))
        store.load()
        store.rememberEditor("t:" + template.id)
        store.deleteTemplate(template.id)
        val reopened = CustomStore(root)
        assertEquals(listOf(other), reopened.load().templates)
        assertEquals(listOf(doc), reopened.load().documents)
        assertNull(reopened.activeEditor())
        reopened.rememberEditor("d:" + doc.id)
        reopened.deleteDocument(doc.id)
        assertTrue(CustomStore(root).load().documents.isEmpty())
        assertEquals(listOf(other), CustomStore(root).load().templates)
        assertNull(reopened.activeEditor())
    }

    @Test fun deletedRecordsCannotReturnAndSharedAttachmentsSurviveCleanup() {
        val root = folder.newFolder()
        val store = CustomStore(root)
        val shared = customId() + ".png"
        val unused = customId() + ".image"
        for (name in listOf(shared, unused)) {
            val file = store.image(name)
            file.writeBytes(byteArrayOf(1,2,3))
            assertTrue(file.setLastModified(System.currentTimeMillis() - 9L * 86400000L))
        }
        val first = CustomDocument(attachments = mapOf("field" to listOf(shared, unused)))
        val second = CustomDocument(attachments = mapOf("field" to listOf(shared)))
        store.save(CustomLibrary(documents = listOf(first, second)))
        store.deleteDocument(first.id)
        store.saveDocument(first)
        File(root,"records/ready").delete()
        val reopened = CustomStore(root)
        assertEquals(listOf(second), reopened.load().documents)
        reopened.cleanUnused(reopened.load())
        assertTrue(reopened.image(shared).exists())
        assertFalse(reopened.image(unused).exists())
    }
}

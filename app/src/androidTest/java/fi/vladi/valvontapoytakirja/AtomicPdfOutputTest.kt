package fi.vladi.valvontapoytakirja

import androidx.test.platform.app.InstrumentationRegistry
import fi.vladi.valvontapoytakirja.pdf.AtomicPdfOutput
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class AtomicPdfOutputTest {
    @Test fun failedExportPreservesPreviousFile() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("atomic_pdf", ".pdf", dir)
        try {
            file.writeText("previous")
            try {
                AtomicPdfOutput.write(file) {
                    it.write("partial".toByteArray())
                    throw IOException("simulated failure")
                }
                fail("Expected export failure")
            } catch (_: IOException) {}
            assertEquals("previous", file.readText())
            AtomicPdfOutput.write(file) { it.write("replacement".toByteArray()) }
            assertEquals("replacement", file.readText())
        } finally {
            android.util.AtomicFile(file).delete()
        }
    }
}

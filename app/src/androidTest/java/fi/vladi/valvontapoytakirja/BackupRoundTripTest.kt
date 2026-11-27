package fi.vladi.valvontapoytakirja

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.vladi.valvontapoytakirja.cloud.CloudBackupManager
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @Test fun exportRestoreAndEmergencyExport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = File(context.filesDir, "backup-roundtrip-marker.txt")
        val archive = File(context.cacheDir, "roundtrip.zip")
        val recovery = File(context.cacheDir, "recovery.zip")
        try {
            data.writeText("before")
            CloudBackupManager.backupToDocument(context, Uri.fromFile(archive))
            data.writeText("after")
            CloudBackupManager.restoreFromUri(context, Uri.fromFile(archive))
            assertEquals("before", data.readText())
            CloudBackupManager.backupToDocument(context, Uri.fromFile(recovery), true)
            CloudBackupManager.restoreFromUri(context, Uri.fromFile(recovery))
            assertEquals("after", data.readText())
        } finally {
            data.delete()
            archive.delete()
            recovery.delete()
        }
    }
}

package fi.vladi.valvontapoytakirja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import android.util.AtomicFile
import fi.vladi.valvontapoytakirja.data.ReportRepository as WetRepository
import fi.vladi.valvontapoytakirja.model.InspectionReport as WetReport
import fi.vladi.tarkastuspoytakirja.data.ReportRepository as GeneralRepository
import fi.vladi.tarkastuspoytakirja.model.InspectionReport as GeneralReport

@RunWith(AndroidJUnit4::class)
class RepositoryRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun wetReportRecovery() {
        val repo = WetRepository(context)
        val report = WetReport(siteAddress = "Recovery test wet")
        try {
            repo.save(report)
            val file = File(context.filesDir, "reports/${report.id}.json")
            AtomicFile(file).startWrite().use { it.write("broken".toByteArray()) }
            assertEquals(report, repo.listReports().single { it.id == report.id })
            repo.save(report.copy(siteAddress = "Updated"))
            assertEquals("Updated", repo.listReports().single { it.id == report.id }.siteAddress)
        } finally { repo.deleteReport(report.id) }
    }
    @Test fun generalReportRecovery() {
        val repo = GeneralRepository(context)
        val report = GeneralReport(siteAddress = "Recovery test general")
        try {
            repo.save(report)
            val file = File(context.filesDir, "tarkastus_reports/${report.id}.json")
            AtomicFile(file).startWrite().use { it.write("broken".toByteArray()) }
            assertEquals(report, repo.listReports().single { it.id == report.id })
            repo.save(report.copy(siteAddress = "Updated"))
            assertEquals("Updated", repo.listReports().single { it.id == report.id }.siteAddress)
        } finally { repo.deleteReport(report.id) }
    }
}

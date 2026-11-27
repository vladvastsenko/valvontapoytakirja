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

    @Test fun sharedSettingsConcurrentWrites() {
        val wet = WetRepository(context)
        val general = GeneralRepository(context)
        val before = wet.loadSettings()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val a = pool.submit { repeat(20) { wet.saveSettings(before.copy(headerText = "wet-$it")) } }
            val b = pool.submit { repeat(20) { general.saveSettings(general.loadSettings().copy(headerText = "general-$it")) } }
            a.get()
            b.get()
            val value = wet.loadSettings().headerText
            assertTrue(value == "wet-19" || value == "general-19")
            assertEquals(value, general.loadSettings().headerText)
        } finally { pool.shutdownNow(); wet.saveSettings(before) }
    }

    @Test fun sharedCustomerAndSiteRecovery() {
        val wet = WetRepository(context)
        val general = GeneralRepository(context)
        val customer = fi.vladi.valvontapoytakirja.model.CustomerProfile(name = "Recovery customer")
        val site = fi.vladi.valvontapoytakirja.model.CustomerSite(customerId = customer.id, name = "Recovery site")
        try {
            wet.saveCustomer(customer)
            wet.saveSite(site)
            for (relative in listOf("customers/${customer.id}.json", "customer_sites/${site.id}.json")) {
                AtomicFile(File(context.filesDir, relative)).startWrite().use { it.write("broken".toByteArray()) }
            }
            assertEquals(customer.name, general.listCustomers().single { it.id == customer.id }.name)
            assertEquals(site.name, general.listSites(customer.id).single { it.id == site.id }.name)
        } finally { wet.deleteCustomer(customer.id) }
    }

    @Test fun bothTemplateRecovery() {
        val wet = WetRepository(context)
        val general = GeneralRepository(context)
        val name = java.util.UUID.randomUUID().toString()
        wet.saveTemplate(WetReport(), name)
        general.saveTemplate(GeneralReport(), name)
        val w = wet.listTemplates().single { it.templateName == name }
        val g = general.listTemplates().single { it.templateName == name }
        val files = listOf(File(context.filesDir, "templates/${w.id}.json"), File(context.filesDir, "tarkastus_templates/${g.id}.json"))
        try {
            files.forEach { AtomicFile(it).startWrite().use { stream -> stream.write("broken".toByteArray()) } }
            assertEquals(w, wet.listTemplates().single { it.id == w.id })
            assertEquals(g, general.listTemplates().single { it.id == g.id })
        } finally { files.forEach { AtomicFile(it).delete() } }
    }
}

package fi.vladi.valvontapoytakirja.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class InspectionReport(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: String = LocalDate.now().toString(),
    val folderPath: String = "",
    val templateName: String = "",

    // Työmaa ja osapuolet
    val siteAddress: String = "",
    val clientOrRepresentative: String = "",
    val clientEmail: String = "",
    val installerName: String = "",
    val installerCertificateNumber: String = "",
    val installerPhone: String = "",
    val installerEmail: String = "",
    val workStartDate: String = "",
    val workEndDate: String = "",

    // Alustat ja työolosuhteet
    val wallSubstrate: String = "",
    val floorSubstrate: String = "",
    val workInstructionsProvidedAndRead: Boolean = false,
    val roomTemperatureC: String = "",
    val relativeHumidityPercent: String = "",
    val substrateMoisturePercent: String = "",

    // Tuotteet – valmistaja ja tuotteen nimi ovat vapaita kenttiä
    val materials: List<MaterialEntry> = defaultMaterialEntries(),

    // Rakenteet ja mitat
    val floorHeating: String = "",
    val thresholdOrDrainLiftMm: String = "",
    val floorSlopes: String = "",

    // Pinta-alat ja menekit
    val wallAreaM2: String = "",
    val wallWaterproofingConsumptionLitres: String = "",
    val floorAreaM2: String = "",
    val floorWaterproofingConsumptionLitres: String = "",

    // Kalvon paksuudet
    val wallDryFilmThicknessMm: String = "",
    val wallRequiredMinimumThicknessMm: String = "",
    val floorDryFilmThicknessMm: String = "",
    val floorRequiredMinimumThicknessMm: String = "",
    // Tarkastuksen tulos ja tarkastaja
    val inspectionResult: String = "HYVÄKSYTTY",
    val inspectorName: String = "",
    val inspectorCertificateNumber: String = "",
    val inspectorPhone: String = "",
    val inspectorEmail: String = "",
    val inspectionDate: String = LocalDate.now().toString(),
    val additionalNotes: String = "",
    val signatureStrokes: List<SignatureStroke> = emptyList(),

    // PDF:n ulkoasu
    val logoPath: String = "",
    val footerText: String = "",

    // Valokuvat tallennetaan sovelluksen omaan tallennustilaan
    val photos: List<ReportPhoto> = emptyList()
)

@Serializable
data class MaterialEntry(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val manufacturer: String = "",
    val productName: String = "",
    val notes: String = ""
)

@Serializable
data class SignatureStroke(
    val points: List<SignaturePoint>
)

@Serializable
data class SignaturePoint(
    val x: Float,
    val y: Float
)

@Serializable
data class ReportPhoto(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val caption: String = ""
)

fun defaultMaterialEntries(): List<MaterialEntry> = listOf(
    "Tasoite",
    "Pohjuste (primer)",
    "Vedeneriste",
    "Kaivotyyppi",
    "Kaivolaippa / lattiakaivovahvike",
    "Nurkkavahvike / vahvikenauha",
    "Läpivientivahvike",
    "Läpivientien tiivistysmassa",
    "Kiinnityslaasti",
    "Laatat",
    "Seinäsaumaus",
    "Lattiasaumaus",
    "Elastinen saumausmassa"
).map { category ->
    val hasKiiltoProducts = category !in listOf("Kaivotyyppi", "Laatat")
    MaterialEntry(category = category, manufacturer = if (hasKiiltoProducts) "Kiilto" else "")
}

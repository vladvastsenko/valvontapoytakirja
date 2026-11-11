package fi.vladi.tarkastuspoytakirja.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class InspectionReport(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: String = LocalDate.now().toString(),
    val folderPath: String = "",
    val templateName: String = "",
    val siteAddress: String = "",
    val clientOrRepresentative: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val purposeAndScope: String = "",
    val generalObservations: String = "",
    val customFields: List<CustomReportField> = emptyList(),
    val observations: List<InspectionObservation> = emptyList(),
    val summary: String = "",
    val inspectorName: String = "",
    val inspectorCertificateNumber: String = "",
    val inspectorPhone: String = "",
    val inspectorEmail: String = "",
    val inspectionDate: String = LocalDate.now().toString(),
    val signatureStrokes: List<SignatureStroke> = emptyList(),
    val logoPath: String = "",
    val footerText: String = ""
)

@Serializable
data class CustomReportField(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = ""
)

@Serializable
data class InspectionObservation(
    val id: String = UUID.randomUUID().toString(),
    val location: String = "",
    val description: String = "",
    val recommendedAction: String = "",
    val photos: List<ReportPhoto> = emptyList()
)

@Serializable
data class SignatureStroke(val points: List<SignaturePoint>)

@Serializable
data class SignaturePoint(val x: Float, val y: Float)

@Serializable
data class ReportPhoto(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val caption: String = "",
    val annotatedPath: String = ""
)

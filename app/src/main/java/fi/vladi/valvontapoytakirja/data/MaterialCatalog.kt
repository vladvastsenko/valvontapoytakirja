package fi.vladi.valvontapoytakirja.data

/**
 * Kiilto starter catalogue for wet-room waterproofing inspection reports.
 * Product choices are grouped by report position so other manufacturers can
 * be added later without changing the report model.
 */
data class CatalogProduct(
    val manufacturer: String,
    val name: String
)

object MaterialCatalog {
    private fun kiilto(vararg names: String): List<CatalogProduct> =
        names.map { CatalogProduct(manufacturer = "Kiilto", name = it) }

    fun productsFor(category: String): List<CatalogProduct> = when (category.lowercase()) {
        "tasoite" -> kiilto(
            "SR DF Pikamärkätilatasoite",
            "SK Märkätilatasoite",
            "TM DF Seinän pikatäyttömassa",
            "OT Light Kevyttäytteinen seinän oikaisutasoite",
            "OT Seinän oikaisutasoite",
            "TT Seinätasoite",
            "60 Pikalattiamassa",
            "70 Lattiamassa",
            "80 Oikaisutasoite",
            "Floor Heat DF Lattialämmitystasoite",
            "TopPlan DF Lattiatasoite",
            "Maxirapid Erikoisnopea tasoite",
            "Pro Easyrapid Erikoisnopea hienotasoite"
        )
        "pohjuste (primer)" -> kiilto(
            "PrimerOne",
            "Pro Keraprimer",
            "Pro Start Primer"
        )
        "vedeneriste" -> kiilto(
            "Kerafiber",
            "KeraPro",
            "Keramix",
            "KeraSafe+",
            "Pro KeraSafe Project"
        )
        "kaivolaippa / lattiakaivovahvike" -> kiilto(
            "Easy Kaivolaippa",
            "KeraSafe Kaivolaippa",
            "Lattiakaivovahvike"
        )
        "nurkkavahvike / vahvikenauha" -> kiilto("Nurkkavahvikenauha")
        "läpivientivahvike" -> kiilto("Läpivientivahvike")
        "läpivientien tiivistysmassa" -> kiilto("Pro XPU")
        "kiinnityslaasti" -> kiilto(
            "Highflex S2 DF",
            "Superfix DF",
            "Pro Lightfix",
            "Flexfix",
            "Kerapid DF",
            "Pro XL Fix S2",
            "Floorfix DF Rapid",
            "Floorfix DF"
        )
        "seinäsaumaus" -> kiilto("Pro Tile Grout", "Pro Epoxy Tile Grout")
        "lattiasaumaus" -> kiilto("Pro Floor Tile Grout", "Pro Epoxy Tile Grout")
        "elastinen saumausmassa" -> kiilto("Pro Sanitary Silicone", "Pro Silicone E")
        else -> emptyList()
    }
}

package fi.vladi.valvontapoytakirja.data

/**
 * Kiilto starter catalogue for wet-room waterproofing inspection reports.
 * Product choices are grouped by report position so other manufacturers can
 * be added later without changing the report model.
 */
object MaterialCatalog {
    fun productsFor(category: String): List<String> = when (category.lowercase()) {
        "tasoite" -> listOf(
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
        "pohjuste (primer)" -> listOf(
            "PrimerOne",
            "Pro Keraprimer",
            "Pro Start Primer"
        )
        "vedeneriste" -> listOf(
            "Kerafiber",
            "KeraPro",
            "Keramix",
            "KeraSafe+",
            "Pro KeraSafe Project"
        )
        "kaivolaippa / lattiakaivovahvike" -> listOf(
            "Easy Kaivolaippa",
            "KeraSafe Kaivolaippa",
            "Lattiakaivovahvike"
        )
        "nurkkavahvike / vahvikenauha" -> listOf(
            "Nurkkavahvikenauha"
        )
        "läpivientivahvike" -> listOf(
            "Läpivientivahvike"
        )
        "läpivientien tiivistysmassa" -> listOf(
            "Pro XPU"
        )
        "kiinnityslaasti" -> listOf(
            "Highflex S2 DF",
            "Superfix DF",
            "Pro Lightfix",
            "Flexfix",
            "Kerapid DF",
            "Pro XL Fix S2",
            "Floorfix DF Rapid",
            "Floorfix DF"
        )
        "seinäsaumaus" -> listOf(
            "Pro Tile Grout",
            "Pro Epoxy Tile Grout"
        )
        "lattiasaumaus" -> listOf(
            "Pro Floor Tile Grout",
            "Pro Epoxy Tile Grout"
        )
        "elastinen saumausmassa" -> listOf(
            "Pro Sanitary Silicone",
            "Pro Silicone E"
        )
        else -> emptyList()
    }
}

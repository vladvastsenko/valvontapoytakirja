package fi.vladi.valvontapoytakirja.data

/**
 * Manufacturer products grouped by the report position.
 * Product names are based on the manufacturers' Finnish product catalogues
 * and wet-room inspection/system documents.
 */
data class CatalogProduct(
    val manufacturer: String,
    val name: String
)

object MaterialCatalog {
    private fun products(manufacturer: String, vararg names: String): List<CatalogProduct> =
        names.map { CatalogProduct(manufacturer = manufacturer, name = it) }

    fun productsFor(category: String): List<CatalogProduct> {
        val key = category.lowercase()
        val kiilto = when (key) {
            "tasoite" -> products("Kiilto",
                "SR DF Pikamärkätilatasoite", "SK Märkätilatasoite", "TM DF Seinän pikatäyttömassa",
                "OT Light Kevyttäytteinen seinän oikaisutasoite", "OT Seinän oikaisutasoite",
                "TT Seinätasoite", "60 Pikalattiamassa", "70 Lattiamassa", "80 Oikaisutasoite",
                "Floor Heat DF Lattialämmitystasoite", "TopPlan DF Lattiatasoite",
                "Maxirapid Erikoisnopea tasoite", "Pro Easyrapid Erikoisnopea hienotasoite")
            "pohjuste (primer)" -> products("Kiilto", "PrimerOne", "Pro Keraprimer", "Pro Start Primer")
            "vedeneriste" -> products("Kiilto", "Kerafiber", "KeraPro", "Keramix", "KeraSafe+", "Pro KeraSafe Project")
            "kaivolaippa / lattiakaivovahvike" -> products("Kiilto", "Easy Kaivolaippa", "KeraSafe Kaivolaippa", "Lattiakaivovahvike")
            "nurkkavahvike / vahvikenauha" -> products("Kiilto", "Nurkkavahvikenauha")
            "läpivientivahvike" -> products("Kiilto", "Läpivientivahvike")
            "läpivientien tiivistysmassa" -> products("Kiilto", "Pro XPU")
            "kiinnityslaasti" -> products("Kiilto", "Highflex S2 DF", "Superfix DF", "Pro Lightfix", "Flexfix", "Kerapid DF", "Pro XL Fix S2", "Floorfix DF Rapid", "Floorfix DF")
            "seinäsaumaus" -> products("Kiilto", "Pro Tile Grout", "Pro Epoxy Tile Grout")
            "lattiasaumaus" -> products("Kiilto", "Pro Floor Tile Grout", "Pro Epoxy Tile Grout")
            "elastinen saumausmassa" -> products("Kiilto", "Pro Sanitary Silicone", "Pro Silicone E")
            else -> emptyList()
        }

        val weber = when (key) {
            "tasoite" -> products("weber",
                "MT Märkätilatasoite", "TT+ Täyttötasoite", "MTL Märkätilalaasti", "PTM Pikatäyttömassa",
                "6000 Pikamassa", "3100 Hienotasoite", "5000 Lattiamassa", "5400 Lämpölattiamassa")
            "pohjuste (primer)" -> products("weber", "MD 16 Dispersio", "MS+/MB Kosteussulku")
            "vedeneriste" -> products("weber", "WP+ Vedeneristysmassa", "Superflex D2 Eristyslaasti")
            "kaivolaippa / lattiakaivovahvike" -> products("weber", "webertec DC Kaivolaippa")
            "nurkkavahvike / vahvikenauha" -> products("weber",
                "webertec sealstrip 120 mm Nauha", "IC Sisäkulma", "OC Ulkokulmakappale", "FC Kuitukangasnauha")
            "läpivientivahvike" -> products("weber", "webertec PC Läpivientikappale", "FM Kuitukangaspala")
            "kiinnityslaasti" -> products("weber",
                "rex fix Saneerauslaasti", "Multiglue", "supra light fix Kevytlaasti",
                "supra rapid light fix Nopea kevytlaasti", "flex fix Superjoustava kiinnityslaasti", "rock fix Kiinnityslaasti")
            "seinäsaumaus", "lattiasaumaus" -> products("weber",
                "classic grout Saumalaasti", "rapid grout Saumalaasti", "weberepox easy Epoksisaumalaasti")
            "elastinen saumausmassa" -> products("weber", "neutral silicone", "special silicone")
            else -> emptyList()
        }

        val schonox = when (key) {
            "tasoite" -> products("SCHÖNOX", "BM 10", "FS", "CLS", "HS 10", "HS 40S", "HS 50")
            "pohjuste (primer)" -> products("SCHÖNOX", "KH", "KH Fix", "SHP")
            "vedeneriste" -> products("SCHÖNOX", "HA", "HA Pro", "2K DS RAPID", "iFIX", "WSF")
            "kaivolaippa / lattiakaivovahvike" -> products("SCHÖNOX",
                "MG Lattiakaivonläpivientikappale", "WetStop C", "WetStop KL")
            "nurkkavahvike / vahvikenauha" -> products("SCHÖNOX",
                "Vedeneristysnauha", "WetStop Butyl", "WetStop sisäkulma", "WetStop ulkokulma")
            "läpivientivahvike" -> products("SCHÖNOX", "WetStop putkiläpivienti")
            "kiinnityslaasti" -> products("SCHÖNOX",
                "Q2 W", "Q4 RAPID", "Q6", "Q6 W", "Q8", "Q9 W", "Q12")
            "seinäsaumaus", "lattiasaumaus" -> products("SCHÖNOX", "CF DESIGN", "SF DESIGN")
            "elastinen saumausmassa" -> products("SCHÖNOX", "ES", "MES Plus", "WD FLEX")
            else -> emptyList()
        }

        val ardex = when (key) {
            "tasoite" -> products("ARDEX",
                "B 12 Märkätilatasoite", "AM 100 Täyttömassa", "K 75 Oikaisumassa",
                "K 70 NEW Oikaisumassa", "A 45 Korjausmassa", "A 31 Pikatasoite", "F 5 Viimeistelytasoite")
            "pohjuste (primer)" -> products("ARDEX", "P 51", "P 2 D", "P 82", "P 7")
            "vedeneriste" -> products("ARDEX", "S 1-K PLUS", "8+9", "8+9 LW")
            "kaivolaippa / lattiakaivovahvike" -> products("ARDEX", "STA Kaivokappale")
            "nurkkavahvike / vahvikenauha" -> products("ARDEX", "SK 12 Vahvistusnauha", "SK TRICOM")
            "läpivientivahvike" -> products("ARDEX", "SRM Läpivientikappale")
            "kiinnityslaasti" -> products("ARDEX",
                "X 77", "X 77 S", "X 77 W", "X 78", "X 80", "X 90", "X 32", "S 48", "WA Epoksikiinnitys")
            "seinäsaumaus", "lattiasaumaus" -> products("ARDEX",
                "G8S FLEX", "G10", "FK", "GK", "RG 12", "WA Epoksisaumausaine")
            "elastinen saumausmassa" -> products("ARDEX", "SE Märkätilasilikoni", "ST Luonnonkivisilikoni")
            else -> emptyList()
        }

        val mapei = when (key) {
            "tasoite" -> products("Mapei", "Nivoplan 350", "Planipatch", "Ultraplan Eco", "Topcem Pronto")
            "pohjuste (primer)" -> products("Mapei", "Primer G", "Primer VT Plus", "Eco Prim Grip Plus")
            "vedeneriste" -> products("Mapei", "Mapegum WPS")
            "kaivolaippa / lattiakaivovahvike" -> products("Mapei", "Mapeband B DC")
            "nurkkavahvike / vahvikenauha" -> products("Mapei",
                "Mapeband B T", "Mapeband Easy", "Mapeguard ST", "Mapeguard IC/EC")
            "läpivientivahvike" -> products("Mapei", "Mapeguard PC")
            "läpivientien tiivistysmassa" -> products("Mapei", "Mapeflex MS 45")
            "kiinnityslaasti" -> products("Mapei",
                "Ultralite S1", "Keraflex Maxi S1", "Elastorapid", "Keraquick S1", "Adesilex P9")
            "seinäsaumaus", "lattiasaumaus" -> products("Mapei",
                "Ultracolor Plus", "Keracolor FF", "Kerapoxy", "Kerapoxy CQ")
            "elastinen saumausmassa" -> products("Mapei", "Mapesil AC", "Mapesil LM")
            else -> emptyList()
        }

        return kiilto + weber + schonox + ardex + mapei
    }
}

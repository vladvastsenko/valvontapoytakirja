package fi.vladi.valvontapoytakirja.pdf

/** A human-readable filename, with only characters forbidden by file systems removed. */
internal object PdfFileNames {
    fun name(language: String, kind: String, site: String): String {
        val prefix = when (kind) {
            "valvonta" -> when (language) {
                "ru" -> "Протокол_контроля_гидроизоляции"
                "en" -> "Waterproofing_inspection"
                "sv" -> "Tätskiktskontroll"
                "et" -> "Hüdroisolatsiooni_kontrollakt"
                "lt" -> "Hidroizoliacijos_patikros_aktas"
                "lv" -> "Hidroizolācijas_pārbaudes_akts"
                "nb" -> "Våtromskontroll"
                "da" -> "Vådrumskontrol"
                "fr" -> "Contrôle_étanchéité"
                "es" -> "Inspección_impermeabilización"
                "it" -> "Controllo_impermeabilizzazione"
                "de" -> "Abdichtungsprüfung"
                "pl" -> "Kontrola_hydroizolacji"
                else -> "Valvontapöytäkirja"
            }
            else -> when (language) {
                "ru" -> "Протокол_осмотра"
                "en" -> "Inspection_report"
                "sv" -> "Besiktningsprotokoll"
                "et" -> "Ülevaatusakt"
                "lt" -> "Patikros_aktas"
                "lv" -> "Pārbaudes_akts"
                "nb" -> "Befaringsrapport"
                "da" -> "Inspektionsrapport"
                "fr" -> "Rapport_inspection"
                "es" -> "Informe_inspección"
                "it" -> "Verbale_ispezione"
                "de" -> "Prüfbericht"
                "pl" -> "Protokół_kontroli"
                else -> "Tarkastuspöytäkirja"
            }
        }
        val detail = site.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .trim().trim('_', '.', ' ')
            .replace(Regex("\\s+"), "_")
            .take(100).ifBlank { when(language) { "ru" -> "объект"; "en" -> "site"; "sv" -> "objekt"; "et" -> "objekt"; "lt" -> "objektas"; "lv" -> "objekts"; "nb" -> "objekt"; "da" -> "objekt"; "fr" -> "chantier"; "es" -> "obra"; "it" -> "cantiere"; "de" -> "Objekt"; "pl" -> "obiekt"; else -> "kohde" } }
        return "${prefix}_${detail}.pdf"
    }
}

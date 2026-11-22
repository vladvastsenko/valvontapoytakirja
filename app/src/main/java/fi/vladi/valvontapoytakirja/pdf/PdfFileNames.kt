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
                else -> "Valvontapöytäkirja"
            }
            else -> when (language) {
                "ru" -> "Протокол_осмотра"
                "en" -> "Inspection_report"
                "sv" -> "Besiktningsprotokoll"
                "et" -> "Ülevaatusakt"
                "lt" -> "Patikros_aktas"
                "lv" -> "Pārbaudes_akts"
                else -> "Tarkastuspöytäkirja"
            }
        }
        val detail = site.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .trim().trim('_', '.', ' ')
            .replace(Regex("\\s+"), "_")
            .take(100).ifBlank { when(language) { "ru" -> "объект"; "en" -> "site"; "sv" -> "objekt"; "et" -> "objekt"; "lt" -> "objektas"; "lv" -> "objekts"; else -> "kohde" } }
        return "${prefix}_${detail}.pdf"
    }
}

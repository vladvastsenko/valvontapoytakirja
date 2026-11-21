package fi.vladi.valvontapoytakirja.pdf

/** A human-readable filename, with only characters forbidden by file systems removed. */
internal object PdfFileNames {
    fun name(language: String, kind: String, site: String): String {
        val prefix = when (kind) {
            "valvonta" -> when (language) {
                "ru" -> "Протокол_контроля_гидроизоляции"
                "en" -> "Waterproofing_inspection"
                "sv" -> "Tätskiktskontroll"
                else -> "Valvontapöytäkirja"
            }
            else -> when (language) {
                "ru" -> "Протокол_осмотра"
                "en" -> "Inspection_report"
                "sv" -> "Besiktningsprotokoll"
                else -> "Tarkastuspöytäkirja"
            }
        }
        val detail = site.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .trim().trim('_', '.', ' ')
            .replace(Regex("\\s+"), "_")
            .take(100).ifBlank { when(language) { "ru" -> "объект"; "en" -> "site"; "sv" -> "objekt"; else -> "kohde" } }
        return "${prefix}_${detail}.pdf"
    }
}

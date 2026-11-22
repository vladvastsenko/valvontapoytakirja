package fi.vladi.valvontapoytakirja.pdf

/**
 * Translates only the descriptive suffix of a catalog product.
 * The original manufacturer/product name remains unchanged in saved reports.
 */
object ProductDisplay {
    private val descriptions = listOf(
        listOf("Pikamärkätilatasoite", "Быстротвердеющая шпаклёвка для влажных помещений", "Quick-setting wet-room plaster", "Snabbhärdande våtrumsspackel"),
        listOf("Märkätilatasoite", "Шпаклёвка для влажных помещений", "Wet-room plaster", "Våtrumsspackel"),
        listOf("Erikoisnopea hienotasoite", "Особо быстротвердеющая финишная шпаклёвка", "Extra-fast finishing compound", "Extra snabbt finspackel"),
        listOf("Erikoisnopea tasoite", "Особо быстротвердеющая выравнивающая смесь", "Extra-fast levelling compound", "Extra snabbt avjämningsbruk"),
        listOf("Kevyttäytteinen seinän oikaisutasoite", "Лёгкая выравнивающая смесь для стен", "Lightweight wall levelling plaster", "Lätt väggutjämningsspackel"),
        listOf("Seinän oikaisutasoite", "Выравнивающая смесь для стен", "Wall levelling plaster", "Väggutjämningsspackel"),
        listOf("Seinän pikatäyttömassa", "Быстротвердеющая смесь для заполнения стен", "Rapid wall filling compound", "Snabb väggfyllnadsmassa"),
        listOf("Lattialämmitystasoite", "Выравнивающая смесь для тёплого пола", "Underfloor heating screed", "Golvvärmeavjämning"),
        listOf("Lattiatasoite", "Выравнивающая смесь для пола", "Floor levelling compound", "Golvavjämningsmassa"),
        listOf("Seinätasoite", "Шпаклёвка для стен", "Wall plaster", "Väggspackel"),
        listOf("Pikalattiamassa", "Быстротвердеющая смесь для пола", "Rapid floor screed", "Snabb golvavjämning"),
        listOf("Lattiamassa", "Смесь для выравнивания пола", "Floor screed", "Golvavjämningsmassa"),
        listOf("Oikaisutasoite", "Выравнивающая смесь", "Levelling compound", "Utjämningsspackel"),
        listOf("Hienotasoite", "Финишная шпаклёвка", "Finishing compound", "Finspackel"),
        listOf("Pikatasoite", "Быстротвердеющая выравнивающая смесь", "Rapid levelling compound", "Snabbavjämning"),
        listOf("Täyttötasoite", "Заполняющая шпаклёвка", "Filling compound", "Fyllnadsspackel"),
        listOf("Täyttömassa", "Заполняющая смесь", "Filling compound", "Fyllnadsmassa"),
        listOf("Pikatäyttömassa", "Быстротвердеющая заполняющая смесь", "Rapid filling compound", "Snabb fyllnadsmassa"),
        listOf("Pikamassa", "Быстротвердеющая смесь", "Rapid compound", "Snabbmassa"),
        listOf("Korjausmassa", "Ремонтная смесь", "Repair compound", "Reparationsmassa"),
        listOf("Viimeistelytasoite", "Финишная шпаклёвка", "Finishing compound", "Finspackel"),
        listOf("Saneerauslaasti", "Клей для ремонтной облицовки", "Renovation tile adhesive", "Renoveringsfix"),
        listOf("Kiinnityslaasti", "Плиточный клей", "Tile adhesive", "Fästmassa"),
        listOf("Kevytlaasti", "Облегчённый клеевой раствор", "Lightweight adhesive mortar", "Lättfix"),
        listOf("Nopea kevytlaasti", "Быстротвердеющий облегчённый клей", "Rapid lightweight adhesive", "Snabbt lättfix"),
        listOf("Superjoustava kiinnityslaasti", "Высокоэластичный плиточный клей", "Highly flexible tile adhesive", "Mycket flexibelt fästmassa"),
        listOf("Saumalaasti", "Затирочная смесь", "Tile grout", "Fogmassa"),
        listOf("Epoksisaumalaasti", "Эпоксидная затирка", "Epoxy grout", "Epoxifog"),
        listOf("Lattiasaumalaasti", "Затирка для пола", "Floor tile grout", "Golvfog"),
        listOf("Epoksisaumausaine", "Эпоксидная затирка", "Epoxy grout", "Epoxifog"),
        listOf("Märkätilasilikoni", "Силикон для влажных помещений", "Wet-room silicone", "Våtrumssilikon"),
        listOf("Luonnonkivisilikoni", "Силикон для натурального камня", "Natural stone silicone", "Naturstenssilikon"),
        listOf("Saniteettisilikoni", "Санитарный силикон", "Sanitary silicone", "Sanitetssilikon"),
        listOf("Vedeneristysmassa", "Гидроизоляционная мастика", "Waterproofing membrane", "Tätskiktsmassa"),
        listOf("Eristyslaasti", "Гидроизоляционный раствор", "Waterproofing mortar", "Tätskiktsbruk"),
        listOf("Tartuntapohjuste", "Адгезионная грунтовка", "Bonding primer", "Vidhäftningsprimer"),
        listOf("Kaivolaippa", "Манжета трапа", "Drain flange", "Brunnsmanschett"),
        listOf("Lattiakaivovahvike", "Усиление трапа", "Floor drain reinforcement", "Golvbrunnsförstärkning"),
        listOf("Nurkkavahvikenauha", "Армирующая угловая лента", "Corner reinforcement tape", "Hörnarmeringsband"),
        listOf("Läpivientivahvike", "Манжета проходки", "Pipe penetration reinforcement", "Rörgenomföringsförstärkning"),
        listOf("Läpivientikappale", "Манжета проходки", "Pipe penetration collar", "Rörmanschett"),
        listOf("Vahvistusnauha", "Армирующая лента", "Reinforcing tape", "Armeringsband"),
        listOf("Vedeneristysnauha", "Гидроизоляционная лента", "Waterproofing tape", "Tätskiktsband"),
        listOf("Sisäkulma", "Внутренний угол", "Inside corner", "Innerhörn"),
        listOf("Ulkokulma", "Наружный угол", "Outside corner", "Ytterhörn"),
        listOf("Nauha", "Лента", "Tape", "Band"),
        listOf("Dispersio", "Дисперсионная грунтовка", "Dispersion primer", "Dispersionsprimer"),
        listOf("Kosteussulku", "Влагозащитная грунтовка", "Moisture barrier", "Fuktspärr"),
        listOf("Epoksikiinnitys", "Эпоксидный клей", "Epoxy adhesive", "Epoxilim")
    ).sortedByDescending { it[0].length }

    fun name(original: String, language: String): String {
        val index = when (language) { "ru" -> 1; "en" -> 2; "sv" -> 3; else -> return original }
        for (entry in descriptions) {
            val suffix = entry[0]
            if (original.length > suffix.length &&
                original.endsWith(suffix, ignoreCase = true) &&
                original[original.length - suffix.length - 1].isWhitespace()
            ) {
                return original.dropLast(suffix.length) + entry[index]
            }
        }
        return original
    }
}

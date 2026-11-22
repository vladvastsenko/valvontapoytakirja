package fi.vladi.valvontapoytakirja.pdf

/**
 * Translates only the descriptive suffix of a catalog product.
 * The original manufacturer/product name remains unchanged in saved reports.
 */
object ProductDisplay {
    private val descriptions = listOf(
        listOf("Pikamärkätilatasoite", "Быстротвердеющая шпаклёвка для влажных помещений", "Quick-setting wet-room plaster", "Snabbhärdande våtrumsspackel", "Kiirkivistuv märgruumipahtel", "Greitai kietėjantis drėgnų patalpų glaistas", "Ātri cietējoša mitro telpu špaktele"),
        listOf("Märkätilatasoite", "Шпаклёвка для влажных помещений", "Wet-room plaster", "Våtrumsspackel", "Märgruumipahtel", "Drėgnų patalpų glaistas", "Mitro telpu špaktele"),
        listOf("Erikoisnopea hienotasoite", "Особо быстротвердеющая финишная шпаклёвка", "Extra-fast finishing compound", "Extra snabbt finspackel", "Eriti kiiresti kivistuv viimistluspahtel", "Ypač greitai kietėjantis apdailos glaistas", "Īpaši ātri cietējoša apdares špaktele"),
        listOf("Erikoisnopea tasoite", "Особо быстротвердеющая выравнивающая смесь", "Extra-fast levelling compound", "Extra snabbt avjämningsbruk", "Eriti kiiresti kivistuv tasandussegu", "Ypač greitai kietėjantis išlyginamasis mišinys", "Īpaši ātri cietējošs izlīdzinošais maisījums"),
        listOf("Kevyttäytteinen seinän oikaisutasoite", "Лёгкая выравнивающая смесь для стен", "Lightweight wall levelling plaster", "Lätt väggutjämningsspackel"),
        listOf("Seinän oikaisutasoite", "Выравнивающая смесь для стен", "Wall levelling plaster", "Väggutjämningsspackel", "Seina tasandussegu", "Sienų išlyginamasis mišinys", "Sienu izlīdzināšanas maisījums"),
        listOf("Seinän pikatäyttömassa", "Быстротвердеющая смесь для заполнения стен", "Rapid wall filling compound", "Snabb väggfyllnadsmassa"),
        listOf("Lattialämmitystasoite", "Выравнивающая смесь для тёплого пола", "Underfloor heating screed", "Golvvärmeavjämning", "Põrandakütte tasandussegu", "Grindinio šildymo išlyginamasis mišinys", "Apsildāmās grīdas izlīdzinošais maisījums"),
        listOf("Lattiatasoite", "Выравнивающая смесь для пола", "Floor levelling compound", "Golvavjämningsmassa", "Põranda tasandussegu", "Grindų išlyginamasis mišinys", "Grīdas izlīdzinošais maisījums"),
        listOf("Seinätasoite", "Шпаклёвка для стен", "Wall plaster", "Väggspackel"),
        listOf("Pikalattiamassa", "Быстротвердеющая смесь для пола", "Rapid floor screed", "Snabb golvavjämning"),
        listOf("Lattiamassa", "Смесь для выравнивания пола", "Floor screed", "Golvavjämningsmassa"),
        listOf("Oikaisutasoite", "Выравнивающая смесь", "Levelling compound", "Utjämningsspackel", "Tasandussegu", "Išlyginamasis mišinys", "Izlīdzinošais maisījums"),
        listOf("Hienotasoite", "Финишная шпаклёвка", "Finishing compound", "Finspackel", "Viimistluspahtel", "Apdailos glaistas", "Apdares špaktele"),
        listOf("Pikatasoite", "Быстротвердеющая выравнивающая смесь", "Rapid levelling compound", "Snabbavjämning"),
        listOf("Täyttötasoite", "Заполняющая шпаклёвка", "Filling compound", "Fyllnadsspackel", "Täitepahtel", "Užpildymo glaistas", "Pildījuma špaktele"),
        listOf("Täyttömassa", "Заполняющая смесь", "Filling compound", "Fyllnadsmassa", "Täitesegu", "Užpildymo mišinys", "Pildījuma maisījums"),
        listOf("Pikatäyttömassa", "Быстротвердеющая заполняющая смесь", "Rapid filling compound", "Snabb fyllnadsmassa"),
        listOf("Pikamassa", "Быстротвердеющая смесь", "Rapid compound", "Snabbmassa", "Kiirkivistuv segu", "Greitai kietėjantis mišinys", "Ātri cietējošs maisījums"),
        listOf("Korjausmassa", "Ремонтная смесь", "Repair compound", "Reparationsmassa", "Parandussegu", "Remontinis mišinys", "Remonta maisījums"),
        listOf("Viimeistelytasoite", "Финишная шпаклёвка", "Finishing compound", "Finspackel"),
        listOf("Saneerauslaasti", "Клей для ремонтной облицовки", "Renovation tile adhesive", "Renoveringsfix", "Renoveerimisplaadiliim", "Renovaciniai plytelių klijai", "Renovācijas flīžu līme"),
        listOf("Kiinnityslaasti", "Плиточный клей", "Tile adhesive", "Fästmassa", "Plaadiliim", "Plytelių klijai", "Flīžu līme"),
        listOf("Kevytlaasti", "Облегчённый клеевой раствор", "Lightweight adhesive mortar", "Lättfix", "Kergliimsegu", "Lengvas klijų mišinys", "Vieglā līmjava"),
        listOf("Nopea kevytlaasti", "Быстротвердеющий облегчённый клей", "Rapid lightweight adhesive", "Snabbt lättfix"),
        listOf("Superjoustava kiinnityslaasti", "Высокоэластичный плиточный клей", "Highly flexible tile adhesive", "Mycket flexibelt fästmassa"),
        listOf("Saumalaasti", "Затирочная смесь", "Tile grout", "Fogmassa", "Vuugisegu", "Siūlių glaistas", "Šuvju java"),
        listOf("Epoksisaumalaasti", "Эпоксидная затирка", "Epoxy grout", "Epoxifog", "Epoksüvuugisegu", "Epoksidinis siūlių glaistas", "Epoksīda šuvju java"),
        listOf("Lattiasaumalaasti", "Затирка для пола", "Floor tile grout", "Golvfog"),
        listOf("Epoksisaumausaine", "Эпоксидная затирка", "Epoxy grout", "Epoxifog"),
        listOf("Märkätilasilikoni", "Силикон для влажных помещений", "Wet-room silicone", "Våtrumssilikon", "Märgruumisilikoon", "Drėgnų patalpų silikonas", "Mitro telpu silikons"),
        listOf("Luonnonkivisilikoni", "Силикон для натурального камня", "Natural stone silicone", "Naturstenssilikon"),
        listOf("Saniteettisilikoni", "Санитарный силикон", "Sanitary silicone", "Sanitetssilikon"),
        listOf("Vedeneristysmassa", "Гидроизоляционная мастика", "Waterproofing membrane", "Tätskiktsmassa", "Hüdroisolatsioonimass", "Hidroizoliacinė masė", "Hidroizolācijas masa"),
        listOf("Eristyslaasti", "Гидроизоляционный раствор", "Waterproofing mortar", "Tätskiktsbruk", "Hüdroisolatsioonisegu", "Hidroizoliacinis mišinys", "Hidroizolācijas java"),
        listOf("Tartuntapohjuste", "Адгезионная грунтовка", "Bonding primer", "Vidhäftningsprimer"),
        listOf("Kaivolaippa", "Манжета трапа", "Drain flange", "Brunnsmanschett", "Trapi mansett", "Trapo manžetė", "Trapa manšete"),
        listOf("Lattiakaivovahvike", "Усиление трапа", "Floor drain reinforcement", "Golvbrunnsförstärkning"),
        listOf("Nurkkavahvikenauha", "Армирующая угловая лента", "Corner reinforcement tape", "Hörnarmeringsband", "Nurga tugevduslint", "Kampų armavimo juosta", "Stūra armēšanas lente"),
        listOf("Läpivientivahvike", "Манжета проходки", "Pipe penetration reinforcement", "Rörgenomföringsförstärkning", "Läbiviigu mansett", "Vamzdžių įvadų manžetė", "Cauruļu izvadu manšete"),
        listOf("Läpivientikappale", "Манжета проходки", "Pipe penetration collar", "Rörmanschett", "Läbiviigu mansett", "Įvado manžetė", "Izvada manšete"),
        listOf("Vahvistusnauha", "Армирующая лента", "Reinforcing tape", "Armeringsband", "Tugevduslint", "Armavimo juosta", "Armēšanas lente"),
        listOf("Vedeneristysnauha", "Гидроизоляционная лента", "Waterproofing tape", "Tätskiktsband", "Hüdroisolatsioonilint", "Hidroizoliacinė juosta", "Hidroizolācijas lente"),
        listOf("Sisäkulma", "Внутренний угол", "Inside corner", "Innerhörn", "Sisenurk", "Vidinis kampas", "Iekšējais stūris"),
        listOf("Ulkokulma", "Наружный угол", "Outside corner", "Ytterhörn", "Välisnurk", "Išorinis kampas", "Ārējais stūris"),
        listOf("Nauha", "Лента", "Tape", "Band", "Lint", "Juosta", "Lente"),
        listOf("Dispersio", "Дисперсионная грунтовка", "Dispersion primer", "Dispersionsprimer", "Dispersioonkrunt", "Dispersinis gruntas", "Dispersijas grunts"),
        listOf("Kosteussulku", "Влагозащитная грунтовка", "Moisture barrier", "Fuktspärr", "Niiskustõke", "Drėgmės barjeras", "Mitruma barjera"),
        listOf("Epoksikiinnitys", "Эпоксидный клей", "Epoxy adhesive", "Epoxilim")
    ).sortedByDescending { it[0].length }

    fun name(original: String, language: String): String {
        val index = when (language) { "ru" -> 1; "en" -> 2; "sv" -> 3; "et" -> 4; "lt" -> 5; "lv" -> 6; else -> return original }
        for (entry in descriptions) {
            val suffix = entry[0]
            if (original.length > suffix.length &&
                original.endsWith(suffix, ignoreCase = true) &&
                original[original.length - suffix.length - 1].isWhitespace()
            ) {
                return original.dropLast(suffix.length) + entry.getOrElse(index) { suffix }
            }
        }
        return original
    }
}

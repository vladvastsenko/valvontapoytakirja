package fi.vladi.valvontapoytakirja.pdf

/**
 * Translates only the descriptive suffix of a catalog product.
 * The original manufacturer/product name remains unchanged in saved reports.
 */
object ProductDisplay {
    private val descriptions = listOf(
        listOf("Pikamärkätilatasoite", "Быстротвердеющая шпаклёвка для влажных помещений", "Quick-setting wet-room plaster", "Snabbhärdande våtrumsspackel", "Kiirkivistuv märgruumipahtel", "Greitai kietėjantis drėgnų patalpų glaistas", "Ātri cietējoša mitro telpu špaktele", "Hurtigherdende våtromssparkel", "Hurtighærdende vådrumsspartel"),
        listOf("Märkätilatasoite", "Шпаклёвка для влажных помещений", "Wet-room plaster", "Våtrumsspackel", "Märgruumipahtel", "Drėgnų patalpų glaistas", "Mitro telpu špaktele", "Våtromssparkel", "Vådrumsspartel"),
        listOf("Erikoisnopea hienotasoite", "Особо быстротвердеющая финишная шпаклёвка", "Extra-fast finishing compound", "Extra snabbt finspackel", "Eriti kiiresti kivistuv viimistluspahtel", "Ypač greitai kietėjantis apdailos glaistas", "Īpaši ātri cietējoša apdares špaktele", "Ekstra hurtigherdende finsparkel", "Ekstra hurtighærdende finspartel"),
        listOf("Erikoisnopea tasoite", "Особо быстротвердеющая выравнивающая смесь", "Extra-fast levelling compound", "Extra snabbt avjämningsbruk", "Eriti kiiresti kivistuv tasandussegu", "Ypač greitai kietėjantis išlyginamasis mišinys", "Īpaši ātri cietējošs izlīdzinošais maisījums", "Ekstra hurtigherdende avrettingsmasse", "Ekstra hurtighærdende afretningsmasse"),
        listOf("Kevyttäytteinen seinän oikaisutasoite", "Лёгкая выравнивающая смесь для стен", "Lightweight wall levelling plaster", "Lätt väggutjämningsspackel", "Lett veggavrettingsmasse", "Let vægafretningsmasse", ),
        listOf("Seinän oikaisutasoite", "Выравнивающая смесь для стен", "Wall levelling plaster", "Väggutjämningsspackel", "Seina tasandussegu", "Sienų išlyginamasis mišinys", "Sienu izlīdzināšanas maisījums", "Veggavrettingsmasse", "Vægafretningsmasse"),
        listOf("Seinän pikatäyttömassa", "Быстротвердеющая смесь для заполнения стен", "Rapid wall filling compound", "Snabb väggfyllnadsmassa", "Hurtigherdende veggfyllmasse", "Hurtighærdende vægfyldmasse", ),
        listOf("Lattialämmitystasoite", "Выравнивающая смесь для тёплого пола", "Underfloor heating screed", "Golvvärmeavjämning", "Põrandakütte tasandussegu", "Grindinio šildymo išlyginamasis mišinys", "Apsildāmās grīdas izlīdzinošais maisījums", "Avrettingsmasse for gulvvarme", "Afretning til gulvvarme"),
        listOf("Lattiatasoite", "Выравнивающая смесь для пола", "Floor levelling compound", "Golvavjämningsmassa", "Põranda tasandussegu", "Grindų išlyginamasis mišinys", "Grīdas izlīdzinošais maisījums", "Gulvavrettingsmasse", "Gulvafretningsmasse"),
        listOf("Seinätasoite", "Шпаклёвка для стен", "Wall plaster", "Väggspackel", "Seinapahtel", "Sienų glaistas", "Sienu špaktele", "Veggsparkel", "Vægspartel"),
        listOf("Pikalattiamassa", "Быстротвердеющая смесь для пола", "Rapid floor screed", "Snabb golvavjämning", "Kiirkivistuv põrandasegu", "Greitai kietėjantis grindų mišinys", "Ātri cietējošs grīdas maisījums", "Hurtigherdende gulvmasse", "Hurtighærdende gulvmasse"),
        listOf("Lattiamassa", "Смесь для выравнивания пола", "Floor screed", "Golvavjämningsmassa", "Põrandasegu", "Grindų mišinys", "Grīdas maisījums", "Gulvmasse", "Gulvmasse"),
        listOf("Oikaisutasoite", "Выравнивающая смесь", "Levelling compound", "Utjämningsspackel", "Tasandussegu", "Išlyginamasis mišinys", "Izlīdzinošais maisījums", "Avrettingsmasse", "Afretning"),
        listOf("Hienotasoite", "Финишная шпаклёвка", "Finishing compound", "Finspackel", "Viimistluspahtel", "Apdailos glaistas", "Apdares špaktele", "Finsparkel", "Finspartel"),
        listOf("Pikatasoite", "Быстротвердеющая выравнивающая смесь", "Rapid levelling compound", "Snabbavjämning", "Kiirkivistuv tasandussegu", "Greitai kietėjantis išlyginamasis mišinys", "Ātri cietējošs izlīdzinošais maisījums", "Hurtigherdende avrettingsmasse", "Hurtighærdende afretningsmasse"),
        listOf("Täyttötasoite", "Заполняющая шпаклёвка", "Filling compound", "Fyllnadsspackel", "Täitepahtel", "Užpildymo glaistas", "Pildījuma špaktele", "Fyllsparkel", "Fyldspartel"),
        listOf("Täyttömassa", "Заполняющая смесь", "Filling compound", "Fyllnadsmassa", "Täitesegu", "Užpildymo mišinys", "Pildījuma maisījums", "Fyllmasse", "Fyldmasse"),
        listOf("Pikatäyttömassa", "Быстротвердеющая заполняющая смесь", "Rapid filling compound", "Snabb fyllnadsmassa", "Kiirkivistuv täitesegu", "Greitai kietėjantis užpildymo mišinys", "Ātri cietējošs pildījuma maisījums", "Hurtigherdende fyllmasse", "Hurtighærdende fyldmasse"),
        listOf("Pikamassa", "Быстротвердеющая смесь", "Rapid compound", "Snabbmassa", "Kiirkivistuv segu", "Greitai kietėjantis mišinys", "Ātri cietējošs maisījums", "Hurtigherdende masse", "Hurtighærdende masse"),
        listOf("Korjausmassa", "Ремонтная смесь", "Repair compound", "Reparationsmassa", "Parandussegu", "Remontinis mišinys", "Remonta maisījums", "Reparasjonsmasse", "Reparationsmasse"),
        listOf("Viimeistelytasoite", "Финишная шпаклёвка", "Finishing compound", "Finspackel", "Viimistluspahtel", "Baigiamasis glaistas", "Apdares špaktele", "Finsparkel", "Finspartel"),
        listOf("Saneerauslaasti", "Клей для ремонтной облицовки", "Renovation tile adhesive", "Renoveringsfix", "Renoveerimisplaadiliim", "Renovaciniai plytelių klijai", "Renovācijas flīžu līme", "Flislim for renovering", "Fliseklæb til renovering"),
        listOf("Kiinnityslaasti", "Плиточный клей", "Tile adhesive", "Fästmassa", "Plaadiliim", "Plytelių klijai", "Flīžu līme", "Flislim", "Fliseklæb"),
        listOf("Kevytlaasti", "Облегчённый клеевой раствор", "Lightweight adhesive mortar", "Lättfix", "Kergliimsegu", "Lengvas klijų mišinys", "Vieglā līmjava", "Lettmørtel", "Letmørtel"),
        listOf("Nopea kevytlaasti", "Быстротвердеющий облегчённый клей", "Rapid lightweight adhesive", "Snabbt lättfix", "Kiirkivistuv kergliimsegu", "Greitai kietėjantys lengvi klijai", "Ātri cietējoša vieglā līmjava", "Hurtigherdende lettmørtel", "Hurtighærdende letmørtel"),
        listOf("Superjoustava kiinnityslaasti", "Высокоэластичный плиточный клей", "Highly flexible tile adhesive", "Mycket flexibelt fästmassa", "Väga elastne plaadiliim", "Ypač elastingi plytelių klijai", "Īpaši elastīga flīžu līme", "Svært fleksibelt flislim", "Meget fleksibel fliseklæb"),
        listOf("Saumalaasti", "Затирочная смесь", "Tile grout", "Fogmassa", "Vuugisegu", "Siūlių glaistas", "Šuvju java", "Fugemasse", "Fugemørtel"),
        listOf("Epoksisaumalaasti", "Эпоксидная затирка", "Epoxy grout", "Epoxifog", "Epoksüvuugisegu", "Epoksidinis siūlių glaistas", "Epoksīda šuvju java", "Epoksyfugemasse", "Epoxyfugemørtel"),
        listOf("Lattiasaumalaasti", "Затирка для пола", "Floor tile grout", "Golvfog", "Põrandaplaatide vuugisegu", "Grindų plytelių siūlių glaistas", "Grīdas flīžu šuvju java", "Fugemasse for gulvfliser", "Fugemørtel til gulvfliser"),
        listOf("Epoksisaumausaine", "Эпоксидная затирка", "Epoxy grout", "Epoxifog", "Epoksüvuugisegu", "Epoksidinis siūlių glaistas", "Epoksīda šuvju java", "Epoksyfugemasse", "Epoxyfugemasse"),
        listOf("Märkätilasilikoni", "Силикон для влажных помещений", "Wet-room silicone", "Våtrumssilikon", "Märgruumisilikoon", "Drėgnų patalpų silikonas", "Mitro telpu silikons", "Våtromssilikon", "Vådrumssilikone"),
        listOf("Luonnonkivisilikoni", "Силикон для натурального камня", "Natural stone silicone", "Naturstenssilikon", "Looduskivisilikoon", "Natūralaus akmens silikonas", "Dabīgā akmens silikons", "Natursteinsilikon", "Naturstenssilikone"),
        listOf("Saniteettisilikoni", "Санитарный силикон", "Sanitary silicone", "Sanitetssilikon", "Sanitaarsilikoon", "Sanitarinis silikonas", "Sanitārais silikons", "Sanitærsilikon", "Sanitetssilikone"),
        listOf("Vedeneristysmassa", "Гидроизоляционная мастика", "Waterproofing membrane", "Tätskiktsmassa", "Hüdroisolatsioonimass", "Hidroizoliacinė masė", "Hidroizolācijas masa", "Membranmasse", "Membranmasse"),
        listOf("Eristyslaasti", "Гидроизоляционный раствор", "Waterproofing mortar", "Tätskiktsbruk", "Hüdroisolatsioonisegu", "Hidroizoliacinis mišinys", "Hidroizolācijas java", "Tettemørtel", "Tætningsmørtel"),
        listOf("Tartuntapohjuste", "Адгезионная грунтовка", "Bonding primer", "Vidhäftningsprimer", "Nakkekrunt", "Sukibimą gerinantis gruntas", "Saķeres grunts", "Heftprimer", "Hæftegrunder"),
        listOf("Kaivolaippa", "Манжета трапа", "Drain flange", "Brunnsmanschett", "Trapi mansett", "Trapo manžetė", "Trapa manšete", "Slukmansjett", "Afløbsmanchet"),
        listOf("Lattiakaivovahvike", "Усиление трапа", "Floor drain reinforcement", "Golvbrunnsförstärkning", "Trapi tugevdus", "Trapo sutvirtinimas", "Trapa stiprinājums", "Slukforsterkning", "Afløbsforstærkning"),
        listOf("Nurkkavahvikenauha", "Армирующая угловая лента", "Corner reinforcement tape", "Hörnarmeringsband", "Nurga tugevduslint", "Kampų armavimo juosta", "Stūra armēšanas lente", "Hjørnearmeringsbånd", "Hjørnearmeringsbånd"),
        listOf("Läpivientivahvike", "Манжета проходки", "Pipe penetration reinforcement", "Rörgenomföringsförstärkning", "Läbiviigu mansett", "Vamzdžių įvadų manžetė", "Cauruļu izvadu manšete", "Rørmansjett", "Rørmanchet"),
        listOf("Läpivientikappale", "Манжета проходки", "Pipe penetration collar", "Rörmanschett", "Läbiviigu mansett", "Įvado manžetė", "Izvada manšete", "Gjennomføringsmansjett", "Gennemføringsmanchet"),
        listOf("Vahvistusnauha", "Армирующая лента", "Reinforcing tape", "Armeringsband", "Tugevduslint", "Armavimo juosta", "Armēšanas lente", "Armeringsbånd", "Armeringsbånd"),
        listOf("Vedeneristysnauha", "Гидроизоляционная лента", "Waterproofing tape", "Tätskiktsband", "Hüdroisolatsioonilint", "Hidroizoliacinė juosta", "Hidroizolācijas lente", "Membranbånd", "Membranbånd"),
        listOf("Sisäkulma", "Внутренний угол", "Inside corner", "Innerhörn", "Sisenurk", "Vidinis kampas", "Iekšējais stūris", "Innvendig hjørne", "Indvendigt hjørne"),
        listOf("Ulkokulma", "Наружный угол", "Outside corner", "Ytterhörn", "Välisnurk", "Išorinis kampas", "Ārējais stūris", "Utvendig hjørne", "Udvendigt hjørne"),
        listOf("Nauha", "Лента", "Tape", "Band", "Lint", "Juosta", "Lente", "Bånd", "Bånd"),
        listOf("Dispersio", "Дисперсионная грунтовка", "Dispersion primer", "Dispersionsprimer", "Dispersioonkrunt", "Dispersinis gruntas", "Dispersijas grunts", "Dispersjonsprimer", "Dispersionsprimer"),
        listOf("Kosteussulku", "Влагозащитная грунтовка", "Moisture barrier", "Fuktspärr", "Niiskustõke", "Drėgmės barjeras", "Mitruma barjera", "Fuktsperre", "Fugtspærre"),
        listOf("Epoksikiinnitys", "Эпоксидный клей", "Epoxy adhesive", "Epoxilim", "Epoksüliim", "Epoksidiniai klijai", "Epoksīda līme", "Epoksylim", "Epoxyklæb")
    ).sortedByDescending { it[0].length }

    fun name(original: String, language: String): String {
        val index = when (language) { "ru" -> 1; "en" -> 2; "sv" -> 3; "et" -> 4; "lt" -> 5; "lv" -> 6; "nb" -> 7; "da" -> 8; else -> return original }
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

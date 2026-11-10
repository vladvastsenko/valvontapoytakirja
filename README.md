# Valvontapöytäkirja – Android

Android-sovellus märkätilojen vedeneristyksen tarkastusraporttien laatimiseen.

## Ominaisuudet

- Natiivi Android-sovellus: Kotlin + Jetpack Compose.
- Raportin tiedot ja liitetyt kuvat tallennetaan puhelimen omaan tallennustilaan.
- Valokuvia voi ottaa kameralla tai valita useita kuvia galleriasta.
- Kuville voi lisätä vapaaehtoisen kommentin.
- PDF:n erillisillä kuvasivuilla enintään 9 kuvaa sivua kohden; pienempi kuvamäärä kasvattaa kuvien kokoa.
- PDF:ssä ei tulosteta kuvanumeroita, kuvaruutuja eikä valokuvasivun otsikkoa.
- Tarkastajan allekirjoituksen voi piirtää sormella. PDF:ään allekirjoitus viedään läpinäkyvänä, korkearesoluutioisena kuvana.
- Ensimmäisen sivun yläosaan voi lisätä oman logon.
- Viimeiselle PDF-sivulle voi lisätä itse muokattavan alatunnisteen.
- Materiaalikatalogissa on Kiilto-, weber-, SCHÖNOX-, ARDEX- ja Mapei-tuotteita työvaiheittain.
- Tuotteen valmistajan ja nimen voi myös syöttää käsin.
- Eri valmistajien järjestelmäosia ei tule yhdistää ilman valmistajan hyväksyntää.
- Lattiakaivon alueen erilliset kalvonpaksuuskentät on poistettu lomakkeesta ja PDF:stä.

## Rakentaminen

Tarvitaan JDK 17, Android SDK ja Gradle 8.11.1.

```bash
gradle assembleDebug
```

APK syntyy polkuun:

```
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions rakentaa debug-APK:n ja julkaisee sen workflow-artifactina.

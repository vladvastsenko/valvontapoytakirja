# Valvontapöytäkirja – Android

Android-sovellus märkätilojen vedeneristyksen tarkastusraporttien laatimiseen.

## Ominaisuudet

- Natiivi Android-sovellus: Kotlin + Jetpack Compose.
- Raportin tiedot tallennetaan automaattisesti puhelimen omaan tallennustilaan.
- Valokuvia voi ottaa kameralla tai valita useita kuvia galleriasta.
- Valokuville voi lisätä vapaaehtoisen kommentin.
- PDF:n valokuvasivuille tulostetaan enintään 9 kuvaa sivua kohden. Tyhjästä kommentista ei tulosteta tekstiriviä.
- Tarkastajan allekirjoituksen voi piirtää sormella. PDF:ään allekirjoitus viedään läpinäkyvänä, korkearesoluutioisena kuvana.
- Materiaalivalinnoissa on Kiilto-tuotekatalogi työvaiheittain sekä mahdollisuus syöttää oma tuote ja valmistaja.
- Materiaalikatalogi on erillinen, joten muiden valmistajien tuotteita voidaan lisätä myöhemmin.
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

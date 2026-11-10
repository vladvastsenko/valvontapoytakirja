# Valvontapöytäkirja – Android

Android-sovellus märkätilojen vedeneristyksen tarkastusraporttien laatimiseen.

## Ensimmäinen versio

- Natiivi Android-sovellus: Kotlin + Jetpack Compose.
- Raportin tiedot tallennetaan automaattisesti puhelimen omaan tallennustilaan.
- Valokuvia voi ottaa kameralla tai valita useita kuvia galleriasta.
- Valokuville voi lisätä selitteet ja kuvat sisällytetään PDF-raporttiin.
- PDF-raportin voi jakaa Androidin jakotoiminnolla.
- Materiaalirivit sisältävät valmistajan, tuotteen ja lisätiedot. Valmistaja ei ole rajattu Kiiltoon.
- Mukana ovat alkuperäisen valvontapöytäkirjan kentät sekä erilliset kentät työolosuhteille ja lattiakaivon alueen kalvonpaksuudelle.

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

## Huomioita

Tämä on ensimmäinen toiminnальный MVP. PDF-ulkoasun viimeistely, lomakevalidointi, materiaaliluetteloiden hallinta ja allekirjoitukset voidaan kehittää seuraavina vaiheina.

# DIT Tools (Android)

App Android con i tool di [ditworld.com/tools](https://ditworld.com/tools/).
Schermata principale con un bottone per ciascuno dei 7 strumenti; toccandolo si apre il tool a schermo intero
(header/footer del sito nascosti, barra superiore nativa con tasto indietro).

Tool: Camera specs & data rates · Firmware tracker · Data calculator · Lens coverage · Frame lines · Exposure planner · Upload time.

I tool sono caricati dal sito: serve connessione internet e si aggiornano da soli.

## Build

```bash
./gradlew assembleRelease   # APK in app/build/outputs/apk/release/
```

Richiede JDK 17 e Android SDK 34. Su GitHub l'APK viene prodotto dall'Action "Build APK" (Artifacts).
L'APK release e' firmato con la chiave debug: va bene per installazione diretta, non per il Play Store.

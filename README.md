# DIT Tools (Android)

App Android con i tool di [ditworld.com/tools](https://ditworld.com/tools/).
Schermata principale con un bottone per ciascuno dei 7 strumenti; toccandolo si apre il tool a schermo intero
(header/footer del sito nascosti, barra superiore nativa con tasto indietro).

Tool: Camera specs & data rates · Firmware tracker · Data calculator · Lens coverage · Frame lines · Exposure planner · Upload time.

## Offline

Tutti i tool funzionano senza connessione: pagine, script, font e immagini sono incorporati nell'app
(`app/src/main/assets/site/`, ~3 MB). Con internet l'app prende la pagina aggiornata dal sito; senza rete (o se il
caricamento fallisce) usa la copia incorporata. I link esterni (download firmware, "En Español", ecc.) richiedono internet.

Per aggiornare la copia incorporata ai dati piu' recenti del sito (es. nuovi firmware):

```bash
python3 tools/mirror.py
```

## Build

```bash
./gradlew assembleRelease   # APK in app/build/outputs/apk/release/
```

Richiede JDK 17 e Android SDK 34. Su GitHub l'APK viene prodotto dall'Action "Build APK" (Artifacts).
L'APK release e' firmato con la chiave debug: va bene per installazione diretta, non per il Play Store.

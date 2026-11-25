# MyCloset (Android)

Armadio digitale + stilista personale, tutto sul telefono. Funzioni ispirate ad Acloset, senza limite di capi e senza account.

## Funzioni
- **Armadio**: foto da fotocamera/galleria, **rimozione sfondo** automatica, riconoscimento di categoria e colore, importazione multipla (fino a 20 foto), ricerca e filtri (categoria, colore, marca, preferiti, mai indossati), ordinamento.
- **Scheda capo**: nome, categoria, colore, marca, prezzo, data d'acquisto, stagioni, occasioni, materiale, fantasia, note; volte indossato, **costo per utilizzo**, ultima volta.
- **Outfit**: tela libera (sposta, ingrandisci, ruota i capi), suggerimento automatico, preferiti, occasioni.
- **Calendario**: cosa hai indossato / pianificato ogni giorno, con anteprime.
- **Statistiche**: valore dell'armadio, costo medio per utilizzo, per categoria/colore/marca, spesa mensile, più indossati, mai indossati.
- **Oggi**: meteo (Open-Meteo) e outfit consigliato in base a temperatura, pioggia, occasione e armonia dei colori.
- **Stilista**: "questo abbinamento va bene?" e "devo comprarlo?" (confronto con ciò che hai già).
- **Viaggi**: valigia con spunte e suggerimento in base ai giorni.
- **Lista desideri**, **backup/ripristino** in un file .zip.

## Cosa NON c'è (richiederebbe un server)
Community/feed social, marketplace dell'usato, chat con stilista AI online, analisi del colore personale.

## Build
```bash
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
./gradlew testDebugUnitTest   # test (Robolectric)
```
JDK 17+, Android SDK 34. L'APK release è firmato con la chiave debug (installazione diretta, non Play Store).

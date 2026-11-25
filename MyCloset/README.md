# MyCloset (Android)

Armadio digitale + stilista personale, tutto sul telefono. Funzioni ispirate ad Acloset, senza limite di capi e senza account.

## Funzioni
- **Armadio**: scatti una foto → in automatico toglie lo sfondo e (con l'AI) riconosce tipo di capo, colore, stagioni e occasioni e **compila la scheda**: tu confermi o correggi. Importazione multipla (fino a 20 foto), ricerca e filtri, ordinamento.
- **Scheda capo**: nome, categoria, colore, marca, prezzo, data d'acquisto, stagioni, occasioni, materiale, fantasia, note; volte indossato, **costo per utilizzo**, ultima volta.
- **Outfit**: tela libera (sposta, ingrandisci, ruota i capi), suggerimento automatico, preferiti, occasioni.
- **Calendario**: cosa hai indossato / pianificato ogni giorno, con anteprime.
- **Statistiche**: valore dell'armadio, costo medio per utilizzo, per categoria/colore/marca, spesa mensile, più indossati, mai indossati.
- **Oggi**: meteo (Open-Meteo) e outfit consigliato in base a temperatura, pioggia, occasione e armonia dei colori.
- **Stilista**: "questo abbinamento va bene?" e "devo comprarlo?" (confronto con ciò che hai già).
- **Viaggi**: valigia con spunte e suggerimento in base ai giorni.
- **Lista desideri**, **backup/ripristino** in un file .zip.

## Stilista AI (opzionale, richiede il server in `server/`)
- **Analisi foto**: dopo la foto, Claude compila la scheda (nome, categoria, colore, stagioni, occasioni, materiale, marca se visibile).
- **Chat con lo stilista**: conosce il tuo armadio e il meteo, propone outfit usando solo capi che hai davvero (si possono indossare o salvare con un tocco).
- **«Devo comprarlo?»**: parere dell'AI con capi simili già posseduti e abbinamenti.
- La chiave Anthropic sta solo sul server (Cloudflare Worker + D1, vedi `server/README.md`); l'app parla col server con un token anonimo. Senza server (o senza rete) l'app funziona comunque: restano riconoscimento e stilista "a regole" sul telefono.
- Indirizzo di serie: `https://mycloset-api.matteocav.workers.dev` (modificabile in Impostazioni → Stilista AI → Verifica).

## Cosa NON c'è
Community/feed social, marketplace dell'usato, analisi del colore personale.

## Build
```bash
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
./gradlew testDebugUnitTest   # 19 test (Robolectric)
cd server && npm test         # 12 test del server
```
JDK 17+, Android SDK 34. L'APK release è firmato con la chiave debug (installazione diretta, non Play Store).

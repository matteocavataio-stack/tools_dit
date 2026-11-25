# Server AI di MyCloset

Un piccolo **Cloudflare Worker** (stesso account e stessa tecnica del server licenze di TeoStackPro) che dà all'app le funzioni AI **senza mettere chiavi dentro l'app**.

| Rotta | Cosa fa |
|---|---|
| `POST /api/register` | crea un'identità anonima per l'installazione (token firmato, nessun account) |
| `POST /api/ai/analyze` | foto di un capo → scheda compilata (nome, categoria, colore, stagioni, occasioni, materiale…) |
| `POST /api/ai/chat` | stilista in chat: consiglia outfit usando **solo** i capi del tuo armadio, meteo e occasione |
| `POST /api/ai/purchase` | «devo comprarlo?»: foto del capo + armadio → verdetto, motivi, capi simili e abbinamenti |
| `GET /api/health` | 200 solo se il database risponde; elenca i provider AI e se sono configurati |

Non salva foto né armadi: l'app li manda a ogni richiesta e il server li gira all'AI. Nel database D1 ci sono solo **contatori d'uso**.

## Provider AI e quando passa all'altro

`AI_PROVIDERS` in `wrangler.toml` è l'elenco **in ordine**: si prova il primo; se la quota finisce o va in errore si passa al successivo. Un provider che ha finito la quota viene saltato per 30 minuti (poi si riprova).

| Provider | Costo | Cosa serve | Note |
|---|---|---|---|
| `workers-ai` (predefinito 1°) | **gratis** fino a 10.000 «neuron» al giorno ([prezzi](https://developers.cloudflare.com/workers-ai/platform/pricing/)); oltre, il piano a pagamento | niente: è già un binding del Worker (`[ai]`) | modello `@cf/google/gemma-4-26b-a4b-it` (legge le foto). Non ho potuto misurare quante foto al giorno ci stanno. |
| `gemini` (predefinito 2°) | **gratis** con limiti per progetto (si vedono in AI Studio) | chiave gratuita da aistudio.google.com/apikey → `GEMINI_API_KEY` | modello `gemini-3.5-flash-lite`. **Con il livello gratuito Google può usare i contenuti per migliorare i suoi prodotti** e persone possono leggerli ([termini](https://ai.google.dev/gemini-api/terms-archive/terms_05_14_24?authuser=1)): vale anche per le tue foto. |
| `anthropic` (non attivo) | **a pagamento** | `ANTHROPIC_API_KEY` | Claude: qualità migliore. Aggiungilo in fondo (`"workers-ai,gemini,anthropic"`) solo se vuoi una riserva a pagamento quando i gratuiti sono esauriti. |

Se un provider non è configurato (es. manca la chiave Gemini) viene semplicemente saltato.
I modelli gratuiti sono più deboli di Claude: possono sbagliare categoria o colore, per questo l'app chiede sempre di confermare.

## Sicurezza e limiti

- Ogni installazione ha un token firmato con `APP_SECRET`: senza token valido il server non chiama l'AI.
- Limiti (in `wrangler.toml`): `DAILY_LIMIT` richieste al giorno per installazione, `GLOBAL_DAILY_LIMIT` in totale, `REGISTER_LIMIT` nuove installazioni al giorno per IP.
- Le chiavi (`GEMINI_API_KEY`, `ANTHROPIC_API_KEY`) stanno solo come *secret* del Worker. **Mai** nel repository né in chat.

## Pubblicazione (una volta sola)

Serve l'account Cloudflare che usi già per `teostack-licenze` e Node.js.

```
cd MyCloset/server
npm install
npx wrangler login
npx wrangler d1 create mycloset-api
```
Copia il `database_id` stampato in `wrangler.toml` al posto di `SOSTITUISCI-CON-L-ID-DI-D1` (**non committare** quel file con l'id vero). Poi:
```
npx wrangler d1 execute mycloset-api --remote --file=schema.sql
openssl rand -base64 32 | npx wrangler secret put APP_SECRET
npx wrangler secret put GEMINI_API_KEY      # facoltativo: la chiave gratuita di Google AI Studio
npx wrangler deploy
```
Il provider Cloudflare non richiede chiavi. L'indirizzo è `https://mycloset-api.matteocav.workers.dev` (quello di serie dell'app).
Controllo: `curl https://mycloset-api.matteocav.workers.dev/api/health` → `{"ok":true,"db":true,"ai":true,"secret":true,"providers":[{"name":"workers-ai","configured":true},{"name":"gemini","configured":true}]}`.

**Aggiornare un server già pubblicato** (dal tuo Mac, tenendo il tuo `database_id`):
```
cd ~/tools_dit && git stash && git pull && git stash pop
cd MyCloset/server && npm install && npx wrangler deploy
```

## Prove in locale

```
npm test                       # 23 prove automatiche (provider e D1 simulati)
```
Per provare il Worker vero in locale con un finto Claude: crea `.dev.vars` con `GEMINI_API_KEY`, `APP_SECRET` e `GEMINI_BASE_URL=http://127.0.0.1:9998` (e togli `[ai]` dalla copia di `wrangler.toml` che usi in locale), poi `npx wrangler d1 execute mycloset-api --local --file=schema.sql` e `npx wrangler dev --local`.

## Backup / manutenzione

Il database contiene solo contatori giornalieri (si puliscono da soli dopo 7 giorni): non serve backup.

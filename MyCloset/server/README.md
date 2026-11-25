# Server AI di MyCloset

Un piccolo **Cloudflare Worker** (stesso account e stessa tecnica del server licenze di TeoStackPro) che permette all'app di usare Claude **senza mettere la chiave dentro l'app**:

| Rotta | Cosa fa |
|---|---|
| `POST /api/register` | crea un'identità anonima per l'installazione (token firmato, nessun account) |
| `POST /api/ai/analyze` | foto di un capo → scheda compilata (nome, categoria, colore, stagioni, occasioni, materiale…) |
| `POST /api/ai/chat` | stilista in chat: consiglia outfit usando **solo** i capi del tuo armadio, meteo e occasione |
| `POST /api/ai/purchase` | «devo comprarlo?»: foto del capo + armadio → verdetto, motivi, capi simili e abbinamenti |
| `GET /api/health` | 200 solo se il database risponde |

Non salva foto né armadi: l'app li manda a ogni richiesta e il server li gira a Claude. Nel database D1 ci sono solo **contatori d'uso** (limiti giornalieri).

## Sicurezza e costi

- La chiave Anthropic (`ANTHROPIC_API_KEY`) sta solo come *secret* del Worker.
- Ogni installazione ha un token firmato con `APP_SECRET`: senza token valido il server non chiama Claude.
- Limiti (in `wrangler.toml`): `DAILY_LIMIT` richieste al giorno per installazione, `GLOBAL_DAILY_LIMIT` in totale, `REGISTER_LIMIT` nuove installazioni al giorno per IP.
- **Imposta anche un limite di spesa mensile** nella console Anthropic (Settings → Limits): è la protezione vera.
- Modelli: `MODEL_VISION` (foto) e `MODEL_CHAT` (stilista) in `wrangler.toml`. Prezzi per milione di token (listino al 3/11/2026, da ricontrollare su anthropic.com/pricing): Opus 5.5 $4 in / $20 out · Sonnet 5.5 $2 / $10 · Haiku 5.5 $0.10 / $0.50. Il predefinito è `claude-opus-5-5` (il più capace); per spendere molto meno puoi mettere `claude-sonnet-5-5` o `claude-haiku-5-5` e rifare `npx wrangler deploy`. Non ho misurato il costo reale per richiesta: guardalo nella console dopo qualche giorno d'uso.
- `USE_FALLBACKS = "1"` attiva il «fallback» lato server di Anthropic (se un modello rifiuta una richiesta, la riprova su un altro). È spento di serie perché non l'ho potuto provare con la chiave vera; i vestiti non dovrebbero attivare rifiuti.

## Pubblicazione (una volta sola)

Serve l'account Cloudflare che usi già per `teostack-licenze`, Node.js e una chiave API Anthropic (console.anthropic.com → API keys).

```
cd MyCloset/server
npm install
npx wrangler login
npx wrangler d1 create mycloset-api
```
Copia il `database_id` stampato in `wrangler.toml` al posto di `SOSTITUISCI-CON-L-ID-DI-D1` (**non committare** quel file con l'id vero, come fai per il server licenze). Poi:
```
npx wrangler d1 execute mycloset-api --remote --file=schema.sql
openssl rand -base64 32          # copia l'output
npx wrangler secret put APP_SECRET           # incolla l'output di sopra
npx wrangler secret put ANTHROPIC_API_KEY    # incolla la chiave Anthropic
npx wrangler deploy
```
L'indirizzo sarà `https://mycloset-api.matteocav.workers.dev` (stesso sottodominio del server licenze): è già quello di serie dell'app. Controllo: `curl https://mycloset-api.matteocav.workers.dev/api/health` deve rispondere `{"ok":true,"db":true,"ai":true,"secret":true}`.

**Mai** scrivere chiavi o password in chat o nel repository.

## Prove in locale

```
npm test                       # 12 prove automatiche (Claude e D1 simulati)
```
Per provare il Worker vero in locale con un finto Claude: crea `.dev.vars` con `ANTHROPIC_API_KEY`, `APP_SECRET` e `ANTHROPIC_BASE_URL=http://127.0.0.1:9999`, poi `npx wrangler d1 execute mycloset-api --local --file=schema.sql` e `npx wrangler dev --local`.

## Backup / manutenzione

Il database contiene solo contatori giornalieri (si puliscono da soli dopo 7 giorni): non serve backup.

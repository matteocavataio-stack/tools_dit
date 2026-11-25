// Worker Cloudflare: server AI di MyCloset.
//   GET  /api/health        stato del servizio (200 solo se il database risponde)
//   POST /api/register      crea l'identita' anonima dell'installazione → { token }
//   POST /api/ai/analyze    foto di un capo → scheda compilata
//   POST /api/ai/chat       stilista in chat (usa l'armadio inviato dall'app)
//   POST /api/ai/purchase   «devo comprarlo?» → verdetto confrontando foto e armadio
// Segreti (Worker secrets): ANTHROPIC_API_KEY e APP_SECRET. Nessun dato dell'utente viene salvato: solo contatori d'uso.
import { AiError, analyzeItem, chat, purchaseCheck } from './ai.js';
import { hashIp, issueToken, verifyToken } from './auth.js';
import { allow, bump, maybeCleanup, today } from './limits.js';
import { configured, providerOrder } from './providers.js';

const JSON_HEADERS = { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' };
const json = (status, body, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...JSON_HEADERS, ...extra } });
const fail = (status, code, message, extra) => json(status, { code, message }, extra);
const log = (event, fields = {}) => console.log(JSON.stringify({ event, ...fields }));

const MAX_BODY = 3_000_000;
const MAX_IMAGE_B64 = 1_800_000;
const MEDIA_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

async function readJSON(request) {
  const text = await request.text();
  if (text.length > MAX_BODY) throw new AiError(413, 'too_large', 'Richiesta troppo grande.');
  try { const v = JSON.parse(text); if (v && typeof v === 'object') return v; } catch { /* sotto */ }
  throw new AiError(400, 'bad_request', 'Richiesta non valida.');
}

const toStr = (v, n) => (typeof v === 'string' ? v.slice(0, n) : '');

function parseImage(body) {
  const data = body.imageBase64, type = body.mediaType;
  if (typeof data !== 'string' || data.length < 100 || data.length > MAX_IMAGE_B64 || !/^[A-Za-z0-9+/=]+$/.test(data))
    throw new AiError(400, 'bad_image', 'Immagine non valida o troppo grande.');
  if (!MEDIA_TYPES.includes(type)) throw new AiError(400, 'bad_image', 'Formato immagine non supportato.');
  return { imageBase64: data, mediaType: type };
}

function parseCloset(body) {
  if (!Array.isArray(body.closet) || body.closet.length === 0) throw new AiError(400, 'empty_closet', 'Armadio vuoto: aggiungi dei capi.');
  return body.closet.slice(0, 400).map((i) => ({
    id: Number.isInteger(i?.id) ? i.id : -1, name: toStr(i?.name, 60), category: toStr(i?.category, 20),
    color: toStr(i?.color, 30), brand: toStr(i?.brand, 40), seasons: toStr(i?.seasons, 40), occasions: toStr(i?.occasions, 60),
    wears: Number.isInteger(i?.wears) ? i.wears : 0,
  })).filter((i) => i.id >= 0);
}

function parseMessages(body) {
  const m = body.messages;
  if (!Array.isArray(m) || m.length === 0 || m.length > 24) throw new AiError(400, 'bad_request', 'Conversazione non valida.');
  const out = m.map((x) => ({ role: x?.role === 'assistant' ? 'assistant' : 'user', content: toStr(x?.content, 2000).trim() }));
  if (out.some((x) => !x.content) || out[out.length - 1].role !== 'user') throw new AiError(400, 'bad_request', 'Conversazione non valida.');
  return out;
}

export async function handle(request, env, deps = {}) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  const now = deps.now ?? Date.now();
  const day = today(now);
  try {
    if (request.method === 'GET' && path === '/api/health') {
      try { await env.DB.prepare('SELECT 1 AS ok').first(); } catch (e) {
        log('health_failed', { error: String(e?.message ?? e) }); return json(503, { ok: false, db: false });
      }
      const cfg = configured(env), order = providerOrder(env);
      return json(200, { ok: true, db: true, ai: order.some((p) => cfg[p]), secret: !!env.APP_SECRET, providers: order.map((p) => ({ name: p, configured: !!cfg[p] })) });
    }

    if (request.method === 'POST' && path === '/api/register') {
      if (!env.APP_SECRET) throw new AiError(500, 'server_config', 'Server non configurato.');
      const ipKey = 'r:' + (await hashIp(request.headers.get('cf-connecting-ip')));
      if (!(await allow(env.DB, ipKey, Number(env.REGISTER_LIMIT || 20), day))) {
        log('register_limited'); return fail(429, 'rate_limited', 'Troppe registrazioni da questa rete: riprova domani.');
      }
      const { token } = await issueToken(env.APP_SECRET);
      return json(200, { token, dailyLimit: Number(env.DAILY_LIMIT || 30) });
    }

    if (request.method === 'POST' && path.startsWith('/api/ai/')) {
      if (!env.APP_SECRET || !providerOrder(env).some((p) => configured(env)[p])) throw new AiError(500, 'server_config', 'Server AI non configurato.');
      const installId = await verifyToken(env.APP_SECRET, request.headers.get('authorization'));
      if (!installId) return fail(401, 'unauthorized', 'Identita\' non valida: riapri l\'app per registrarti di nuovo.');

      const daily = Number(env.DAILY_LIMIT || 30);
      if (!(await allow(env.DB, 'i:' + installId, daily, day)))
        return fail(429, 'daily_limit', `Hai raggiunto il limite di ${daily} richieste AI di oggi: riprova domani.`);
      if (!(await allow(env.DB, 'g:all', Number(env.GLOBAL_DAILY_LIMIT || 300), day))) {
        log('global_limit'); return fail(503, 'busy', 'Il servizio AI ha raggiunto il limite di oggi: riprova domani.');
      }
      maybeCleanup(env.DB, day);

      const body = await readJSON(request);
      const route = path.slice('/api/ai/'.length);
      const done = async (result) => { try { await bump(env.DB, 'p:' + result.provider, day); } catch { /* statistica non critica */ } return json(200, result); };
      if (route === 'analyze') return done(await analyzeItem(deps, env, parseImage(body)));
      if (route === 'chat') {
        return done(await chat(deps, env, {
          messages: parseMessages(body), closet: parseCloset(body), weather: toStr(body.weather, 200),
          today: new Date(now).toISOString().slice(0, 10),
        }));
      }
      if (route === 'purchase') return done(await purchaseCheck(deps, env, { ...parseImage(body), closet: parseCloset(body) }));
    }
    return fail(404, 'not_found', 'Non trovato.');
  } catch (e) {
    if (e instanceof AiError) {
      if (e.status >= 500) log('error', { code: e.code, status: e.status });
      return fail(e.status, e.code, e.message);
    }
    log('server_error', { error: String(e?.message ?? e) });
    return fail(500, 'server_error', 'Errore del server.');
  }
}

export default { fetch: (request, env) => handle(request, env) };

// Catena di provider AI: si prova il primo della lista (AI_PROVIDERS); se finisce la quota o va in errore si passa al successivo.
//   "workers-ai"  Cloudflare Workers AI (binding AI): quota gratuita giornaliera, nessuna chiave
//   "gemini"      Google Gemini API (chiave GEMINI_API_KEY): livello gratuito con limiti per progetto
//   "anthropic"   Claude (ANTHROPIC_API_KEY): a pagamento, SOLO se lo aggiungi tu alla lista
import Anthropic from '@anthropic-ai/sdk';

export class ProviderError extends Error {
  constructor(provider, kind, message) { super(message); this.provider = provider; this.kind = kind; } // kind: quota | unavailable | bad_output | refused | config | error
}

const DEFAULT_ORDER = 'workers-ai,gemini';
export const DEFAULTS = {
  WORKERS_AI_MODEL: '@cf/google/gemma-4-26b-a4b-it',
  GEMINI_MODEL: 'gemini-3.5-flash-lite',
  ANTHROPIC_MODEL: 'claude-opus-5-5',
};

export function providerOrder(env) {
  return String(env.AI_PROVIDERS || DEFAULT_ORDER).split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);
}

export function configured(env) {
  return { 'workers-ai': !!env.AI, gemini: !!env.GEMINI_API_KEY, anthropic: !!env.ANTHROPIC_API_KEY };
}

/** Estrae un oggetto JSON da un testo (anche se il modello lo ha messo in un blocco ```json). */
export function extractJson(text) {
  if (text && typeof text === 'object') return text;
  const t = String(text ?? '').replace(/```(?:json)?/gi, '').trim();
  try { return JSON.parse(t); } catch { /* sotto */ }
  const a = t.indexOf('{'), b = t.lastIndexOf('}');
  if (a >= 0 && b > a) { try { return JSON.parse(t.slice(a, b + 1)); } catch { /* niente */ } }
  return null;
}

const looksLikeQuota = (msg) => /quota|allocation|exhaust|rate.?limit|too many|capacity|429|4006|3040/i.test(msg || '');
const schemaHint = (schema) => '\n\nRispondi SOLO con un oggetto JSON valido (nessun testo prima o dopo, niente markdown) conforme a questo JSON Schema:\n' + JSON.stringify(schema);

// ---------------------------------------------------------------- Workers AI
async function workersAi(env, req) {
  if (!env.AI) throw new ProviderError('workers-ai', 'config', 'Binding AI mancante.');
  const model = env.WORKERS_AI_MODEL || DEFAULTS.WORKERS_AI_MODEL;
  const system = req.system + schemaHint(req.schema);
  const messages = [{ role: 'system', content: system }];
  req.messages.forEach((m, i) => {
    const last = i === req.messages.length - 1;
    if (last && m.role === 'user' && req.image) {
      messages.push({ role: 'user', content: [
        { type: 'image_url', image_url: { url: `data:${req.image.mediaType};base64,${req.image.data}` } },
        { type: 'text', text: m.text },
      ] });
    } else messages.push({ role: m.role, content: m.text });
  });
  const base = { messages, max_completion_tokens: req.maxTokens, temperature: 0.2, chat_template_kwargs: { enable_thinking: false } };
  const run = (extra) => env.AI.run(model, { ...base, ...extra });
  let res;
  try {
    try { res = await run({ response_format: { type: 'json_schema', json_schema: { name: req.schemaName, schema: req.schema } } }); }
    catch (e) {
      // se il modello non accetta lo schema, si riprova col solo «json_object» (lo schema e' gia' nel testo)
      if (looksLikeQuota(String(e?.message))) throw e;
      res = await run({ response_format: { type: 'json_object' } });
    }
  } catch (e) {
    const msg = String(e?.message ?? e);
    throw new ProviderError('workers-ai', looksLikeQuota(msg) ? 'quota' : 'error', msg.slice(0, 200));
  }
  const content = res?.choices?.[0]?.message?.content ?? res?.response ?? null;
  const json = extractJson(content);
  if (!json) throw new ProviderError('workers-ai', 'bad_output', 'Risposta non JSON.');
  return json;
}

// -------------------------------------------------------------------- Gemini
async function gemini(env, req, deps) {
  if (!env.GEMINI_API_KEY) throw new ProviderError('gemini', 'config', 'Chiave GEMINI_API_KEY mancante.');
  const model = env.GEMINI_MODEL || DEFAULTS.GEMINI_MODEL;
  const contents = req.messages.map((m, i) => {
    const parts = [];
    if (i === req.messages.length - 1 && m.role === 'user' && req.image) parts.push({ inlineData: { mimeType: req.image.mediaType, data: req.image.data } });
    parts.push({ text: m.text });
    return { role: m.role === 'assistant' ? 'model' : 'user', parts };
  });
  const body = {
    systemInstruction: { parts: [{ text: req.system }] },
    contents,
    generationConfig: { responseMimeType: 'application/json', responseJsonSchema: req.schema, maxOutputTokens: Math.max(req.maxTokens, 4096), temperature: 0.2 },
  };
  if (env.GEMINI_THINKING_LEVEL) body.generationConfig.thinkingConfig = { thinkingLevel: env.GEMINI_THINKING_LEVEL }; // facoltativo: MINIMAL | LOW | MEDIUM | HIGH
  let r;
  try {
    r = await (deps.fetch ?? fetch)(`${env.GEMINI_BASE_URL || 'https://generativelanguage.googleapis.com'}/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
      method: 'POST', headers: { 'content-type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify(body), signal: AbortSignal.timeout(40_000),
    });
  } catch (e) { throw new ProviderError('gemini', 'unavailable', String(e?.message ?? e).slice(0, 200)); }
  const text = await r.text();
  if (!r.ok) {
    const kind = r.status === 429 ? 'quota' : r.status >= 500 ? 'unavailable' : r.status === 401 || r.status === 403 ? 'config' : 'error';
    throw new ProviderError('gemini', kind, `HTTP ${r.status}: ${text.slice(0, 160)}`);
  }
  let data; try { data = JSON.parse(text); } catch { throw new ProviderError('gemini', 'bad_output', 'Risposta non valida.'); }
  if (data?.promptFeedback?.blockReason) throw new ProviderError('gemini', 'refused', 'Richiesta bloccata: ' + data.promptFeedback.blockReason);
  const cand = data?.candidates?.[0];
  if (cand?.finishReason === 'MAX_TOKENS') throw new ProviderError('gemini', 'bad_output', 'Risposta troncata.');
  const out = (cand?.content?.parts ?? []).filter((p) => !p.thought).map((p) => p.text ?? '').join('');
  const json = extractJson(out);
  if (!json) throw new ProviderError('gemini', 'bad_output', 'Risposta non JSON.');
  return json;
}

// ----------------------------------------------------------------- Anthropic
async function anthropic(env, req, deps) {
  if (!env.ANTHROPIC_API_KEY && !deps.client) throw new ProviderError('anthropic', 'config', 'Chiave ANTHROPIC_API_KEY mancante.');
  const client = deps.client ?? new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, baseURL: env.ANTHROPIC_BASE_URL || undefined });
  const model = (req.heavy ? env.MODEL_CHAT : env.MODEL_VISION) || env.MODEL_VISION || env.MODEL_CHAT || DEFAULTS.ANTHROPIC_MODEL;
  const messages = req.messages.map((m, i) => ({
    role: m.role,
    content: i === req.messages.length - 1 && m.role === 'user' && req.image
      ? [{ type: 'image', source: { type: 'base64', media_type: req.image.mediaType, data: req.image.data } }, { type: 'text', text: m.text }]
      : m.text,
  }));
  const body = {
    model, max_tokens: req.maxTokens, messages,
    system: [{ type: 'text', text: req.system, cache_control: { type: 'ephemeral' } }],
    output_config: { effort: req.effort, format: { type: 'json_schema', schema: req.schema } },
  };
  let res;
  try {
    res = env.USE_FALLBACKS === '1'
      ? await client.beta.messages.create({ ...body, betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default' })
      : await client.messages.create(body);
  } catch (e) {
    const s = e?.status;
    throw new ProviderError('anthropic', s === 429 || s === 529 ? 'quota' : s === 401 || s === 403 ? 'config' : s >= 500 ? 'unavailable' : 'error', String(e?.message ?? e).slice(0, 200));
  }
  if (res.stop_reason === 'refusal') throw new ProviderError('anthropic', 'refused', 'Rifiutato.');
  if (res.stop_reason === 'max_tokens') throw new ProviderError('anthropic', 'bad_output', 'Troncata.');
  const json = extractJson((res.content || []).filter((b) => b.type === 'text').map((b) => b.text).join(''));
  if (!json) throw new ProviderError('anthropic', 'bad_output', 'Risposta non JSON.');
  return json;
}

const RUNNERS = { 'workers-ai': workersAi, gemini, anthropic };

// Dopo un errore di quota/servizio un provider viene saltato per un po' (memoria del Worker; si azzera da sola).
const cooldown = new Map();
export const resetCooldowns = () => cooldown.clear();
const COOLDOWN_MS = { quota: 30 * 60_000, unavailable: 2 * 60_000, config: 10 * 60_000, error: 60_000, bad_output: 0, refused: 0 };

/**
 * Prova i provider in ordine. Ritorna { json, provider } oppure solleva ProviderError con `attempts`.
 * @param essential campi che devono esserci per considerare valida la risposta
 */
export async function runChain(env, req, deps, essential) {
  const now = deps.now ?? Date.now();
  const order = providerOrder(env).filter((p) => RUNNERS[p]);
  const usable = order.filter((p) => configured(env)[p] || (p === 'anthropic' && deps.client));
  const ready = usable.filter((p) => (cooldown.get(p) ?? 0) <= now);
  const attempts = [];
  for (const name of (ready.length ? ready : usable)) {
    try {
      const json = await RUNNERS[name](env, req, deps);
      if (!json || typeof json !== 'object' || essential.some((k) => json[k] === undefined || json[k] === null)) throw new ProviderError(name, 'bad_output', 'Campi mancanti.');
      cooldown.delete(name);
      return { json, provider: name, attempts };
    } catch (e) {
      const pe = e instanceof ProviderError ? e : new ProviderError(name, 'error', String(e?.message ?? e));
      attempts.push({ provider: name, kind: pe.kind });
      console.log(JSON.stringify({ event: 'provider_failed', provider: name, kind: pe.kind, detail: pe.message }));
      if (COOLDOWN_MS[pe.kind]) cooldown.set(name, now + COOLDOWN_MS[pe.kind]);
    }
  }
  const err = new ProviderError('chain', attempts.length ? attempts[attempts.length - 1].kind : 'config', 'Nessun provider AI disponibile.');
  err.attempts = attempts;
  throw err;
}

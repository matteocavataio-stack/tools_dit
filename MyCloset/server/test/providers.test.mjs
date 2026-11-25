import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { handle } from '../src/index.js';
import { extractJson, resetCooldowns } from '../src/providers.js';

function fakeD1() {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  return { prepare: (sql) => ({ bind: (...a) => ({ first: async () => db.prepare(sql).get(...a) ?? null, run: async () => db.prepare(sql).run(...a) }), first: async () => db.prepare(sql).get() ?? null }) };
}
const BIG = 'A'.repeat(400);
const closet = [
  { id: 1, name: 'Maglia', category: 'TOP', color: 'Nero', brand: '', seasons: '', occasions: '', wears: 1 },
  { id: 2, name: 'Jeans', category: 'BOTTOM', color: 'Denim', brand: '', seasons: '', occasions: '', wears: 2 },
];
const ANALYZE = { name: 'Maglia rossa', category: 'TOP', colorHex: '#D32F2F', colorName: 'Rosso', brand: '', seasons: ['SUMMER'], occasions: ['CASUAL'], material: '', pattern: '', notes: '' };

const env = (over = {}) => ({ DB: fakeD1(), APP_SECRET: 'segreto', DAILY_LIMIT: '100', GLOBAL_DAILY_LIMIT: '1000', REGISTER_LIMIT: '50', ...over });
const call = (e, path, body, token, deps) => handle(new Request('https://x.test' + path, { method: 'POST', body: body ? JSON.stringify(body) : undefined,
  headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}), 'cf-connecting-ip': '9.9.9.9' } }), e, deps);
const register = async (e) => (await (await call(e, '/api/register')).json()).token;

const wai = (reply, calls = []) => ({ run: async (model, input) => { calls.push({ model, input }); return typeof reply === 'function' ? reply(model, input) : reply; } });
const wText = (obj) => ({ choices: [{ message: { content: typeof obj === 'string' ? obj : JSON.stringify(obj) } }] });
const gFetch = (reply, calls = []) => async (url, init) => {
  calls.push({ url, init, body: JSON.parse(init.body) });
  const r = typeof reply === 'function' ? reply() : reply;
  return new Response(JSON.stringify(r.body), { status: r.status ?? 200 });
};
const gOk = (obj) => ({ body: { candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify(obj) }] } }] } });
const analyze = (e, t, deps) => call(e, '/api/ai/analyze', { imageBase64: BIG, mediaType: 'image/jpeg' }, t, deps);

beforeEach(() => resetCooldowns());

test('estrae JSON anche da blocchi di codice o testo attorno', () => {
  assert.deepEqual(extractJson('```json\n{"a":1}\n```'), { a: 1 });
  assert.deepEqual(extractJson('Ecco: {"a":{"b":2}} fine'), { a: { b: 2 } });
  assert.equal(extractJson('niente'), null);
});

test('Workers AI: richiesta nel formato giusto (immagine come data URL, JSON schema, senza ragionamento)', async () => {
  const calls = [], e = env({ AI: wai(wText(ANALYZE), calls) }), t = await register(e);
  const r = await analyze(e, t, {});
  const out = await r.json();
  assert.equal(r.status, 200); assert.equal(out.provider, 'workers-ai'); assert.equal(out.name, 'Maglia rossa');
  const { model, input } = calls[0];
  assert.equal(model, '@cf/google/gemma-4-26b-a4b-it');
  assert.equal(input.messages[0].role, 'system'); assert.match(input.messages[0].content, /JSON Schema/);
  const user = input.messages[1].content;
  assert.equal(user[0].type, 'image_url'); assert.match(user[0].image_url.url, /^data:image\/jpeg;base64,AAAA/);
  assert.equal(input.response_format.type, 'json_schema'); assert.equal(input.response_format.json_schema.name, 'analyze');
  assert.equal(input.chat_template_kwargs.enable_thinking, false);
});

test('Workers AI senza quota → passa a Gemini (formato richiesta Gemini corretto)', async () => {
  const wcalls = [], gcalls = [];
  const e = env({ AI: wai(() => { throw new Error('4006: you have used up your daily free allocation of 10,000 neurons'); }, wcalls), GEMINI_API_KEY: 'g-key' });
  const t = await register(e);
  const r = await analyze(e, t, { fetch: gFetch(gOk(ANALYZE), gcalls) });
  const out = await r.json();
  assert.equal(r.status, 200); assert.equal(out.provider, 'gemini');
  const { url, init, body } = gcalls[0];
  assert.equal(url, 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent');
  assert.equal(init.headers['x-goog-api-key'], 'g-key');
  assert.match(body.systemInstruction.parts[0].text, /catalogatore/);
  assert.equal(body.contents[0].role, 'user');
  assert.equal(body.contents[0].parts[0].inlineData.mimeType, 'image/jpeg'); assert.equal(body.contents[0].parts[1].text, 'Compila la scheda di questo capo.');
  assert.equal(body.generationConfig.responseMimeType, 'application/json'); assert.equal(body.generationConfig.responseJsonSchema.type, 'object');
  assert.ok(body.generationConfig.maxOutputTokens >= 4096);
});

test('dopo un errore di quota il provider viene saltato per un po\'', async () => {
  const wcalls = [];
  const e = env({ AI: wai(() => { throw new Error('daily free allocation exceeded'); }, wcalls), GEMINI_API_KEY: 'k' }), t = await register(e);
  const deps = { fetch: gFetch(gOk(ANALYZE)) };
  await analyze(e, t, deps); await analyze(e, t, deps); await analyze(e, t, deps);
  assert.equal(wcalls.length, 1, 'Workers AI non viene richiamato finche\' e\' in pausa');
  // passato il tempo di pausa, si riprova
  await analyze(e, t, { ...deps, now: Date.now() + 31 * 60_000 });
  assert.equal(wcalls.length, 2);
});

test('entrambi gratuiti falliti: errore chiaro, e Claude NON viene usato se non e\' in lista', async () => {
  const claude = { messages: { create: async () => { throw new Error('non deve essere chiamato'); } } };
  const e = env({ AI: wai(() => { throw new Error('quota exhausted'); }), GEMINI_API_KEY: 'k', ANTHROPIC_API_KEY: 'sk' }), t = await register(e);
  const r = await analyze(e, t, { client: claude, fetch: gFetch({ status: 429, body: { error: { status: 'RESOURCE_EXHAUSTED' } } }) });
  assert.equal(r.status, 503); assert.equal((await r.json()).code, 'busy');
});

test('con anthropic in coda alla lista: usato solo come ultima riserva', async () => {
  const ccalls = [];
  const claude = { messages: { create: async (req) => { ccalls.push(req); return { stop_reason: 'end_turn', content: [{ type: 'text', text: JSON.stringify(ANALYZE) }] }; } } };
  const e = env({ AI_PROVIDERS: 'workers-ai,gemini,anthropic', AI: wai(() => { throw new Error('quota'); }), GEMINI_API_KEY: 'k', ANTHROPIC_API_KEY: 'sk' }), t = await register(e);
  const r = await analyze(e, t, { client: claude, fetch: gFetch({ status: 429, body: {} }) });
  assert.equal((await r.json()).provider, 'anthropic'); assert.equal(ccalls.length, 1);
});

test('risposta non valida o incompleta → si passa al provider successivo', async () => {
  const e = env({ AI: wai(wText('Mi dispiace, non posso aiutarti')), GEMINI_API_KEY: 'k' }), t = await register(e);
  const r1 = await analyze(e, t, { fetch: gFetch(gOk(ANALYZE)) });
  assert.equal((await r1.json()).provider, 'gemini');
  resetCooldowns();
  const e2 = env({ AI: wai(wText({ category: 'TOP' })), GEMINI_API_KEY: 'k' }), t2 = await register(e2); // manca "name"
  assert.equal((await (await analyze(e2, t2, { fetch: gFetch(gOk(ANALYZE)) })).json()).provider, 'gemini');
});

test('Workers AI: JSON dentro un blocco ```json e schema rifiutato → riprova con json_object', async () => {
  const calls = [];
  const ai = wai((model, input) => { if (input.response_format.type === 'json_schema') throw new Error('response_format not supported'); return wText('```json\n' + JSON.stringify(ANALYZE) + '\n```'); }, calls);
  const e = env({ AI: ai }), t = await register(e);
  const out = await (await analyze(e, t, {})).json();
  assert.equal(out.provider, 'workers-ai'); assert.equal(calls.length, 2); assert.equal(calls[1].input.response_format.type, 'json_object');
});

test('Gemini: risposta troncata o bloccata → errore gestito', async () => {
  const e = env({ AI_PROVIDERS: 'gemini', GEMINI_API_KEY: 'k' }), t = await register(e);
  const cut = await analyze(e, t, { fetch: gFetch({ body: { candidates: [{ finishReason: 'MAX_TOKENS', content: { parts: [{ text: '{"na' }] } }] } }) });
  assert.equal(cut.status, 502); resetCooldowns();
  const blocked = await analyze(e, t, { fetch: gFetch({ body: { promptFeedback: { blockReason: 'SAFETY' } } }) });
  assert.equal(blocked.status, 422);
});

test('chat e acquisto passano per la stessa catena (testo + cronologia)', async () => {
  const calls = [];
  const e = env({ AI: wai(wText({ reply: 'Metti jeans e maglia.', outfits: [{ title: 'Casual', itemIds: [1, 2, 99], why: 'ok' }] }), calls) }), t = await register(e);
  const r = await call(e, '/api/ai/chat', { messages: [{ role: 'user', content: 'Ciao' }, { role: 'assistant', content: 'Ciao!' }, { role: 'user', content: 'Cosa metto?' }], closet, weather: '10°C' }, t, {});
  const out = await r.json();
  assert.equal(out.provider, 'workers-ai'); assert.deepEqual(out.outfits[0].itemIds, [1, 2]);
  const msgs = calls[0].input.messages;
  assert.deepEqual(msgs.map((m) => m.role), ['system', 'user', 'assistant', 'user']); assert.match(msgs[0].content, /#1 \| Maglia/);
  assert.equal(typeof msgs[3].content, 'string'); // niente immagine nella chat
  const p = await call(e, '/api/ai/purchase', { imageBase64: BIG, mediaType: 'image/png', closet }, t, {});
  assert.equal(p.status, 502, 'risposta senza verdetto → non valida'); // la risposta finta della chat non ha "verdict"
});

test('health elenca i provider e quali sono configurati', async () => {
  const e = env({ AI: wai({}), GEMINI_API_KEY: '' });
  const r = await handle(new Request('https://x.test/api/health'), e);
  const j = await r.json();
  assert.equal(j.ai, true);
  assert.deepEqual(j.providers, [{ name: 'workers-ai', configured: true }, { name: 'gemini', configured: false }]);
});

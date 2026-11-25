// Identita' anonima di un'installazione: «<uuid>.<firma>». La firma HMAC-SHA256 si fa con APP_SECRET (segreto del Worker),
// quindi nessuno puo' inventarsi identita' valide; il server non conserva nulla per verificarle.
const enc = new TextEncoder();

const b64u = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const unb64u = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));

async function hmacKey(secret, usage) {
  return crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, [usage]);
}

export async function issueToken(secret) {
  const id = crypto.randomUUID();
  const sig = await crypto.subtle.sign('HMAC', await hmacKey(secret, 'sign'), enc.encode(id));
  return { installId: id, token: `${id}.${b64u(sig)}` };
}

/** Ritorna l'id installazione se il token e' valido, altrimenti null. La verifica e' a tempo costante. */
export async function verifyToken(secret, header) {
  const m = /^Bearer ([0-9a-f-]{36})\.([A-Za-z0-9_-]{20,64})$/.exec(header || '');
  if (!m || !secret) return null;
  try {
    const ok = await crypto.subtle.verify('HMAC', await hmacKey(secret, 'verify'), unb64u(m[2]), enc.encode(m[1]));
    return ok ? m[1] : null;
  } catch { return null; }
}

export async function hashIp(ip) {
  const d = await crypto.subtle.digest('SHA-256', enc.encode('mycloset-ip:' + (ip || 'sconosciuto')));
  return b64u(d).slice(0, 22);
}

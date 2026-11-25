// Limiti giornalieri con contatori in D1. Incrementa e ritorna il nuovo valore in un'unica istruzione atomica.
export const today = (now = Date.now()) => new Date(now).toISOString().slice(0, 10);

export async function bump(db, key, day) {
  const row = await db.prepare(
    'INSERT INTO usage (key, day, count) VALUES (?, ?, 1) ON CONFLICT(key, day) DO UPDATE SET count = count + 1 RETURNING count'
  ).bind(key, day).first();
  return row ? row.count : 1;
}

/** Vero se la richiesta e' consentita. Se il database non risponde NON si blocca l'utente (ma il tetto globale non si applica). */
export async function allow(db, key, limit, day) {
  try { return (await bump(db, key, day)) <= limit; } catch { return true; }
}

/** Pulizia dei contatori vecchi (si fa raramente, a caso, per non pesare su ogni richiesta). */
export async function maybeCleanup(db, day) {
  if (Math.random() > 0.01) return;
  try { await db.prepare('DELETE FROM usage WHERE day < date(?, \'-7 days\')').bind(day).run(); } catch { /* non critico */ }
}

-- Contatori d'uso (limiti giornalieri). Una riga per chiave e giorno (UTC).
CREATE TABLE IF NOT EXISTS usage (
  key   TEXT NOT NULL,      -- "i:<installazione>", "g:all" oppure "r:<hash IP>"
  day   TEXT NOT NULL,      -- AAAA-MM-GG
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (key, day)
);

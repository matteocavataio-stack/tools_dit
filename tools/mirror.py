#!/usr/bin/env python3
"""Scarica le pagine dei tool di ditworld.com e tutte le risorse locali
(css, js, font, immagini) in app/src/main/assets/site/, per l'uso offline.
Uso: python3 tools/mirror.py"""
import re, sys, os, urllib.request, urllib.parse, shutil

BASE = "https://ditworld.com"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "site")
PAGES = ["/tools/", "/tools/cameras/", "/tools/firmware/", "/tools/data-calculator/",
         "/tools/lens-coverage/", "/tools/frame-lines/", "/tools/exposure-planner/",
         "/tools/upload-time/"]
EXT = r"(?:css|js|mjs|woff2?|ttf|otf|png|jpe?g|webp|avif|svg|gif|ico|json|webmanifest)"
# percorsi root-relativi: "/_astro/x.js", url(/fonts/a.woff2), '/images/a.png'
REF = re.compile(r"""["'(=\s](/(?!/)[A-Za-z0-9_@.\-/%]+\.""" + EXT + r""")(?=["')\s?#;,]|$)""")
REL = re.compile(r"""url\(\s*["']?(?!data:|https?:|/)([A-Za-z0-9_@.\-/%]+\.""" + EXT + r""")""")

def get(path):
    req = urllib.request.Request(BASE + urllib.parse.quote(path, safe="/%@"),
                                 headers={"User-Agent": "dit-tools-mirror"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def dest(path):
    return os.path.join(OUT, path.lstrip("/") + ("index.html" if path.endswith("/") else ""))

def save(path, data):
    d = dest(path)
    os.makedirs(os.path.dirname(d), exist_ok=True)
    open(d, "wb").write(data)

if os.path.isdir(OUT): shutil.rmtree(OUT)
seen, queue = set(), list(PAGES)
while queue:
    p = queue.pop()
    if p in seen: continue
    seen.add(p)
    try: data = get(p)
    except Exception as e:
        print("SKIP", p, e); continue
    save(p, data)
    if re.search(r"\.(?:css|js|mjs|json|webmanifest)$|/$", p):
        txt = data.decode("utf-8", "ignore")
        refs = set(REF.findall(txt))
        if p.endswith(".css"):
            base = os.path.dirname(p) + "/"
            refs |= {os.path.normpath(base + r) for r in REL.findall(txt)}
        if p.endswith((".js", ".mjs")):
            base = os.path.dirname(p) + "/"
            refs |= {os.path.normpath(base + r) for r in re.findall(r"""["'`](\.{1,2}/[A-Za-z0-9_@.\-/]+\.""" + EXT + r""")["'`]""", txt)}
        for r in refs:
            if r not in seen and not r.startswith("/cdn-cgi/"): queue.append(r)
print(len(seen), "file scaricati")

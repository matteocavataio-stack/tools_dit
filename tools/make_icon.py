#!/usr/bin/env python3
"""Genera l'icona dell'app (foreground adattivo) dal logo sorgente.
Uso: python3 tools/make_icon.py <logo.jpg>"""
import sys
from PIL import Image, ImageDraw
src = Image.open(sys.argv[1]).convert("RGB")
# riquadro del logo nell'immagine sorgente (coordinate misurate sul file 2000x1116)
L, T, R, B, RAD = 680 + 36, 150 + 34, 680 + 608, 150 + 611, 112
SS = 4  # supersampling per bordi lisci
w, h = R - L, B - T
tile = src.crop((L, T, R, B)).resize((w * SS, h * SS), Image.LANCZOS).convert("RGBA")
mask = Image.new("L", tile.size, 0)
ImageDraw.Draw(mask).rounded_rectangle((SS, SS, w * SS - SS, h * SS - SS), RAD * SS, fill=255)
tile.putalpha(mask)
tile = tile.resize((w, h), Image.LANCZOS)
SIZE = 432                      # layer adattivo 108dp @ xxxhdpi
side = int(SIZE * 64 / 108)     # il riquadro occupa ~59% (zona sicura)
tile = tile.resize((side, side), Image.LANCZOS)
out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
out.paste(tile, ((SIZE - side) // 2, (SIZE - side) // 2), tile)
out.save("app/src/main/res/drawable-nodpi/ic_launcher_foreground.png")

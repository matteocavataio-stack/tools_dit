package com.mycloset.app.logic

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.min

object Colors {
    /** Colori selezionabili nell'editor: nome italiano + esadecimale. */
    val palette: List<Pair<String, String>> = listOf(
        "Nero" to "#111111", "Bianco" to "#F5F5F5", "Grigio" to "#9E9E9E", "Beige" to "#D8C3A5",
        "Marrone" to "#7B4B2A", "Rosso" to "#D32F2F", "Bordeaux" to "#7A1F2B", "Rosa" to "#F48FB1",
        "Arancione" to "#F57C00", "Giallo" to "#FBC02D", "Verde" to "#388E3C", "Verde oliva" to "#6B7B3A",
        "Turchese" to "#00ACC1", "Azzurro" to "#64B5F6", "Blu" to "#1976D2", "Blu navy" to "#14213D",
        "Viola" to "#7B1FA2", "Lilla" to "#B39DDB", "Denim" to "#4A6FA5", "Oro" to "#C9A227",
    )

    fun parse(hex: String): Int = try { Color.parseColor(hex) } catch (_: Exception) { Color.GRAY }

    fun toHex(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)

    private fun hsl(argb: Int): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }

    /** Nome italiano approssimato di un colore. */
    fun name(hex: String): String {
        val (h, s, l) = hsl(parse(hex)).let { Triple(it[0], it[1], it[2]) }
        if (l < 0.12f) return "Nero"
        if (l > 0.92f && s < 0.35f) return "Bianco"
        if (s < 0.12f) return if (l > 0.7f) "Grigio chiaro" else if (l < 0.3f) "Antracite" else "Grigio"
        val light = l > 0.72f
        val dark = l < 0.28f
        return when {
            h < 12 || h >= 345 -> if (dark) "Bordeaux" else if (light) "Rosa" else "Rosso"
            h < 22 -> if (dark || s < 0.5f) "Marrone" else "Arancione"
            h < 40 -> if (s < 0.45f && l > 0.6f) "Beige" else if (dark || l < 0.4f) "Marrone" else "Arancione"
            h < 65 -> if (s < 0.4f && l > 0.6f) "Beige" else if (dark) "Verde oliva" else "Giallo"
            h < 90 -> if (dark) "Verde oliva" else "Verde lime"
            h < 160 -> if (dark) "Verde bosco" else "Verde"
            h < 195 -> "Turchese"
            h < 215 -> if (light) "Azzurro" else "Celeste"
            h < 245 -> if (dark) "Blu navy" else if (light) "Azzurro" else "Blu"
            h < 275 -> if (light) "Lilla" else "Viola"
            h < 300 -> "Viola"
            else -> if (light) "Rosa" else "Fucsia"
        }
    }

    /** Neutri: si abbinano a tutto. */
    fun isNeutral(hex: String): Boolean {
        val c = hsl(parse(hex))
        val l = c[2]
        val s = c[1]
        if (l < 0.18f || l > 0.9f || s < 0.14f) return true
        val n = name(hex)
        return n == "Beige" || n == "Blu navy" || n == "Denim" || n == "Marrone"
    }

    /** Distanza percettiva semplice tra due colori (0..~441). */
    fun distance(a: String, b: String): Double {
        val x = parse(a); val y = parse(b)
        val dr = (Color.red(x) - Color.red(y)).toDouble()
        val dg = (Color.green(x) - Color.green(y)).toDouble()
        val db = (Color.blue(x) - Color.blue(y)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    /** Armonia 0..1 tra due colori (neutri, analoghi, complementari...). */
    fun harmony(a: String, b: String): Double {
        val na = isNeutral(a); val nb = isNeutral(b)
        if (na && nb) return 0.85
        if (na || nb) return 0.9
        val ha = hsl(parse(a)); val hb = hsl(parse(b))
        var d = abs(ha[0] - hb[0]); if (d > 180) d = 360 - d
        val base = when {
            d < 15 -> 0.65          // monocromatico
            d < 40 -> 0.85          // analoghi
            d in 150f..210f -> 0.8  // complementari
            d in 105f..135f -> 0.65 // triade
            else -> 0.3
        }
        // due colori molto saturi insieme "urlano"
        val loud = min(ha[1], hb[1])
        return (base - if (loud > 0.75f && d > 40) 0.15 else 0.0).coerceIn(0.0, 1.0)
    }

    /** Colore dominante di una foto di capo (ignora trasparenza/sfondo). */
    fun dominant(bmp: Bitmap): String {
        val w = bmp.width; val h = bmp.height
        val step = maxOf(1, maxOf(w, h) / 120)
        val hasAlpha = bmp.hasAlpha()
        var transparentSeen = false
        val buckets = HashMap<Int, IntArray>() // key -> [count, r, g, b]
        fun scan(x0: Int, y0: Int, x1: Int, y1: Int, requireOpaque: Boolean) {
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    val p = bmp.getPixel(x, y)
                    val a = Color.alpha(p)
                    if (a < 200) { transparentSeen = true } else {
                        val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                        val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
                        val e = buckets.getOrPut(key) { IntArray(4) }
                        e[0]++; e[1] += r; e[2] += g; e[3] += b
                    }
                    x += step
                }
                y += step
            }
        }
        if (hasAlpha) scan(0, 0, w, h, true)
        if (!hasAlpha || !transparentSeen || buckets.isEmpty()) {
            buckets.clear()
            scan(w / 4, h / 4, w * 3 / 4, h * 3 / 4, false) // centro: lontano dallo sfondo
        }
        if (buckets.isEmpty()) return "#808080"
        val best = buckets.values.maxByOrNull { e ->
            val n = e[0]
            val hs = FloatArray(3)
            ColorUtils.RGBToHSL(e[1] / n, e[2] / n, e[3] / n, hs)
            n * (1.0 + hs[1] * 0.4) // leggero favore ai colori saturi
        }!!
        return toHex(Color.rgb(best[1] / best[0], best[2] / best[0], best[3] / best[0]))
    }
}

package com.mycloset.app.logic

import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Occasion
import com.mycloset.app.data.Season
import com.mycloset.app.data.csv
import kotlin.random.Random

data class Suggestion(val items: List<Item>, val note: String, val score: Int)

data class MatchReport(val score: Int, val verdict: String, val tips: List<String>)

data class PurchaseReport(
    val verdict: String,
    val similar: List<Item>,
    val pairsWith: List<Item>,
    val tips: List<String>,
)

/** Stilista "on-device": regole + armonia dei colori, nessun server. */
object Stylist {

    fun seasonsForTemp(t: Double): Set<Season> = when {
        t < 10 -> setOf(Season.WINTER)
        t < 17 -> setOf(Season.AUTUMN, Season.SPRING)
        t < 25 -> setOf(Season.SPRING, Season.SUMMER, Season.AUTUMN)
        else -> setOf(Season.SUMMER)
    }

    fun seasonOfDate(month: Int): Season = when (month) {
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    private fun suits(item: Item, seasons: Set<Season>?, occasion: Occasion?): Boolean {
        if (seasons != null) {
            val s = item.seasons.csv()
            if (s.isNotEmpty() && seasons.none { it.name in s }) return false
        }
        if (occasion != null) {
            val o = item.occasions.csv()
            if (o.isNotEmpty() && occasion.name !in o) return false
        }
        return true
    }

    private fun pairScore(items: List<Item>): Double {
        if (items.size < 2) return 0.7
        var sum = 0.0; var n = 0
        for (i in items.indices) for (j in i + 1 until items.size) {
            sum += Colors.harmony(items[i].colorHex, items[j].colorHex); n++
        }
        return sum / n
    }

    /**
     * Genera un outfit dai capi posseduti.
     * @param tempC temperatura percepita (null = sconosciuta)
     * @param recentlyWorn id dei capi indossati di recente (penalizzati: spinge a "riscoprire" l'armadio)
     */
    fun generate(
        all: List<Item>,
        tempC: Double?,
        rainy: Boolean,
        occasion: Occasion?,
        recentlyWorn: Set<Long>,
        seed: Long,
        mustInclude: Item? = null,
    ): Suggestion? {
        val owned = all.filter { it.status == "OWNED" }
        val seasons = tempC?.let { seasonsForTemp(it) }
        fun pool(c: Category) = owned.filter { Category.of(it.category) == c && suits(it, seasons, occasion) }
        val tops = pool(Category.TOP); val bottoms = pool(Category.BOTTOM)
        val dresses = pool(Category.DRESS); val outer = pool(Category.OUTERWEAR)
        val shoes = pool(Category.SHOES); val bags = pool(Category.BAGS)
        val needOuter = tempC != null && (tempC < 16 || (rainy && tempC < 22))
        val rnd = Random(seed)

        val canBase = (tops.isNotEmpty() && bottoms.isNotEmpty()) || dresses.isNotEmpty()
        if (!canBase) return null

        var best: List<Item>? = null
        var bestScore = -1.0
        repeat(80) {
            val parts = mutableListOf<Item>()
            val useDress = dresses.isNotEmpty() && (tops.isEmpty() || bottoms.isEmpty() || rnd.nextInt(4) == 0)
            if (useDress) parts += dresses.random(rnd)
            else { parts += tops.random(rnd); parts += bottoms.random(rnd) }
            if (needOuter && outer.isNotEmpty()) parts += outer.random(rnd)
            if (shoes.isNotEmpty()) parts += shoes.random(rnd)
            if (bags.isNotEmpty() && rnd.nextInt(3) == 0) parts += bags.random(rnd)
            if (mustInclude != null && parts.none { it.id == mustInclude.id }) {
                val cat = Category.of(mustInclude.category)
                parts.removeAll { Category.of(it.category) == cat }
                parts += mustInclude
            }
            var score = pairScore(parts)
            score += parts.count { it.id !in recentlyWorn } * 0.05
            score += parts.count { it.favorite } * 0.03
            if (score > bestScore) { bestScore = score; best = parts.toList() }
        }
        val items = best ?: return null
        val notes = mutableListOf<String>()
        if (tempC != null) notes += "Con ${tempC.toInt()}°C"
        if (needOuter && items.none { Category.of(it.category) == Category.OUTERWEAR })
            notes += "non hai capispalla adatti nell'armadio: portane uno"
        if (rainy) notes += "possibile pioggia: porta un ombrello"
        val note = notes.joinToString(", ").replaceFirstChar { it.uppercase() }
        return Suggestion(items, note, (pairScore(items) * 100).toInt())
    }

    /** Valuta l'abbinamento tra capi scelti. */
    fun match(items: List<Item>): MatchReport {
        if (items.size < 2) return MatchReport(0, "Scegli almeno due capi.", emptyList())
        val score = (pairScore(items) * 100).toInt()
        val tips = mutableListOf<String>()
        val nonNeutral = items.filter { !Colors.isNeutral(it.colorHex) }
        if (nonNeutral.size >= 3) tips += "Troppi colori vivaci insieme: tieni un solo capo protagonista."
        if (items.map { Category.of(it.category) }.let { it.size != it.toSet().size })
            tips += "Hai due capi della stessa categoria: di solito se ne indossa uno solo."
        val occ = items.map { it.occasions.csv() }.filter { it.isNotEmpty() }
        if (occ.size >= 2 && occ.reduce { a, b -> a intersect b }.isEmpty())
            tips += "Le occasioni d'uso sono diverse (es. sport + elegante)."
        val ssn = items.map { it.seasons.csv() }.filter { it.isNotEmpty() }
        if (ssn.size >= 2 && ssn.reduce { a, b -> a intersect b }.isEmpty())
            tips += "Sono capi di stagioni diverse."
        if (tips.isEmpty() && score >= 75) tips += "I colori si bilanciano bene."
        val verdict = when {
            score >= 80 -> "Ottimo abbinamento"
            score >= 65 -> "Funziona"
            score >= 50 -> "Discreto, si puo' migliorare"
            else -> "Poco armonioso"
        }
        return MatchReport(score, verdict, tips)
    }

    /** "Devo comprarlo?": confronto di un capo candidato con quello che hai gia'. */
    fun purchaseCheck(all: List<Item>, category: Category, colorHex: String): PurchaseReport {
        val owned = all.filter { it.status == "OWNED" }
        val similar = owned.filter {
            Category.of(it.category) == category && Colors.distance(it.colorHex, colorHex) < 90
        }
        val sameCat = owned.count { Category.of(it.category) == category }
        val others = owned.filter { Category.of(it.category) != category && Category.of(it.category) != Category.ACCESSORIES }
        val pairs = others.filter { Colors.harmony(it.colorHex, colorHex) >= 0.8 }
        val tips = mutableListOf<String>()
        if (similar.isNotEmpty()) tips += "Hai gia' ${similar.size} capi molto simili (stessa categoria e colore)."
        if (sameCat >= 15) tips += "Hai gia' $sameCat capi in questa categoria."
        if (pairs.size < 3 && others.isNotEmpty()) tips += "Si abbina solo con ${pairs.size} dei tuoi capi."
        if (pairs.size >= 8) tips += "Si abbina bene con ${pairs.size} capi che hai gia': versatile."
        val verdict = when {
            similar.size >= 2 -> "Meglio evitare: ne hai gia' di simili."
            similar.size == 1 && pairs.size < 5 -> "Probabilmente non serve."
            pairs.size >= 6 -> "Ottimo acquisto: si abbina a molto."
            else -> "Acquisto ragionevole."
        }
        return PurchaseReport(verdict, similar.take(6), pairs.take(8), tips)
    }
}

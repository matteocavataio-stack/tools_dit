package com.mycloset.app.logic

import com.mycloset.app.data.Item
import com.mycloset.app.data.WearLog
import java.time.LocalDate

/** Statistiche calcolate dai capi e dal registro degli outfit indossati (solo date fino a oggi). */
class Stats(val items: List<Item>, logs: List<WearLog>, today: LocalDate = LocalDate.now()) {
    val owned: List<Item> = items.filter { it.status == "OWNED" }
    private val past = logs.filter { it.date <= today.toEpochDay() }
    private val wearsMap: Map<Long, Int> = past.groupBy { it.itemId }.mapValues { (_, v) -> v.map { it.date }.toSet().size }
    private val lastMap: Map<Long, Long> = past.groupBy { it.itemId }.mapValues { (_, v) -> v.maxOf { it.date } }

    fun wears(item: Item) = wearsMap[item.id] ?: 0
    fun lastWorn(item: Item): LocalDate? = lastMap[item.id]?.let { LocalDate.ofEpochDay(it) }

    /** Costo per utilizzo (prezzo / n. volte indossato; mai indossato = prezzo pieno). */
    fun costPerWear(item: Item): Double = if (item.price <= 0) 0.0 else item.price / maxOf(1, wears(item))

    val totalValue get() = owned.sumOf { it.price }
    val totalWears get() = owned.sumOf { wears(it) }
    val neverWorn get() = owned.filter { wears(it) == 0 }
    val mostWorn get() = owned.filter { wears(it) > 0 }.sortedByDescending { wears(it) }
    val bestCpw get() = owned.filter { it.price > 0 && wears(it) > 0 }.sortedBy { costPerWear(it) }
    val worstCpw get() = owned.filter { it.price > 0 }.sortedByDescending { costPerWear(it) }
    val avgCpw: Double get() {
        val w = owned.filter { it.price > 0 && wears(it) > 0 }
        return if (w.isEmpty()) 0.0 else w.sumOf { costPerWear(it) } / w.size
    }

    fun byCategory() = owned.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }
    fun byColor() = owned.groupingBy { Colors.name(it.colorHex) }.eachCount().toList().sortedByDescending { it.second }
    fun byBrand() = owned.filter { it.brand.isNotBlank() }.groupingBy { it.brand.trim() }.eachCount()
        .toList().sortedByDescending { it.second }

    /** Spesa per mese (ultimi 12), da data d'acquisto. */
    fun spendingByMonth(today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Double>> {
        val start = today.withDayOfMonth(1).minusMonths(11)
        val sums = owned.filter { it.purchaseDate != null && it.price > 0 }.groupBy {
            LocalDate.ofEpochDay(it.purchaseDate!!).withDayOfMonth(1)
        }.mapValues { (_, v) -> v.sumOf { it.price } }
        return (0 until 12).map { start.plusMonths(it.toLong()) }.map { it to (sums[it] ?: 0.0) }
    }
}

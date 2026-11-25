package com.mycloset.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Capo dell'armadio (status = OWNED) oppure della lista desideri (status = WISHLIST). */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val imagePath: String = "",
    val category: String = Category.TOP.name,
    val colorHex: String = "#808080",
    val brand: String = "",
    val seasons: String = "",      // CSV di Season.name
    val occasions: String = "",    // CSV di Occasion.name
    val material: String = "",
    val pattern: String = "",
    val price: Double = 0.0,
    val purchaseDate: Long? = null, // epoch day
    val notes: String = "",
    val favorite: Boolean = false,
    val status: String = "OWNED",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "outfits")
data class Outfit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val imagePath: String = "",
    val occasion: String = "",
    val seasons: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Posizione di un capo sulla tela dell'outfit (coordinate relative 0..1 del centro). */
@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfitId", "itemId"],
    foreignKeys = [
        ForeignKey(Outfit::class, ["id"], ["outfitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class OutfitItem(
    val outfitId: Long,
    val itemId: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val z: Int = 0,
)

/** Un capo indossato (o pianificato, se la data e' futura) in un giorno. */
@Entity(
    tableName = "wear_logs",
    foreignKeys = [
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId"), Index("date")],
)
data class WearLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,            // epoch day
    val itemId: Long,
    val outfitId: Long? = null,
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val city: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
)

@Entity(
    tableName = "trip_items",
    primaryKeys = ["tripId", "itemId"],
    foreignKeys = [
        ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class TripItem(
    val tripId: Long,
    val itemId: Long,
    val packed: Boolean = false,
)

enum class Category(val label: String) {
    TOP("Top"), BOTTOM("Pantaloni e gonne"), DRESS("Abiti"), OUTERWEAR("Capispalla"),
    SHOES("Scarpe"), BAGS("Borse"), ACCESSORIES("Accessori"), OTHER("Altro");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class Season(val label: String) {
    SPRING("Primavera"), SUMMER("Estate"), AUTUMN("Autunno"), WINTER("Inverno")
}

enum class Occasion(val label: String) {
    CASUAL("Casual"), WORK("Lavoro"), FORMAL("Elegante"), SPORT("Sport"),
    PARTY("Serata"), HOME("Casa"), TRAVEL("Viaggio")
}

fun String.csv(): Set<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
fun Collection<String>.toCsv(): String = joinToString(",")

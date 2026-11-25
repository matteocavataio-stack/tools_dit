package com.mycloset.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosetDao {
    // ---- Items ----
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun items(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): Item?

    @Insert suspend fun insert(item: Item): Long
    @Update suspend fun update(item: Item)
    @Delete suspend fun delete(item: Item)

    // ---- Outfits ----
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC")
    fun outfits(): Flow<List<Outfit>>

    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun outfit(id: Long): Outfit?

    @Insert suspend fun insert(outfit: Outfit): Long
    @Update suspend fun update(outfit: Outfit)
    @Delete suspend fun delete(outfit: Outfit)

    @Query("SELECT * FROM outfit_items")
    fun allOutfitItems(): Flow<List<OutfitItem>>

    @Query("SELECT * FROM outfit_items WHERE outfitId = :outfitId ORDER BY z")
    suspend fun outfitItems(outfitId: Long): List<OutfitItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitItems(list: List<OutfitItem>)

    @Query("DELETE FROM outfit_items WHERE outfitId = :outfitId")
    suspend fun clearOutfitItems(outfitId: Long)

    // ---- Wear logs ----
    @Query("SELECT * FROM wear_logs")
    fun wearLogs(): Flow<List<WearLog>>

    @Insert suspend fun insert(log: WearLog): Long
    @Insert suspend fun insertWearLogs(list: List<WearLog>)

    @Query("DELETE FROM wear_logs WHERE date = :date AND itemId = :itemId")
    suspend fun deleteWear(date: Long, itemId: Long)

    @Query("DELETE FROM wear_logs WHERE date = :date AND outfitId = :outfitId")
    suspend fun deleteWearOutfit(date: Long, outfitId: Long)

    @Query("DELETE FROM wear_logs WHERE date = :date")
    suspend fun deleteWearDay(date: Long)

    // ---- Trips ----
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun trips(): Flow<List<Trip>>

    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip)
    @Delete suspend fun delete(trip: Trip)

    @Query("SELECT * FROM trip_items")
    fun tripItems(): Flow<List<TripItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tripItem: TripItem)

    @Delete suspend fun delete(tripItem: TripItem)

    // ---- Backup ----
    @Query("SELECT * FROM items") suspend fun itemsOnce(): List<Item>
    @Query("SELECT * FROM outfits") suspend fun outfitsOnce(): List<Outfit>
    @Query("SELECT * FROM outfit_items") suspend fun outfitItemsOnce(): List<OutfitItem>
    @Query("SELECT * FROM wear_logs") suspend fun wearLogsOnce(): List<WearLog>
    @Query("SELECT * FROM trips") suspend fun tripsOnce(): List<Trip>
    @Query("SELECT * FROM trip_items") suspend fun tripItemsOnce(): List<TripItem>

    @Query("DELETE FROM items") suspend fun clearItems()
    @Query("DELETE FROM outfits") suspend fun clearOutfits()
    @Query("DELETE FROM trips") suspend fun clearTrips()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreItems(l: List<Item>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreOutfits(l: List<Outfit>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTrips(l: List<Trip>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTripItems(l: List<TripItem>)
}

@Database(
    entities = [Item::class, Outfit::class, OutfitItem::class, WearLog::class, Trip::class, TripItem::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ClosetDao
}

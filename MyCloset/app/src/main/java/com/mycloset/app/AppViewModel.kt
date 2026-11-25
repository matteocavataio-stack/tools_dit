package com.mycloset.app

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.mycloset.app.data.AppDatabase
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.data.Trip
import com.mycloset.app.data.TripItem
import com.mycloset.app.data.WearLog
import com.mycloset.app.logic.AiClient
import com.mycloset.app.logic.AiException
import com.mycloset.app.logic.AiOutfit
import com.mycloset.app.logic.ImageProc
import com.mycloset.app.logic.Stats
import com.mycloset.app.logic.Prefs
import com.mycloset.app.logic.Weather
import com.mycloset.app.logic.WeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Filtro dell'armadio, condiviso cosi' le statistiche possono "aprire" l'armadio gia' filtrato. */
data class ClosetFilter(
    val query: String = "",
    val category: Category? = null,
    val colorHex: String? = null,
    val brand: String? = null,
    val onlyFavorites: Boolean = false,
    val wishlist: Boolean = false,
    val neverWorn: Boolean = false,
)

/** Messaggio della chat con lo stilista AI. role = "user" | "assistant". */
data class ChatMsg(val role: String, val text: String, val outfits: List<AiOutfit> = emptyList(), val error: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val db = Room.databaseBuilder(app, AppDatabase::class.java, "mycloset.db").build()
    val dao = db.dao()
    val prefs = Prefs(app)

    val items: StateFlow<List<Item>> = dao.items().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val outfits: StateFlow<List<Outfit>> = dao.outfits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val outfitItems: StateFlow<List<OutfitItem>> = dao.allOutfitItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val wearLogs: StateFlow<List<WearLog>> = dao.wearLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trips: StateFlow<List<Trip>> = dao.trips().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tripItems: StateFlow<List<TripItem>> = dao.tripItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filter = MutableStateFlow(ClosetFilter())
    val weather = MutableStateFlow<Weather?>(null)
    val weatherLoading = MutableStateFlow(false)
    val currency = MutableStateFlow(prefs.currency)
    val city = MutableStateFlow(prefs.city)

    // ---- AI (server MyCloset) ----
    val ai = AiClient(prefs)
    val aiEnabled = MutableStateFlow(prefs.aiEnabled)
    val aiUrl = MutableStateFlow(prefs.aiUrl)
    val chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chatBusy = MutableStateFlow(false)

    fun setAi(enabled: Boolean, url: String) {
        prefs.aiEnabled = enabled; prefs.aiUrl = url
        if (url.trim().trimEnd('/') != aiUrl.value.trim().trimEnd('/')) prefs.aiToken = "" // altro server = altra identita'
        aiEnabled.value = enabled; aiUrl.value = prefs.aiUrl
    }

    fun wearsOf(item: Item): Int = Stats(items.value, wearLogs.value).wears(item)

    private fun weatherLine(): String? = weather.value?.let { w ->
        val d = w.days.firstOrNull()
        "${w.city}: ${w.temp.toInt()}°C (percepita ${w.feelsLike.toInt()}°C), ${Weather.describe(w.code)}" +
            (d?.let { ", min ${it.min.toInt()}° max ${it.max.toInt()}°, probabilita' di pioggia ${it.rainPct}%" } ?: "")
    }

    fun sendChat(text: String) {
        val q = text.trim()
        if (q.isEmpty() || chatBusy.value) return
        chat.value = chat.value + ChatMsg("user", q)
        chatBusy.value = true
        viewModelScope.launch {
            val history = chat.value.filter { !it.error }.takeLast(12).map { it.role to it.text }.dropWhile { it.first != "user" }
            try {
                val r = ai.chat(history, items.value, ::wearsOf, weatherLine())
                chat.value = chat.value + ChatMsg("assistant", r.reply, r.outfits)
            } catch (e: AiException) {
                chat.value = chat.value + ChatMsg("assistant", e.message ?: "Errore.", error = true)
            } catch (e: Exception) {
                chat.value = chat.value + ChatMsg("assistant", "Qualcosa e' andato storto: riprova.", error = true)
            }
            chatBusy.value = false
        }
    }

    fun clearChat() { chat.value = emptyList() }

    init { refreshWeather() }

    fun refreshWeather() = viewModelScope.launch {
        if (prefs.lat == null) { weather.value = null; return@launch }
        weatherLoading.value = true
        weather.value = withContext(Dispatchers.IO) { WeatherApi.load(getApplication(), prefs) }
        weatherLoading.value = false
    }

    fun setCity(name: String, lat: Double, lon: Double) {
        prefs.city = name; prefs.lat = lat; prefs.lon = lon; prefs.weatherCache = ""
        city.value = name
        refreshWeather()
    }

    fun setCurrency(c: String) { prefs.currency = c; currency.value = c }

    // ---- Capi ----
    fun saveItem(item: Item, newImage: Bitmap?, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        var it = item
        if (newImage != null) {
            val name = ImageProc.saveItemImage(getApplication(), newImage)
            if (item.imagePath.isNotEmpty()) ImageProc.itemFile(getApplication(), item.imagePath).delete()
            it = it.copy(imagePath = name)
        }
        val id = if (it.id == 0L) dao.insert(it) else { dao.update(it); it.id }
        onDone(id)
    }

    fun deleteItem(item: Item) = viewModelScope.launch {
        dao.delete(item)
        withContext(Dispatchers.IO) { ImageProc.itemFile(getApplication(), item.imagePath).delete() }
    }

    fun toggleFavorite(item: Item) = viewModelScope.launch { dao.update(item.copy(favorite = !item.favorite)) }

    fun markPurchased(item: Item) = viewModelScope.launch {
        dao.update(item.copy(status = "OWNED", purchaseDate = LocalDate.now().toEpochDay()))
    }

    // ---- Indossati / calendario ----
    fun wear(date: LocalDate, itemIds: List<Long>, outfitId: Long? = null) = viewModelScope.launch {
        val day = date.toEpochDay()
        itemIds.forEach { dao.deleteWear(day, it) } // evita doppioni
        dao.insertWearLogs(itemIds.map { WearLog(date = day, itemId = it, outfitId = outfitId) })
    }

    fun unwearOutfit(date: LocalDate, outfitId: Long) = viewModelScope.launch { dao.deleteWearOutfit(date.toEpochDay(), outfitId) }
    fun unwearItem(date: LocalDate, itemId: Long) = viewModelScope.launch { dao.deleteWear(date.toEpochDay(), itemId) }
    fun clearDay(date: LocalDate) = viewModelScope.launch { dao.deleteWearDay(date.toEpochDay()) }

    // ---- Outfit ----
    fun saveOutfit(
        outfit: Outfit, placements: List<Pair<Item, OutfitItem>>, onDone: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        val img = ImageProc.renderOutfit(getApplication(), placements)
        if (outfit.imagePath.isNotEmpty()) ImageProc.outfitFile(getApplication(), outfit.imagePath).delete()
        val saved = outfit.copy(imagePath = img)
        val id = if (saved.id == 0L) dao.insert(saved) else { dao.update(saved); saved.id }
        dao.clearOutfitItems(id)
        dao.insertOutfitItems(placements.map { (_, p) -> p.copy(outfitId = id) })
        onDone(id)
    }

    fun deleteOutfit(o: Outfit) = viewModelScope.launch {
        dao.delete(o)
        withContext(Dispatchers.IO) { ImageProc.outfitFile(getApplication(), o.imagePath).delete() }
    }

    fun toggleFavorite(o: Outfit) = viewModelScope.launch { dao.update(o.copy(favorite = !o.favorite)) }

    // ---- Viaggi ----
    fun saveTrip(t: Trip) = viewModelScope.launch { if (t.id == 0L) dao.insert(t) else dao.update(t) }
    fun deleteTrip(t: Trip) = viewModelScope.launch { dao.delete(t) }
    fun addToTrip(tripId: Long, itemId: Long) = viewModelScope.launch { dao.insert(TripItem(tripId, itemId, false)) }
    fun setPacked(ti: TripItem, packed: Boolean) = viewModelScope.launch { dao.insert(ti.copy(packed = packed)) }
    fun removeFromTrip(ti: TripItem) = viewModelScope.launch { dao.delete(ti) }
}

package com.mycloset.app.logic

import android.content.Context
import android.net.Uri
import com.mycloset.app.data.ClosetDao
import com.mycloset.app.data.Item
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.data.Trip
import com.mycloset.app.data.TripItem
import com.mycloset.app.data.WearLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup completo (dati + foto) in un unico file .zip, ripristinabile su qualsiasi telefono. */
object Backup {
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)

    suspend fun export(context: Context, dao: ClosetDao, uri: Uri): Int = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("items", JSONArray(dao.itemsOnce().map { i -> JSONObject()
            .put("id", i.id).put("name", i.name).put("imagePath", i.imagePath).put("category", i.category)
            .put("colorHex", i.colorHex).put("brand", i.brand).put("seasons", i.seasons).put("occasions", i.occasions)
            .put("material", i.material).put("pattern", i.pattern).put("price", i.price)
            .put("purchaseDate", i.purchaseDate ?: JSONObject.NULL).put("notes", i.notes)
            .put("favorite", i.favorite).put("status", i.status).put("createdAt", i.createdAt) }))
        root.put("outfits", JSONArray(dao.outfitsOnce().map { o -> JSONObject()
            .put("id", o.id).put("name", o.name).put("imagePath", o.imagePath).put("occasion", o.occasion)
            .put("seasons", o.seasons).put("favorite", o.favorite).put("createdAt", o.createdAt) }))
        root.put("outfitItems", JSONArray(dao.outfitItemsOnce().map { o -> JSONObject()
            .put("outfitId", o.outfitId).put("itemId", o.itemId).put("x", o.x.toDouble()).put("y", o.y.toDouble())
            .put("scale", o.scale.toDouble()).put("rotation", o.rotation.toDouble()).put("z", o.z) }))
        root.put("wearLogs", JSONArray(dao.wearLogsOnce().map { w -> JSONObject()
            .put("id", w.id).put("date", w.date).put("itemId", w.itemId).put("outfitId", w.outfitId ?: JSONObject.NULL) }))
        root.put("trips", JSONArray(dao.tripsOnce().map { t -> JSONObject()
            .put("id", t.id).put("name", t.name).put("city", t.city).put("startDate", t.startDate).put("endDate", t.endDate) }))
        root.put("tripItems", JSONArray(dao.tripItemsOnce().map { t -> JSONObject()
            .put("tripId", t.tripId).put("itemId", t.itemId).put("packed", t.packed) }))

        var files = 0
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(root.toString().toByteArray()); zip.closeEntry()
                for (sub in listOf("items", "outfits")) {
                    ImageProc.dir(context, sub).listFiles()?.forEach { f ->
                        zip.putNextEntry(ZipEntry("$sub/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }; zip.closeEntry(); files++
                    }
                }
            }
        } ?: error("Impossibile scrivere il file")
        files
    }

    /** Sostituisce TUTTI i dati attuali con quelli del backup. */
    suspend fun import(context: Context, dao: ClosetDao, uri: Uri) = withContext(Dispatchers.IO) {
        var json: String? = null
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == "data.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith("items/") || name.startsWith("outfits/") -> {
                            val sub = name.substringBefore('/'); val fn = File(name).name // niente path traversal
                            File(ImageProc.dir(context, sub), fn).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    e = zip.nextEntry
                }
            }
        } ?: error("Impossibile leggere il file")
        val root = JSONObject(json ?: error("Backup non valido: manca data.json"))
        fun arr(k: String) = root.optJSONArray(k) ?: JSONArray()
        fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }

        val items = arr("items").map { o -> Item(o.getLong("id"), o.getString("name"), o.getString("imagePath"),
            o.getString("category"), o.getString("colorHex"), o.getString("brand"), o.getString("seasons"),
            o.getString("occasions"), o.getString("material"), o.getString("pattern"), o.getDouble("price"),
            o.optLongOrNull("purchaseDate"), o.getString("notes"), o.getBoolean("favorite"),
            o.getString("status"), o.getLong("createdAt")) }
        val outfits = arr("outfits").map { o -> Outfit(o.getLong("id"), o.getString("name"), o.getString("imagePath"),
            o.getString("occasion"), o.getString("seasons"), o.getBoolean("favorite"), o.getLong("createdAt")) }
        val outfitItems = arr("outfitItems").map { o -> OutfitItem(o.getLong("outfitId"), o.getLong("itemId"),
            o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("scale").toFloat(),
            o.getDouble("rotation").toFloat(), o.getInt("z")) }
        val logs = arr("wearLogs").map { o -> WearLog(o.getLong("id"), o.getLong("date"), o.getLong("itemId"),
            o.optLongOrNull("outfitId")) }
        val trips = arr("trips").map { o -> Trip(o.getLong("id"), o.getString("name"), o.getString("city"),
            o.getLong("startDate"), o.getLong("endDate")) }
        val tripItems = arr("tripItems").map { o -> TripItem(o.getLong("tripId"), o.getLong("itemId"), o.getBoolean("packed")) }

        dao.clearItems(); dao.clearOutfits(); dao.clearTrips()
        dao.restoreItems(items); dao.restoreOutfits(outfits)
        dao.insertOutfitItems(outfitItems); dao.insertWearLogs(logs)
        dao.restoreTrips(trips); dao.restoreTripItems(tripItems)
    }
}

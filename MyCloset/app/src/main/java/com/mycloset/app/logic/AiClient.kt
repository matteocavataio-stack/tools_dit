package com.mycloset.app.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import com.mycloset.app.data.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AiException(val code: String, message: String) : Exception(message)

data class AiAnalysis(
    val provider: String = "",
    val name: String, val category: String, val colorHex: String, val colorName: String, val brand: String,
    val seasons: List<String>, val occasions: List<String>, val material: String, val pattern: String, val notes: String,
)

data class AiOutfit(val title: String, val itemIds: List<Long>, val why: String)

data class AiChatReply(val reply: String, val outfits: List<AiOutfit>)

data class AiPurchase(
    val name: String, val category: String, val colorHex: String, val colorName: String, val verdict: String,
    val summary: String, val reasons: List<String>, val similarIds: List<Long>, val pairsWellIds: List<Long>,
    val outfitIdeas: List<AiOutfit>,
)

/** Client del server AI di MyCloset (vedi /server). Tutte le funzioni sono sospese e vanno chiamate fuori dal thread principale. */
class AiClient(private val prefs: Prefs) {
    val configured: Boolean get() = prefs.aiEnabled && prefs.aiUrl.isNotBlank()

    private fun base() = prefs.aiUrl.trim().trimEnd('/')

    private fun request(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, String> {
        val c = URL(base() + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 90_000 // l'AI puo' impiegare diversi secondi
            c.setRequestProperty("Accept", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally { c.disconnect() }
    }

    private fun failure(code: Int, text: String): AiException {
        val j = runCatching { JSONObject(text) }.getOrNull()
        val msg = j?.optString("message").orEmpty().ifBlank { "Errore del server ($code)." }
        return AiException(j?.optString("code").orEmpty().ifBlank { "http_$code" }, msg)
    }

    /** Stato del server: ok se risponde e ha l'AI configurata. */
    suspend fun health(): String = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/health", null, null)
            if (code != 200) throw failure(code, text)
            val j = JSONObject(text)
            val list = j.optJSONArray("providers")
            val detail = if (list == null) "" else (0 until list.length()).joinToString(", ") {
                val o = list.getJSONObject(it)
                providerLabel(o.optString("name")) + if (o.optBoolean("configured")) " (pronto)" else " (non configurato)"
            }
            if (!j.optBoolean("ai") || !j.optBoolean("secret")) "Server raggiungibile, ma l'AI non e' ancora configurata (mancano segreti o provider)." + if (detail.isNotEmpty()) " $detail." else ""
            else "Server raggiungibile e AI attiva." + if (detail.isNotEmpty()) " Provider in ordine: $detail." else ""
        } catch (e: AiException) { throw e
        } catch (e: Exception) { throw AiException("network", "Server non raggiungibile: controlla l'indirizzo e la connessione.") }
    }

    private fun token(): String {
        prefs.aiToken.takeIf { it.isNotEmpty() }?.let { return it }
        val (code, text) = request("POST", "/api/register", JSONObject(), null)
        if (code != 200) throw failure(code, text)
        return JSONObject(text).getString("token").also { prefs.aiToken = it }
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!configured) throw AiException("disabled", "Stilista AI non attivo: controlla le impostazioni.")
        try {
            var (code, text) = request("POST", path, body, token())
            if (code == 401) { prefs.aiToken = ""; val r = request("POST", path, body, token()); code = r.first; text = r.second }
            if (code != 200) throw failure(code, text)
            JSONObject(text)
        } catch (e: AiException) { throw e
        } catch (e: java.io.IOException) { throw AiException("network", "Server non raggiungibile: controlla la connessione.")
        } catch (e: org.json.JSONException) { throw AiException("bad_output", "Risposta del server non valida.") }
    }

    suspend fun analyze(photo: Bitmap): AiAnalysis {
        val j = post("/api/ai/analyze", imageBody(photo))
        return AiAnalysis(
            j.optString("provider"),
            j.optString("name"), j.optString("category"), j.optString("colorHex"), j.optString("colorName"), j.optString("brand"),
            strings(j.optJSONArray("seasons")), strings(j.optJSONArray("occasions")),
            j.optString("material"), j.optString("pattern"), j.optString("notes"),
        )
    }

    suspend fun chat(history: List<Pair<String, String>>, closet: List<Item>, wears: (Item) -> Int, weather: String?): AiChatReply {
        val body = closetBody(closet, wears).put("messages", JSONArray(history.map { (role, text) ->
            JSONObject().put("role", role).put("content", text)
        }))
        if (!weather.isNullOrBlank()) body.put("weather", weather)
        val j = post("/api/ai/chat", body)
        return AiChatReply(j.optString("reply"), outfits(j.optJSONArray("outfits")))
    }

    suspend fun purchase(photo: Bitmap, closet: List<Item>, wears: (Item) -> Int): AiPurchase {
        val j = post("/api/ai/purchase", imageBody(photo).also { b -> closetBody(closet, wears).let { c -> b.put("closet", c.get("closet")) } })
        return AiPurchase(
            j.optString("name"), j.optString("category"), j.optString("colorHex"), j.optString("colorName"), j.optString("verdict"),
            j.optString("summary"), strings(j.optJSONArray("reasons")), longs(j.optJSONArray("similarIds")),
            longs(j.optJSONArray("pairsWellIds")), outfits(j.optJSONArray("outfitIdeas")),
        )
    }

    companion object {
        fun providerLabel(name: String) = when (name) {
            "workers-ai" -> "Cloudflare"; "gemini" -> "Google Gemini"; "anthropic" -> "Claude"; else -> name
        }

        /** JPEG ridimensionato (max 768 px) su fondo chiaro: le foto con sfondo rimosso sono PNG trasparenti. */
        fun encodeImage(src: Bitmap): String {
            val scale = minOf(1f, 768f / maxOf(src.width, src.height))
            val w = (src.width * scale).toInt().coerceAtLeast(1); val h = (src.height * scale).toInt().coerceAtLeast(1)
            val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(flat); c.drawColor(0xFFF7F4EF.toInt())
            c.drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), 0f, 0f, null)
            val out = ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, 85, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        fun imageBody(photo: Bitmap) = JSONObject().put("imageBase64", encodeImage(photo)).put("mediaType", "image/jpeg")

        /** Armadio in forma compatta: solo capi posseduti, i piu' recenti per primi, al massimo 300. */
        fun closetBody(closet: List<Item>, wears: (Item) -> Int): JSONObject {
            val arr = JSONArray()
            closet.filter { it.status == "OWNED" }.take(300).forEach { i ->
                arr.put(JSONObject().put("id", i.id).put("name", i.name).put("category", i.category)
                    .put("color", Colors.name(i.colorHex)).put("brand", i.brand).put("seasons", i.seasons)
                    .put("occasions", i.occasions).put("wears", wears(i)))
            }
            return JSONObject().put("closet", arr)
        }

        private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
        private fun longs(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getLong(it) }
        private fun outfits(a: JSONArray?) = (0 until (a?.length() ?: 0)).map {
            val o = a!!.getJSONObject(it)
            AiOutfit(o.optString("title"), longs(o.optJSONArray("itemIds")), o.optString("why"))
        }
    }
}

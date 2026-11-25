package com.mycloset.app.logic

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class Place(val name: String, val country: String, val lat: Double, val lon: Double)

data class DayForecast(val date: LocalDate, val min: Double, val max: Double, val rainPct: Int, val code: Int)

data class Weather(
    val city: String,
    val temp: Double,
    val feelsLike: Double,
    val code: Int,
    val days: List<DayForecast>,
    val offline: Boolean = false,
) {
    val rainy: Boolean get() = code in RAIN_CODES || (days.firstOrNull()?.rainPct ?: 0) >= 50

    companion object {
        val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        fun describe(code: Int): String = when (code) {
            0 -> "Sereno"; 1 -> "Prevalentemente sereno"; 2 -> "Parzialmente nuvoloso"; 3 -> "Coperto"
            45, 48 -> "Nebbia"; 51, 53, 55 -> "Pioviggine"; 56, 57 -> "Pioggia gelata"
            61 -> "Pioggia debole"; 63 -> "Pioggia"; 65 -> "Pioggia forte"; 66, 67 -> "Pioggia gelata"
            71, 73, 75, 77 -> "Neve"; 80, 81, 82 -> "Rovesci"; 85, 86 -> "Nevischio"
            95, 96, 99 -> "Temporale"; else -> "Variabile"
        }
    }
}

/** Meteo da Open-Meteo (gratuito, senza chiave). Ultimo risultato in cache per l'uso offline. */
object WeatherApi {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    fun search(query: String): List<Place> {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=it&format=json"))
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Place(o.getString("name"), o.optString("country"), o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    fun forecast(city: String, lat: Double, lon: Double): Weather {
        val raw = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&timezone=auto&forecast_days=7"
        )
        return parse(city, raw)
    }

    fun parse(city: String, raw: String, offline: Boolean = false): Weather {
        val j = JSONObject(raw)
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            DayForecast(
                LocalDate.parse(dates.getString(i)),
                d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("temperature_2m_max").optDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i),
                d.getJSONArray("weather_code").optInt(i),
            )
        }
        return Weather(city, cur.getDouble("temperature_2m"), cur.getDouble("apparent_temperature"),
            cur.getInt("weather_code"), days, offline)
    }

    /** Scarica; in caso di errore usa l'ultimo meteo salvato. */
    fun load(context: Context, prefs: Prefs): Weather? {
        val lat = prefs.lat; val lon = prefs.lon
        if (lat == null || lon == null) return null
        return try {
            val w = forecast(prefs.city, lat, lon)
            prefs.weatherCache = rawCache(w) // vedi sotto
            w
        } catch (_: Exception) {
            prefs.weatherCache.takeIf { it.isNotEmpty() }?.let {
                try { parse(prefs.city, it, offline = true) } catch (_: Exception) { null }
            }
        }
    }

    // Per la cache ri-serializziamo nel formato Open-Meteo che parse() capisce.
    private fun rawCache(w: Weather): String {
        val daily = JSONObject()
            .put("time", org.json.JSONArray(w.days.map { it.date.toString() }))
            .put("temperature_2m_min", org.json.JSONArray(w.days.map { it.min }))
            .put("temperature_2m_max", org.json.JSONArray(w.days.map { it.max }))
            .put("precipitation_probability_max", org.json.JSONArray(w.days.map { it.rainPct }))
            .put("weather_code", org.json.JSONArray(w.days.map { it.code }))
        val cur = JSONObject().put("temperature_2m", w.temp).put("apparent_temperature", w.feelsLike)
            .put("weather_code", w.code)
        return JSONObject().put("current", cur).put("daily", daily).toString()
    }
}

package com.mycloset.app.logic

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mycloset", Context.MODE_PRIVATE)

    var city: String
        get() = sp.getString("city", "") ?: ""
        set(v) = sp.edit().putString("city", v).apply()
    var lat: Double?
        get() = if (sp.contains("lat")) java.lang.Double.longBitsToDouble(sp.getLong("lat", 0)) else null
        set(v) = sp.edit().apply { if (v == null) remove("lat") else putLong("lat", java.lang.Double.doubleToLongBits(v)) }.apply()
    var lon: Double?
        get() = if (sp.contains("lon")) java.lang.Double.longBitsToDouble(sp.getLong("lon", 0)) else null
        set(v) = sp.edit().apply { if (v == null) remove("lon") else putLong("lon", java.lang.Double.doubleToLongBits(v)) }.apply()
    var currency: String
        get() = sp.getString("currency", "€") ?: "€"
        set(v) = sp.edit().putString("currency", v).apply()
    var weatherCache: String
        get() = sp.getString("weather", "") ?: ""
        set(v) = sp.edit().putString("weather", v).apply()
}

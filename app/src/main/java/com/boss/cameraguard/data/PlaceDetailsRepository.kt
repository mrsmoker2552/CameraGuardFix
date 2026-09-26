package com.boss.cameraguard.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Details are OSM data only. No invented reviews, photos or open-now status. */
object PlaceDetailsRepository {
    data class Details(val name: String, val latitude: Double, val longitude: Double,
        val address: String = "", val category: String = "Place", val hours: String? = null,
        val phone: String? = null, val website: String? = null) {
        fun place() = RoutePlannerRepository.Place(name, latitude, longitude)
    }
    private var lastLookup = 0L
    private val cache = mutableMapOf<String, Details>()
    @Synchronized fun reverse(lat: Double, lon: Double, label: String?): Details {
        val key = "$lat,$lon,$label"
        cache[key]?.let { return it }
        val wait = 1100L - (android.os.SystemClock.elapsedRealtime() - lastLookup)
        if (wait > 0) Thread.sleep(wait)
        lastLookup = android.os.SystemClock.elapsedRealtime()
        val json = JSONObject(get("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=18&addressdetails=1&extratags=1"))
        val name = json.optString("name").ifBlank { "Selected location" }
        // Reverse lookup may return a neighbouring building, not the tapped named POI.
        if (!label.isNullOrBlank() && !name.equals(label, ignoreCase=true)) {
            return Details(label, lat, lon, category="Map place").also { cache[key]=it }
        }
        val tags = json.optJSONObject("extratags")
        fun tag(vararg names: String): String? = names.firstNotNullOfOrNull { tags?.optString(it)?.takeIf { v -> v.isNotBlank() } }
        return Details(label ?: name, lat, lon, json.optString("display_name"),
            json.optString("type", "Place").replace('_', ' '), tag("opening_hours"),
            tag("phone", "contact:phone"), tag("website", "contact:website")).also { cache[key]=it }
    }
    fun nearby(lat: Double, lon: Double, category: String): List<Details> {
        val filter = when(category) {
            "Petrol" -> "[amenity=fuel]"
            "Food" -> "[amenity~\"restaurant|cafe|fast_food\"]"
            "Shops" -> "[shop]"
            "Hotels" -> "[tourism~\"hotel|guest_house|motel\"]"
            else -> "[leisure=park]"
        }
        val query = "[out:json][timeout:20];nwr$filter(around:2500,$lat,$lon);out center tags 40;"
        val json = JSONObject(get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query,"UTF-8")))
        val items = json.getJSONArray("elements")
        return (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i); val tags = item.optJSONObject("tags") ?: return@mapNotNull null
            val point = item.optJSONObject("center") ?: item
            if (!point.has("lat") || !point.has("lon")) return@mapNotNull null
            Details(tags.optString("name").ifBlank { tags.optString("brand").ifBlank { category } },
                point.getDouble("lat"), point.getDouble("lon"),
                listOf(tags.optString("addr:street"),tags.optString("addr:housenumber"),tags.optString("addr:city")).filter { it.isNotBlank() }.joinToString(" "), category,
                tags.optString("opening_hours").takeIf { it.isNotBlank() },
                tags.optString("phone",tags.optString("contact:phone")).takeIf { it.isNotBlank() },
                tags.optString("website",tags.optString("contact:website")).takeIf { it.isNotBlank() })
        }.sortedBy { val out=FloatArray(1); android.location.Location.distanceBetween(lat,lon,it.latitude,it.longitude,out); out[0] }
    }
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=10000; c.readTimeout=25000
        c.setRequestProperty("User-Agent", "CameraGuard/1.5.7 Android")
        return try { check(c.responseCode in 200..299) { "Place service unavailable (${c.responseCode})" }; c.inputStream.bufferedReader().use { it.readText() } }
        finally { c.disconnect() }
    }
}

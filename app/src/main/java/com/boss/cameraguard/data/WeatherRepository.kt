package com.boss.cameraguard.data

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free, no-API-key current weather for the rider's live position, via Open-Meteo
 * (https://open-meteo.com - public, no signup, no rate-limit key required for this
 * volume of use). Read-only, informational: never used in any camera-warning or
 * routing decision, matching the app's existing "never a fabricated data source"
 * approach used for CurrentRoadRepository/RoutePlannerRepository.
 */
data class CurrentWeather(val temperatureC: Double, val weatherCode: Int, val windKmh: Double, val isDay: Boolean) {
    /** Short label + a plain-text glyph rendered by Compose Text, no icon-font/network image dependency. */
    val summary: String get() = when (weatherCode) {
        0 -> if (isDay) "Clear" else "Clear night"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather"
    }
    val glyph: String get() = when (weatherCode) {
        0 -> if (isDay) "☀" else "☽"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "▒"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "☔"
        71, 73, 75, 77, 85, 86 -> "❄"
        95, 96, 99 -> "⚡"
        else -> "☁"
    }
}

object WeatherRepository {
    private data class Cached(val atMillis: Long, val cellLat: Int, val cellLon: Int, val weather: CurrentWeather)
    // Weather changes slowly and Open-Meteo's own model updates hourly, so a rider
    // moving within roughly the same ~1km cell reuses the last fetch instead of
    // re-querying on every HUD/map recomposition.
    private const val CACHE_TTL_MILLIS = 10L * 60L * 1000L
    @Volatile private var cache: Cached? = null

    fun current(location: Location): CurrentWeather? {
        val cellLat = (location.latitude * 100).toInt()
        val cellLon = (location.longitude * 100).toInt()
        val now = System.currentTimeMillis()
        cache?.let { if (now - it.atMillis <= CACHE_TTL_MILLIS && it.cellLat == cellLat && it.cellLon == cellLon) return it.weather }
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code,wind_speed_10m,is_day&timezone=auto"
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 10000; requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val body = if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else return null
            val current = JSONObject(body).optJSONObject("current") ?: return null
            val weather = CurrentWeather(
                temperatureC = current.optDouble("temperature_2m", Double.NaN).takeIf { it.isFinite() } ?: return null,
                weatherCode = current.optInt("weather_code", -1),
                windKmh = current.optDouble("wind_speed_10m", 0.0),
                isDay = current.optInt("is_day", 1) == 1
            )
            cache = Cached(now, cellLat, cellLon, weather)
            weather
        } catch (_: Exception) {
            null
        }
    }
}

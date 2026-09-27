package com.boss.cameraguard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlace(val label: String, val name: String, val lat: Double, val lon: Double)
data class TripRecord(val destinationName: String, val distanceMeters: Double, val durationSeconds: Double, val completedAtMillis: Long)

/**
 * Free, on-device Home/Work/saved-places and recent-trips history - plain SharedPreferences,
 * no account, server or new permission required. Purely informational/UI convenience: it never
 * feeds routing, map-matching or camera-warning logic, all of which are untouched by this.
 */
object SavedPlacesRepository {
    private const val PREFS = "cameraguard_saved_places"
    private const val KEY_HOME = "home"
    private const val KEY_WORK = "work"
    private const val KEY_SAVED = "saved"
    private const val KEY_TRIPS = "trips"
    private const val MAX_SAVED = 20
    private const val MAX_TRIPS = 30

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun placeToJson(p: SavedPlace) = JSONObject().put("label", p.label).put("name", p.name).put("lat", p.lat).put("lon", p.lon)
    private fun placeFromJson(o: JSONObject) = SavedPlace(o.optString("label"), o.optString("name"), o.optDouble("lat"), o.optDouble("lon"))

    fun home(context: Context): SavedPlace? = prefs(context).getString(KEY_HOME, null)?.let { runCatching { placeFromJson(JSONObject(it)) }.getOrNull() }
    fun work(context: Context): SavedPlace? = prefs(context).getString(KEY_WORK, null)?.let { runCatching { placeFromJson(JSONObject(it)) }.getOrNull() }

    fun setHome(context: Context, name: String, lat: Double, lon: Double) {
        prefs(context).edit().putString(KEY_HOME, placeToJson(SavedPlace("Home", name, lat, lon)).toString()).apply()
    }
    fun setWork(context: Context, name: String, lat: Double, lon: Double) {
        prefs(context).edit().putString(KEY_WORK, placeToJson(SavedPlace("Work", name, lat, lon)).toString()).apply()
    }

    fun savedPlaces(context: Context): List<SavedPlace> {
        val raw = prefs(context).getString(KEY_SAVED, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { placeFromJson(it) } }
    }

    fun addSavedPlace(context: Context, name: String, lat: Double, lon: Double) {
        val existing = savedPlaces(context).filterNot { it.name == name && it.lat == lat && it.lon == lon }
        val updated = (listOf(SavedPlace(name.take(24), name, lat, lon)) + existing).take(MAX_SAVED)
        val arr = JSONArray(); updated.forEach { arr.put(placeToJson(it)) }
        prefs(context).edit().putString(KEY_SAVED, arr.toString()).apply()
    }

    fun removeSavedPlace(context: Context, name: String, lat: Double, lon: Double) {
        val updated = savedPlaces(context).filterNot { it.name == name && it.lat == lat && it.lon == lon }
        val arr = JSONArray(); updated.forEach { arr.put(placeToJson(it)) }
        prefs(context).edit().putString(KEY_SAVED, arr.toString()).apply()
    }

    fun trips(context: Context): List<TripRecord> {
        val raw = prefs(context).getString(KEY_TRIPS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                TripRecord(it.optString("destinationName"), it.optDouble("distanceMeters"), it.optDouble("durationSeconds"), it.optLong("completedAtMillis"))
            }
        }
    }

    fun recordTrip(context: Context, destinationName: String, distanceMeters: Double, durationSeconds: Double) {
        if (destinationName.isBlank()) return
        val updated = (listOf(TripRecord(destinationName, distanceMeters, durationSeconds, System.currentTimeMillis())) + trips(context)).take(MAX_TRIPS)
        val arr = JSONArray()
        updated.forEach { t ->
            arr.put(JSONObject()
                .put("destinationName", t.destinationName)
                .put("distanceMeters", t.distanceMeters)
                .put("durationSeconds", t.durationSeconds)
                .put("completedAtMillis", t.completedAtMillis))
        }
        prefs(context).edit().putString(KEY_TRIPS, arr.toString()).apply()
    }

    fun clearTrips(context: Context) { prefs(context).edit().remove(KEY_TRIPS).apply() }
}

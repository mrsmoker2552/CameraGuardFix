package com.boss.cameraguard.data

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Prototype geocoding/routing client. Route preferences are sent to the routing engine, not merely stored in UI. */
object RoutePlannerRepository {
    data class Place(val name: String, val lat: Double, val lon: Double)
    private data class CachedSuggestions(val atMillis: Long, val places: List<Place>)
    private val suggestionCache = LinkedHashMap<String, CachedSuggestions>(24, 0.75f, true)
    private const val SUGGESTION_CACHE_TTL_MILLIS = 5L * 60L * 1000L

    fun searchPlace(query: String): List<Place> {
        if (query.isBlank()) return emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val arr = JSONArray(get("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=$q"))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            Place(o.optString("display_name", "Destination"), lat, lon)
        }
    }

    fun suggestPlaces(query: String, origin: Location? = null): List<Place> {
        val normalized = query.trim().lowercase()
        if (normalized.length < 2) return emptyList()
        val now = System.currentTimeMillis()
        synchronized(suggestionCache) {
            suggestionCache[normalized + (origin?.let { ":${"%.2f".format(java.util.Locale.US,it.latitude)}:${"%.2f".format(java.util.Locale.US,it.longitude)}" } ?: "")]?.takeIf { now - it.atMillis <= SUGGESTION_CACHE_TTL_MILLIS }?.let { return it.places }
        }
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val proximity = origin?.let { "&lat=${it.latitude}&lon=${it.longitude}&location_bias_scale=0.85" } ?: ""
        val root = JSONObject(get("https://photon.komoot.io/api/?limit=12&lang=it&q=$q$proximity"))
        val features = root.optJSONArray("features") ?: return emptyList()
        val places = (0 until features.length()).mapNotNull { i ->
            val f = features.optJSONObject(i) ?: return@mapNotNull null
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coords.length() < 2) return@mapNotNull null
            val props = f.optJSONObject("properties")
            val name = listOfNotNull(
                props?.optString("name")?.takeIf { it.isNotBlank() },
                props?.optString("street")?.takeIf { it.isNotBlank() }?.let { street ->
                    val houseNumber = props?.optString("housenumber")?.takeIf { it.isNotBlank() }
                    if (houseNumber == null) street else "$street $houseNumber"
                },
                props?.optString("city")?.takeIf { it.isNotBlank() }
                    ?: props?.optString("town")?.takeIf { it.isNotBlank() }
                    ?: props?.optString("village")?.takeIf { it.isNotBlank() },
                props?.optString("state")?.takeIf { it.isNotBlank() },
                props?.optString("country")?.takeIf { it.isNotBlank() }
            ).distinct().joinToString(", ").ifBlank { "Destination" }
            Place(name, coords.getDouble(1), coords.getDouble(0))
        }.distinctBy { "%.5f,%.5f".format(java.util.Locale.US, it.lat, it.lon) }
            .sortedWith(compareBy<Place> { !it.name.lowercase().startsWith(normalized) }
                .thenBy { place -> origin?.let { distanceMeters(LatLng(it.latitude, it.longitude), LatLng(place.lat, place.lon)) } ?: 0f })
            .take(7)
        synchronized(suggestionCache) {
            suggestionCache[normalized + (origin?.let { ":${"%.2f".format(java.util.Locale.US,it.latitude)}:${"%.2f".format(java.util.Locale.US,it.longitude)}" } ?: "")] = CachedSuggestions(now, places)
            while (suggestionCache.size > 24) suggestionCache.remove(suggestionCache.entries.first().key)
        }
        return places
    }

    fun nearbyPlaces(category: String, origin: Location): List<Place> {
        val filter = when (category) {
            "Petrol" -> "node[amenity=fuel]"
            "Food" -> "node[amenity~\"^(restaurant|cafe|fast_food)$\"]"
            "Shops" -> "node[shop]"
            "Hotels" -> "node[tourism~\"^(hotel|hostel|guest_house)$\"]"
            "Parks" -> "node[leisure=park]"
            else -> return emptyList()
        }
        val query = "[out:json][timeout:15];$filter(around:5000,${origin.latitude},${origin.longitude});out center 30;"
        val url = "https://overpass.kumi.systems/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8")
        val items = JSONObject(get(url)).optJSONArray("elements") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val node = items.optJSONObject(i) ?: return@mapNotNull null
            val lat = node.optDouble("lat", Double.NaN)
            val lon = node.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            val tags = node.optJSONObject("tags")
            Place(tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("brand")?.takeIf { it.isNotBlank() } ?: category, lat, lon)
        }.sortedBy { distanceMeters(LatLng(origin.latitude, origin.longitude), LatLng(it.lat, it.lon)) }.take(12)
    }

    fun route(origin: Location, destination: Place, preferences: RoutePreferences = RoutePreferences()): NavigationRoute =
        routeWithAlternates(origin, destination, preferences).first()

    /**
     * Same routing call as [route], but also asks Valhalla for up to one alternate road
     * option (free - Valhalla's public instance already supports the `alternates` request
     * field, no separate service or key needed) and returns every option found, primary
     * first. Falls back to a single-route list when the backend has no free-form deviation
     * to offer, or when OSRM (which has no alternates support here) is used instead.
     */
    fun routeWithAlternates(origin: Location, destination: Place, preferences: RoutePreferences = RoutePreferences(), maxAlternates: Int = 1): List<NavigationRoute> {
        // Valhalla exposes actual road-cost controls. Autostrada avoidance is strictest;
        // Tangenziale uses a strong highway penalty while still allowing access when no practical alternative exists.
        val highwayUse = when {
            preferences.avoidAutostrada -> 0.0
            preferences.avoidTangenziale -> 0.15
            else -> 1.0
        }
        val tollUse = if (preferences.avoidTollRoads) 0.0 else 1.0
        val payload = JSONObject().apply {
            put("locations", JSONArray().apply {
                put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude).put("type", "break"))
                put(JSONObject().put("lat", destination.lat).put("lon", destination.lon).put("type", "break"))
            })
            put("costing", "auto")
            put("costing_options", JSONObject().put("auto", JSONObject()
                .put("use_highways", highwayUse)
                .put("use_tolls", tollUse)))
            put("units", "kilometers")
            if (maxAlternates > 0) put("alternates", maxAlternates)
        }
        val calculated = runCatching { routeValhalla(destination, payload.toString()) }
            .getOrElse { error ->
                // The public OSRM fallback cannot guarantee all CameraGuard avoidance
                // preferences (especially toll-road avoidance). Never silently return a
                // route that contradicts an enabled user preference.
                if (preferences.avoidAutostrada || preferences.avoidTangenziale || preferences.avoidTollRoads) {
                    throw IllegalStateException("Preferred-route service unavailable; avoidance options were not relaxed", error)
                }
                listOf(routeOsrm(origin, destination))
            }

        // Lock every rendered route to the two user-visible navigation anchors.
        // Routing engines snap break points to the drivable carriageway, which is correct
        // for guidance, but the UI must visibly start at the rider's live GPS position and
        // terminate at the selected destination pin. These short connector segments make
        // that relationship explicit without changing the calculated road path.
        val originPoint = LatLng(origin.latitude, origin.longitude)
        val destinationPoint = LatLng(destination.lat, destination.lon)
        return calculated.map { candidate ->
            val normalized = buildList {
                add(originPoint)
                candidate.points.forEach { point ->
                    if (lastOrNull() == null || distanceMeters(last(), point) > 1.5f) add(point)
                }
                if (lastOrNull() == null || distanceMeters(last(), destinationPoint) > 1.5f) add(destinationPoint)
                else if (isNotEmpty()) this[size - 1] = destinationPoint
            }
            candidate.copy(points = normalized)
        }
    }

    /** First element is Valhalla's primary trip; any further elements are its `alternates`, cheapest-first as returned. */
    private fun routeValhalla(destination: Place, payload: String): List<NavigationRoute> {
        val root = JSONObject(post("https://valhalla1.openstreetmap.de/route", payload))
        val trips = buildList {
            add(root.getJSONObject("trip"))
            root.optJSONArray("alternates")?.let { alts ->
                for (i in 0 until alts.length()) alts.optJSONObject(i)?.optJSONObject("trip")?.let { add(it) }
            }
        }
        return trips.map { trip -> parseValhallaTrip(destination, trip) }
    }

    private fun parseValhallaTrip(destination: Place, trip: JSONObject): NavigationRoute {
        val legs = trip.getJSONArray("legs")
        val points = mutableListOf<LatLng>()
        val steps = mutableListOf<NavigationStep>()
        for (i in 0 until legs.length()) {
            val shape = legs.getJSONObject(i).getString("shape")
            val decoded = decodePolyline6(shape)
            val maneuvers = legs.getJSONObject(i).optJSONArray("maneuvers")
            if (maneuvers != null) for (j in 0 until maneuvers.length()) {
                val m = maneuvers.getJSONObject(j)
                decoded.getOrNull(m.optInt("begin_shape_index", -1))?.let { point ->
                    steps += NavigationStep(m.optString("instruction", "Continue"), point)
                }
            }
            if (points.isNotEmpty() && decoded.isNotEmpty() && points.last() == decoded.first()) points.addAll(decoded.drop(1)) else points.addAll(decoded)
        }
        val summary = trip.getJSONObject("summary")
        return NavigationRoute(destination.name, destination.lat, destination.lon, points,
            summary.optDouble("length", 0.0) * 1000.0, summary.optDouble("time", 0.0), steps = steps)
    }

    private fun routeOsrm(origin: Location, destination: Place): NavigationRoute {
        val url = "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.lon},${destination.lat}?overview=full&geometries=geojson&steps=true"
        val root = JSONObject(get(url))
        val r = root.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map { i -> val p = coords.getJSONArray(i); LatLng(p.getDouble(1), p.getDouble(0)) }
        val steps = mutableListOf<NavigationStep>()
        val legs = r.getJSONArray("legs")
        for (i in 0 until legs.length()) {
            val maneuvers = legs.getJSONObject(i).getJSONArray("steps")
            for (j in 0 until maneuvers.length()) {
                val step = maneuvers.getJSONObject(j)
                val m = step.getJSONObject("maneuver")
                val point = m.getJSONArray("location")
                val action = m.optString("type", "continue").replace('_', ' ')
                val direction = m.optString("modifier", "")
                val road = step.optString("name", "")
                val exit = m.optInt("exit", 0)
                val instruction = listOf(action.replaceFirstChar { it.uppercase() }, direction,
                    if (exit > 0) "exit $exit" else "", if (road.isNotBlank()) "onto $road" else "")
                    .filter { it.isNotBlank() }.joinToString(" ")
                steps += NavigationStep(instruction, LatLng(point.getDouble(1), point.getDouble(0)))
            }
        }
        return NavigationRoute(destination.name, destination.lat, destination.lon, points, r.getDouble("distance"), r.getDouble("duration"), steps = steps)
    }

    private fun decodePolyline6(encoded: String): List<LatLng> {
        val out = mutableListOf<LatLng>(); var index = 0; var lat = 0; var lon = 0
        while (index < encoded.length) {
            var result = 0; var shift = 0; var b: Int
            do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20 && index < encoded.length)
            lat += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            result = 0; shift = 0
            do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20 && index < encoded.length)
            lon += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            out += LatLng(lat / 1e6, lon / 1e6)
        }
        return out
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Float {
        val out = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
        return out[0]
    }

    private fun get(url: String): String = connection(url, "GET", null)
    private fun post(url: String, body: String): String = connection(url, "POST", body)
    private fun connection(url: String, method: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000; c.requestMethod = method
        c.setRequestProperty("User-Agent", "CameraGuard/1.5.2 Android"); c.setRequestProperty("Accept", "application/json")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        return try { val code = c.responseCode; if (code !in 200..299) error("HTTP $code"); c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}

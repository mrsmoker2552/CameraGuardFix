package com.boss.cameraguard.data

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class CurrentRoad(val osmWayId: Long, val name: String?, val points: List<RoadPoint>, val speedLimitKmh: Int? = null)

/** Lightweight OSM map matcher for the HUD only. It never participates in camera-warning decisions. */
class CurrentRoadRepository {
    private val endpoints = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    fun match(location: Location, radiusMeters: Int = 90): CurrentRoad? {
        val q="""[out:json][timeout:12];way[highway](around:$radiusMeters,${location.latitude},${location.longitude});out tags geom;"""
        for (endpoint in endpoints) {
            try {
                val result = parse(post(endpoint, q), location)
                if (result != null) return result
            } catch (_: Exception) {
                // Try the second mirror if the first times out or returns no usable road.
            }
        }
        return null
    }
    private fun post(endpoint:String,q:String):String {
        val c=(URL(endpoint).openConnection() as HttpURLConnection).apply { requestMethod="POST"; connectTimeout=7000; readTimeout=18000; doOutput=true; setRequestProperty("Content-Type","application/x-www-form-urlencoded") }
        c.outputStream.bufferedWriter().use { it.write("data="+java.net.URLEncoder.encode(q,"UTF-8")) }
        if(c.responseCode !in 200..299) error("Overpass ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
    private fun parse(json:String, loc:Location):CurrentRoad? {
        val arr=JSONObject(json).optJSONArray("elements") ?: return null
        var best:CurrentRoad?=null; var bestScore=Double.MAX_VALUE
        for(i in 0 until arr.length()) { val o=arr.optJSONObject(i)?:continue; val g=o.optJSONArray("geometry")?:continue; if(g.length()<2) continue
            val tags=o.optJSONObject("tags")
            val highway=tags?.optString("highway").orEmpty()
            if (highway in setOf("footway", "path", "cycleway", "pedestrian", "steps", "bridleway", "corridor", "construction", "proposed")) continue
            val pts=(0 until g.length()).mapNotNull { j->g.optJSONObject(j)?.let { RoadPoint(it.optDouble("lat"),it.optDouble("lon")) } }
            if(pts.size<2) continue
            for(j in 0 until pts.lastIndex){ val a=pts[j]; val b=pts[j+1]; val d=segmentDistanceMeters(loc.latitude,loc.longitude,a,b); val forwardBearing=bearing(a,b); val reverseBearing=(forwardBearing+180.0)%360.0; val hd=if(loc.hasBearing() && loc.speed>1.5f) minOf(angleDiff(loc.bearing.toDouble(),forwardBearing), angleDiff(loc.bearing.toDouble(),reverseBearing)) else 0.0; val score=d + hd*0.65
                if(d<35 && hd<65 && score<bestScore){
                    bestScore=score
                    // Include a larger local corridor in BOTH OSM digitization directions.
                    // A tiny 3-point slice caused the HUD road to end almost immediately.
                    val start=maxOf(0,j-36); val end=minOf(pts.size,j+61)
                    best=CurrentRoad(o.optLong("id"),tags?.optString("name")?.takeIf{it.isNotBlank()},pts.subList(start,end),parseMaxspeed(tags?.optString("maxspeed")))
                }
            }
        }; return best
    }
    private fun segmentDistanceMeters(lat:Double,lon:Double,a:RoadPoint,b:RoadPoint):Double { val mLat=110540.0; val mLon=111320.0*cos(Math.toRadians(lat)); val ax=(a.longitude-lon)*mLon; val ay=(a.latitude-lat)*mLat; val bx=(b.longitude-lon)*mLon; val by=(b.latitude-lat)*mLat; val dx=bx-ax; val dy=by-ay; val t=if(dx*dx+dy*dy==0.0)0.0 else (-(ax*dx+ay*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0); return hypot(ax+t*dx,ay+t*dy) }
    private fun bearing(a:RoadPoint,b:RoadPoint)=((Math.toDegrees(atan2(sin(Math.toRadians(b.longitude-a.longitude))*cos(Math.toRadians(b.latitude)), cos(Math.toRadians(a.latitude))*sin(Math.toRadians(b.latitude))-sin(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*cos(Math.toRadians(b.longitude-a.longitude))))+360)%360)
    private fun angleDiff(a:Double,b:Double)=abs((a-b+540)%360-180)

    /**
     * OSM's `maxspeed` tag is free-text: usually a plain km/h number, sometimes "30 mph"
     * (mainly UK/US ways) or a country implicit-limit code (e.g. "PK:urban") that this app
     * cannot resolve to a number without a lookup table. Anything it can't confidently
     * parse as a numeric limit is left null rather than guessed, since a wrong displayed
     * speed limit is worse than none.
     */
    private fun parseMaxspeed(raw: String?): Int? {
        val value = raw?.trim()?.lowercase() ?: return null
        if (value.isEmpty()) return null
        val mphMatch = Regex("""(\d+)\s*mph""").find(value)
        if (mphMatch != null) return (mphMatch.groupValues[1].toDouble() * 1.60934).roundToInt()
        val plain = Regex("""^(\d+)""").find(value) ?: return null
        return plain.groupValues[1].toIntOrNull()
    }

    /**
     * Keep real OSM road sections near the rider instead of taking the first N ways
     * returned by Overpass (whose order does not correspond to geographic proximity).
     * Full local geometry and endpoint vertices are needed by the HUD to join roads;
     * this network is never used to make camera-warning or routing decisions.
     */
    fun fetchNearbyRoadNetwork(location: Location, radiusMeters: Int = 2200, maxWays: Int = 700, maxPointsPerWay: Int = 160): List<List<RoadPoint>> {
        val q = """[out:json][timeout:24];way[highway](around:$radiusMeters,${location.latitude},${location.longitude});out tags geom;"""
        for (endpoint in endpoints) {
            try {
                val elements = JSONObject(post(endpoint, q)).optJSONArray("elements") ?: continue
                val candidates = mutableListOf<Pair<Double, List<RoadPoint>>>()
                for (i in 0 until elements.length()) {
                    val way = elements.optJSONObject(i) ?: continue
                    val type = way.optJSONObject("tags")?.optString("highway").orEmpty()
                    if (type in setOf("footway", "path", "cycleway", "pedestrian", "steps", "bridleway", "corridor", "construction", "proposed")) continue
                    val geometry = way.optJSONArray("geometry") ?: continue
                    if (geometry.length() < 2) continue
                    val points = (0 until geometry.length()).mapNotNull { index ->
                        geometry.optJSONObject(index)?.let { node ->
                            val lat = node.optDouble("lat", Double.NaN)
                            val lon = node.optDouble("lon", Double.NaN)
                            if (lat.isFinite() && lon.isFinite()) RoadPoint(lat, lon) else null
                        }
                    }
                    if (points.size < 2) continue
                    var proximity = Double.POSITIVE_INFINITY
                    var firstNearby = points.lastIndex
                    var lastNearby = 0
                    for (j in 0 until points.lastIndex) {
                        val distance = segmentDistanceMeters(location.latitude, location.longitude, points[j], points[j+1])
                        proximity = minOf(proximity, distance)
                        if (distance <= radiusMeters + 120) {
                            firstNearby = minOf(firstNearby, j)
                            lastNearby = maxOf(lastNearby, j+1)
                        }
                    }
                    if (proximity <= radiusMeters + 30 && firstNearby < lastNearby) {
                        // A single OSM way may continue for kilometres. Keep the full local
                        // neighbourhood with two real vertices of overlap on either side.
                        // This avoids spreading the 100-point budget across an entire city.
                        val from = maxOf(0, firstNearby - 2)
                        val until = minOf(points.size, lastNearby + 3)
                        if (until - from >= 2) candidates += proximity to points.subList(from, until)
                    }
                }
                // Keep the closest roads AND their complete bends / real OSM endpoints.
                // Preserve a fixed payload bound for slower mobile devices.
                val sorted = candidates.sortedBy { it.first }.take(maxWays)
                var budget = 26000
                val result = mutableListOf<List<RoadPoint>>()
                for ((_, way) in sorted) {
                    if (budget < 2) break
                    val count = minOf(maxPointsPerWay, budget)
                    val selected = if (way.size > count) {
                        (0 until count).map { index ->
                            way[((index.toDouble() * way.lastIndex) / (count-1)).toInt().coerceAtMost(way.lastIndex)]
                        }
                    } else way
                    if (selected.size >= 2) { result += selected; budget -= selected.size }
                }
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {
                // Retry the second Overpass mirror; retain the previous network on failure.
            }
        }
        return emptyList()
    }

}

package com.example.fitness.activity

import com.example.fitness.data.*
import org.json.JSONArray
import org.json.JSONObject

internal fun routeGeoJson(points: List<LocationPoint>, segments: List<Segment>): JSONObject {
    val features = JSONArray()
    segments.forEach { segment ->
        val coordinates = segmentCoordinates(points, segment)
        if (coordinates.size >= 2) {
            val lines = JSONArray()
            var line = JSONArray()
            coordinates.forEachIndexed { index, coordinate ->
                if (index > 0) {
                    val previous = coordinates[index - 1]
                    if (kotlin.math.abs(coordinate.lon - previous.lon) > 180) {
                        val adjusted = if (coordinate.lon < previous.lon) coordinate.lon + 360 else coordinate.lon - 360
                        val boundary = if (previous.lon > 0) 180.0 else -180.0
                        val fraction = (boundary - previous.lon) / (adjusted - previous.lon)
                        val lat = previous.lat + (coordinate.lat - previous.lat) * fraction
                        line.put(JSONArray(listOf(boundary, lat))); lines.put(line)
                        line = JSONArray().put(JSONArray(listOf(-boundary, lat)))
                    }
                }
                line.put(JSONArray(listOf(coordinate.lon, coordinate.lat)))
            }
            lines.put(line)
            features.put(JSONObject().put("type", "Feature").put("properties", JSONObject().put("type", segment.type.name))
                .put("geometry", JSONObject().put("type", "MultiLineString").put("coordinates", lines)))
        }
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features)
}

internal fun mapStyle(satellite: Boolean, key: String, points: List<LocationPoint>, segments: List<Segment>): String {
    val base = JSONObject().put("type", "raster").put("tileSize", 256)
    if (satellite) base.put("url", "https://api.maptiler.com/tiles/satellite-v2/tiles.json?key=${java.net.URLEncoder.encode(key, "UTF-8")}")
        .put("tileSize", 512)
    else base.put("tiles", JSONArray(listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png")))
        .put("maxzoom", 19).put("attribution", "© OpenStreetMap contributors")
    val route = routeGeoJson(points, segments)
    val markers = JSONArray()
    val coordinates = segments.flatMap { segmentCoordinates(points, it) }.ifEmpty { points.map { MapCoordinate(it.lat, it.lon, it.timestamp, it.speed) } }
    listOf("start" to coordinates.firstOrNull(), "end" to coordinates.lastOrNull()).forEach { (kind, point) ->
        point?.let { markers.put(JSONObject().put("type", "Feature").put("properties", JSONObject().put("kind", kind))
            .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(it.lon, it.lat))))) }
    }
    val sources = JSONObject().put("base", base)
        .put("route", JSONObject().put("type", "geojson").put("data", route))
        .put("ends", JSONObject().put("type", "geojson").put("data", JSONObject().put("type", "FeatureCollection").put("features", markers)))
        .put("cursor", JSONObject().put("type", "geojson").put("data", JSONObject().put("type", "FeatureCollection").put("features", JSONArray())))
    val layers = JSONArray()
        .put(JSONObject().put("id", "background").put("type", "background").put("paint", JSONObject().put("background-color", "#12151a")))
        .put(JSONObject().put("id", "base").put("type", "raster").put("source", "base"))
        .put(JSONObject().put("id", "route").put("type", "line").put("source", "route")
            .put("paint", JSONObject().put("line-width", 4).put("line-color", JSONArray("[\"match\",[\"get\",\"type\"],\"WALKING\",\"#7fd6b0\",\"VEHICLE\",\"#f0a35e\",\"#8fb8ff\"]"))))
        .put(JSONObject().put("id", "ends").put("type", "circle").put("source", "ends")
            .put("paint", JSONObject().put("circle-radius", 7).put("circle-stroke-width", 2).put("circle-stroke-color", "#12151a")
                .put("circle-color", JSONArray("[\"match\",[\"get\",\"kind\"],\"start\",\"#7fd6b0\",\"#f0a35e\"]"))))
        .put(JSONObject().put("id", "cursor").put("type", "circle").put("source", "cursor").put("paint", JSONObject().put("circle-radius", 8).put("circle-color", "#8fb8ff")))
    return JSONObject().put("version", 8).put("sources", sources).put("layers", layers).toString()
}

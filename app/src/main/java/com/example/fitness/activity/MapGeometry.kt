package com.example.fitness.activity

import com.example.fitness.data.LocationPoint
import com.example.fitness.data.Segment

data class MapCoordinate(val lat: Double, val lon: Double, val time: Long, val speed: Double?)
fun interpolateCoordinate(points: List<LocationPoint>, time: Long): MapCoordinate? {
    if (points.isEmpty()) return null
    val ordered = points // DAO order is timestamp, id; avoid sorting on every scrub.
    val exact = ordered.binarySearchBy(time) { it.timestamp }
    if (exact >= 0) return ordered[exact].let { MapCoordinate(it.lat, it.lon, it.timestamp, it.speed) }
    val index = -exact - 1
    if (index == 0 || index >= ordered.size) return null
    val a = ordered[index - 1]; val b = ordered[index]
    if (b.timestamp - a.timestamp > 180_000 || b.timestamp <= a.timestamp) return null
    val fraction = (time - a.timestamp).toDouble() / (b.timestamp - a.timestamp)
    val delta = ((b.lon - a.lon + 540) % 360) - 180
    val lon = ((a.lon + delta * fraction + 540) % 360) - 180
    val speed = if (a.speed != null && b.speed != null) a.speed + (b.speed - a.speed) * fraction else null
    return MapCoordinate(a.lat + (b.lat - a.lat) * fraction, lon, time, speed)
}
fun segmentCoordinates(points: List<LocationPoint>, segment: Segment): List<MapCoordinate> {
    val first = interpolateCoordinate(points, segment.start) ?: return emptyList()
    val last = interpolateCoordinate(points, segment.end) ?: return emptyList()
    val startIndex = points.binarySearchBy(segment.start) { it.timestamp }.let { if (it >= 0) it + 1 else -it - 1 }
    val endIndex = points.binarySearchBy(segment.end) { it.timestamp }.let { if (it >= 0) it else -it - 1 }
    return listOf(first) + points.subList(startIndex.coerceAtMost(points.size), endIndex.coerceAtLeast(startIndex).coerceAtMost(points.size))
        .map { MapCoordinate(it.lat, it.lon, it.timestamp, it.speed) } + last
}
fun scrubCoordinate(points: List<LocationPoint>, segments: List<Segment>, time: Long): MapCoordinate? =
    if (segments.none { time >= it.start && time <= it.end }) null else interpolateCoordinate(points, time)

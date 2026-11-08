package com.example.fitness.activity

import com.example.fitness.data.LocationPoint
import kotlin.math.*

enum class Motion { STILL, WALKING, VEHICLE, UNKNOWN }
data class Sampling(val intervalMs: Long, val minDistanceM: Float)
fun sampling(motion: Motion, saver: Boolean, turning: Boolean = false): Sampling? = when (motion) {
    Motion.STILL -> null
    Motion.WALKING -> Sampling(if (saver) 40_000 else 20_000, if (saver) 15f else 8f)
    Motion.VEHICLE -> Sampling(if (turning) 2_000 else if (saver) 10_000 else 5_000, if (turning) 5f else 15f)
    Motion.UNKNOWN -> Sampling(if (saver) 20_000 else 10_000, 10f)
}
fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val lat = Math.toRadians(bLat - aLat)
    val lon = Math.toRadians(bLon - aLon)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(lon / 2).pow(2)
    return 6_371_000 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1 - h).coerceIn(0.0, 1.0)))
}
fun validLocation(point: LocationPoint, previous: LocationPoint?): Boolean {
    if (!point.lat.isFinite() || point.lat !in -90.0..90.0 || !point.lon.isFinite() || point.lon !in -180.0..180.0 ||
        !point.accuracy.isFinite() || point.accuracy !in 0.0..50.0 || point.timestamp <= 0) return false
    if (point.speed != null && (!point.speed.isFinite() || point.speed !in 0.0..90.0)) return false
    if (previous != null) {
        val seconds = (point.timestamp - previous.timestamp) / 1000.0
        if (seconds <= 0) return false
        val implied = (distanceM(previous.lat, previous.lon, point.lat, point.lon) - previous.accuracy - point.accuracy).coerceAtLeast(0.0) / seconds
        if (implied > 90) return false
    }
    return true
}
fun bearingChange(a: Float, b: Float): Float = abs((b - a + 540) % 360 - 180)

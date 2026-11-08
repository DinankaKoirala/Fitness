package com.example.fitness.activity

import com.example.fitness.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

data class TimeRange(val from: Long, val to: Long) { init { require(to > from) } }
fun dayRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
    TimeRange(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())

fun classify(speed: Double, cadence: Double): SegmentType {
    require(speed.isFinite() && speed >= 0 && cadence.isFinite() && cadence >= 0)
    return when {
    speed <= 0.35 -> SegmentType.STILL
    cadence >= 30 && speed <= 7 -> SegmentType.WALKING
    speed >= 2.5 -> SegmentType.VEHICLE
    else -> SegmentType.WALKING
    }
}

fun cadence(steps: List<StepInterval>, from: Long, to: Long): Double {
    if (to <= from) return 0.0
    val count = steps.sumOf {
        val overlap = (min(to, it.end) - max(from, it.start)).coerceAtLeast(0)
        if (it.end <= it.start) 0.0 else it.steps.toDouble() * overlap / (it.end - it.start)
    }
    return count * 60_000 / (to - from)
}

fun buildSegments(points: List<LocationPoint>, steps: List<StepInterval>, labels: List<SegmentLabel>, range: TimeRange): List<Segment> {
    val result = mutableListOf<Segment>()
    val zone = ZoneId.systemDefault()
    val midnightCuts = generateSequence(Instant.ofEpochMilli(range.from).atZone(zone).toLocalDate().plusDays(1)) { it.plusDays(1) }
        .map { it.atStartOfDay(zone).toInstant().toEpochMilli() }.takeWhile { it < range.to }.toSet()
    val orderedSteps = steps.sortedBy { it.start }
    val activeSteps = mutableListOf<StepInterval>()
    var nextStep = 0
    val ordered = points.filter { validLocation(it, null) }.sortedBy { it.timestamp }.distinctBy { it.timestamp }
    ordered.zipWithNext().forEach { (a, b) ->
        val duration = b.timestamp - a.timestamp
        if (duration <= 0 || duration > 180_000 || !validLocation(b, a)) return@forEach
        val from = max(range.from, a.timestamp)
        val to = min(range.to, b.timestamp)
        if (to <= from) return@forEach
        while (nextStep < orderedSteps.size && orderedSteps[nextStep].start < to) activeSteps.add(orderedSteps[nextStep++])
        activeSteps.removeAll { it.end <= from }
        val distance = distanceM(a.lat, a.lon, b.lat, b.lon)
        val derived = (distance - a.accuracy - b.accuracy).coerceAtLeast(0.0) / (duration / 1000.0)
        val startSpeed = a.speed ?: derived
        val endSpeed = b.speed ?: derived
        val speed = (startSpeed + endSpeed) / 2
        val cuts = (listOf(from, to) + midnightCuts.filter { it > from && it < to } + labels.flatMap { listOf(it.start, it.end) }.filter { it > from && it < to }).distinct().sorted()
        cuts.zipWithNext().forEach { (start, end) ->
            val midpoint = start + (end - start) / 2
            val correction = labels.filter { it.start <= midpoint && it.end > midpoint }.maxWithOrNull(compareBy<SegmentLabel> { it.updatedAt }.thenBy { it.id })
            val type = correction?.type ?: classify(speed, cadence(activeSteps, start, end))
            val startFraction = (start - a.timestamp).toDouble() / duration
            val endFraction = (end - a.timestamp).toDouble() / duration
            val maxSpeed = max(startSpeed + (endSpeed - startSpeed) * startFraction, startSpeed + (endSpeed - startSpeed) * endFraction)
            val segment = Segment(start = start, end = end, type = type,
                distance = if (type == SegmentType.STILL) 0.0 else distance * (end - start) / duration,
                estimated = correction == null, maxSpeed = if (type == SegmentType.STILL) 0.0 else max(maxSpeed, distance / (duration / 1000.0)))
            val previous = result.lastOrNull()
            if (previous != null && previous.end == start && start !in midnightCuts && previous.type == type && previous.estimated == segment.estimated) {
                result[result.lastIndex] = previous.copy(end = end, distance = previous.distance + segment.distance,
                    maxSpeed = max(previous.maxSpeed, segment.maxSpeed))
            } else result.add(segment)
        }
    }
    return result
}

data class ActivityStats(val distance: Double, val walked: Double, val vehicle: Double, val averageSpeed: Double,
    val maxSpeed: Double, val observedMs: Long)
fun activityStats(segments: List<Segment>): ActivityStats {
    val moving = segments.filter { it.type != SegmentType.STILL }
    val distance = moving.sumOf { it.distance }
    val duration = moving.sumOf { it.end - it.start }
    return ActivityStats(distance, segments.filter { it.type == SegmentType.WALKING }.sumOf { it.distance },
        segments.filter { it.type == SegmentType.VEHICLE }.sumOf { it.distance },
        if (duration > 0) distance / (duration / 1000.0) else 0.0,
        moving.maxOfOrNull { it.maxSpeed } ?: 0.0, segments.sumOf { it.end - it.start })
}

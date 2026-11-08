package com.example.fitness.activity

import com.example.fitness.data.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ClassificationTest {
    private fun point(time: Long, lon: Double, speed: Double = 10.0) = LocationPoint(timestamp = time, lat = 0.0, lon = lon, speed = speed, accuracy = 1.0)
    @Test fun speedAndCadenceDistinguishTravelAndFastFootMotion() {
        assertEquals(SegmentType.STILL, classify(0.1, 0.0))
        assertEquals(SegmentType.WALKING, classify(1.2, 80.0))
        assertEquals(SegmentType.WALKING, classify(5.0, 160.0))
        assertEquals(SegmentType.VEHICLE, classify(10.0, 0.0))
        assertEquals(SegmentType.VEHICLE, classify(3.0, 0.0))
        assertEquals(100.0, cadence(listOf(StepInterval(start = 0, end = 60_000, steps = 100)), 15_000, 45_000), 0.0)
    }
    @Test fun aRangeClipsEdgesAndRecomputesDistanceAndDuration() {
        val points = listOf(point(1000, 0.0), point(11000, 0.001))
        val full = activityStats(buildSegments(points, emptyList(), emptyList(), TimeRange(1000, 11000)))
        val partial = activityStats(buildSegments(points, emptyList(), emptyList(), TimeRange(3500, 8500)))
        assertEquals(full.distance / 2, partial.distance, 0.001)
        assertEquals(5000L, partial.observedMs)
        assertEquals(full.averageSpeed, partial.averageSpeed, 0.001)
        assertTrue(partial.maxSpeed >= partial.averageSpeed)
    }
    @Test fun gapsAndDuplicateTimesNeverInventTravel() {
        val points = listOf(point(1000, 0.0), point(1000, 0.001), point(1_000_000, 0.1))
        assertTrue(buildSegments(points, emptyList(), emptyList(), TimeRange(1, 2_000_000)).isEmpty())
        assertEquals(0.0, activityStats(emptyList()).maxSpeed, 0.0)
    }
    @Test fun labelCorrectionsClipAndConserveDistance() {
        val points = listOf(point(1000, 0.0), point(11000, 0.001))
        val override = SegmentLabel(start = 3500, end = 8500, type = SegmentType.WALKING)
        val segments = buildSegments(points, emptyList(), listOf(override), TimeRange(1000, 11000))
        assertEquals(listOf(SegmentType.VEHICLE, SegmentType.WALKING, SegmentType.VEHICLE), segments.map { it.type })
        assertFalse(segments[1].estimated)
        assertEquals(segments.sumOf { it.distance } / 2, segments[1].distance, 0.001)
    }
    @Test fun calendarRangesRespectDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val spring = dayRange(LocalDate.parse("2026-03-08"), zone)
        val fall = dayRange(LocalDate.parse("2026-11-01"), zone)
        assertEquals(23 * 3600_000L, spring.to - spring.from)
        assertEquals(25 * 3600_000L, fall.to - fall.from)
    }
}

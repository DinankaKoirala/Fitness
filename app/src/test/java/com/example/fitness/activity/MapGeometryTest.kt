package com.example.fitness.activity

import com.example.fitness.data.*
import org.junit.Assert.*
import org.junit.Test

class MapGeometryTest {
    private fun p(time: Long, lon: Double) = LocationPoint(timestamp = time, lat = 10.0, lon = lon, speed = 2.0, accuracy = 5.0)
    @Test fun clippedGeometryUsesInterpolatedEndpoints() {
        val points = listOf(p(1000, 0.0), p(11000, 0.001))
        val segment = Segment(start = 3500, end = 8500, type = SegmentType.WALKING, distance = 50.0)
        val path = segmentCoordinates(points, segment)
        assertEquals(2, path.size)
        assertEquals(0.00025, path.first().lon, 0.00000001)
        assertEquals(0.00075, path.last().lon, 0.00000001)
        assertNull(scrubCoordinate(points, listOf(segment), 2000))
    }
    @Test fun interpolationRespectsGapsAndTheAntimeridian() {
        assertNull(interpolateCoordinate(listOf(p(1000, 0.0), p(1_000_000, 1.0)), 500_000))
        val coordinate = interpolateCoordinate(listOf(p(1000, 179.5), p(11000, -179.5)), 6000)!!
        assertEquals(180.0, kotlin.math.abs(coordinate.lon), 0.000001)
    }
}

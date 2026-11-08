package com.example.fitness.activity

import com.example.fitness.data.LocationPoint
import org.junit.Assert.*
import org.junit.Test

class LocationPolicyTest {
    private val point = LocationPoint(timestamp = 1000, lat = 27.7, lon = 85.3, speed = 1.2, accuracy = 5.0)
    @Test fun stillHasNoGpsAndVehicleTurnsSampleMoreClosely() {
        assertNull(sampling(Motion.STILL, false))
        assertTrue(sampling(Motion.WALKING, true)!!.intervalMs > sampling(Motion.WALKING, false)!!.intervalMs)
        assertTrue(sampling(Motion.VEHICLE, false, true)!!.intervalMs < sampling(Motion.VEHICLE, false)!!.intervalMs)
    }
    @Test fun noiseAndImpossibleJumpsAreRejected() {
        assertTrue(validLocation(point, null))
        assertFalse(validLocation(point.copy(accuracy = 100.0), null))
        assertFalse(validLocation(point.copy(lat = Double.NaN), null))
        assertFalse(validLocation(point.copy(speed = 200.0), null))
        assertFalse(validLocation(point.copy(timestamp = 500), point))
        assertFalse(validLocation(point.copy(timestamp = 2000, lat = 30.0), point))
        assertTrue(validLocation(point.copy(timestamp = 11000, lat = 27.7001), point))
    }
    @Test fun haversineAndBearingWrapAreStable() {
        assertEquals(0.0, distanceM(27.7, 85.3, 27.7, 85.3), 0.0)
        assertEquals(111195.0, distanceM(0.0, 0.0, 1.0, 0.0), 2.0)
        assertEquals(20f, bearingChange(350f, 10f))
    }
}

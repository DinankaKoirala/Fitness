package com.example.fitness.gym

import com.example.fitness.data.SetEntry
import org.junit.Assert.*
import org.junit.Test

class WorkoutMathTest {
    private fun set(reps: Int, weight: Double) = SetEntry(workoutId = 1, exerciseId = 1, reps = reps, weightKg = weight, performedAt = 1)
    @Test fun personalRecordsUseWeightRepsAndVolumeWithoutInventedBaselines() {
        val old = set(10, 20.0).copy(id = 1)
        assertEquals(RecordKind.FIRST, personalRecord(set(1, 1.0), emptyList()))
        assertEquals(RecordKind.WEIGHT, personalRecord(set(1, 25.0), listOf(old)))
        assertEquals(RecordKind.REPS, personalRecord(set(11, 20.0), listOf(old)))
        assertEquals(RecordKind.VOLUME, personalRecord(set(30, 10.0), listOf(old)))
        assertEquals(RecordKind.NONE, personalRecord(set(5, 10.0), listOf(old)))
        assertEquals(RecordKind.REPS, personalRecord(set(12, 0.0), listOf(set(10, 0.0).copy(id = 2))))
    }
    @Test fun invalidSetsAreRejectedAndTimersClampAtZero() {
        assertThrows(IllegalArgumentException::class.java) { validateSet(0, 20.0) }
        assertThrows(IllegalArgumentException::class.java) { validateSet(8, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { validateSet(8, -1.0) }
        validateSet(10, 0.0)
        assertEquals("0:00", timerText(-1000))
        assertEquals("1:30", timerText(90_000))
    }
}

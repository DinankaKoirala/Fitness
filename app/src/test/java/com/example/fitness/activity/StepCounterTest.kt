package com.example.fitness.activity

import com.example.fitness.data.*
import org.junit.Assert.*
import org.junit.Test

class StepCounterTest {
    private fun reading(raw: Long, date: String = "2026-11-08", boot: Int = 1, time: Long = raw + 1000) = StepReading(date, raw, boot, time)
    @Test fun firstReadingIsBaselineAndRestartContinuesFromPersistedState() {
        val first = updateSteps(null, null, reading(1000))
        assertEquals(0L, first.day.steps)
        val next = updateSteps(first.baseline, first.day, reading(1012))
        assertEquals(12L, next.day.steps)
        val restart = updateSteps(next.baseline.copy(), next.day.copy(), reading(1020))
        assertEquals(20L, restart.day.steps)
    }
    @Test fun rebootAndDriverResetNeverSubtractSteps() {
        val old = StepCounterState(date = "2026-11-08", rawCounter = 9000, bootCount = 1, eventTime = 100)
        val day = StepDay("2026-11-08", 500)
        assertEquals(508L, updateSteps(old, day, reading(8, boot = 2, time = 200)).day.steps)
        assertEquals(503L, updateSteps(old, day, reading(3, time = 200)).day.steps)
    }
    @Test fun midnightGapIsMarkedEstimatedAndDoesNotOverwriteYesterday() {
        val old = StepCounterState(date = "2026-11-08", rawCounter = 100, bootCount = 1, eventTime = 100)
        val result = updateSteps(old, null, reading(110, date = "2026-11-09", time = 200))
        assertEquals("2026-11-09", result.day.date)
        assertEquals(10L, result.day.steps)
        assertTrue(result.day.estimated)
    }
    @Test fun manualCorrectionAndExplicitRestartPreserveDailyTotals() {
        val old = StepCounterState(date = "2026-11-08", rawCounter = 100, bootCount = 1, eventTime = 100)
        val corrected = StepDay("2026-11-08", 2000)
        assertEquals(2010L, updateSteps(old, corrected, reading(110, time = 200)).day.steps)
        assertEquals(2000L, updateSteps(old, corrected, reading(500, time = 200), reset = true).day.steps)
        assertEquals(old, updateSteps(old, corrected, reading(90, time = 50)).baseline)
    }
}

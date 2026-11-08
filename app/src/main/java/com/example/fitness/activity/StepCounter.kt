package com.example.fitness.activity

import com.example.fitness.data.StepCounterState
import com.example.fitness.data.StepDay

data class StepReading(val date: String, val raw: Long, val boot: Int, val time: Long)
data class StepUpdate(val day: StepDay, val baseline: StepCounterState)

fun updateSteps(previous: StepCounterState?, day: StepDay?, reading: StepReading, reset: Boolean = false): StepUpdate {
    require(reading.raw >= 0)
    val base = day ?: StepDay(reading.date, 0)
    if (previous != null && !reset && previous.bootCount == reading.boot && reading.time < previous.eventTime) {
        return StepUpdate(base, previous)
    }
    val delta = when {
        previous == null || reset -> 0L
        previous.bootCount != reading.boot || reading.raw < previous.rawCounter -> reading.raw
        else -> reading.raw - previous.rawCounter
    }
    val uncertainSplit = previous != null && previous.date != reading.date && delta > 0
    val now = reading.time
    return StepUpdate(base.copy(steps = Math.addExact(base.steps, delta), updatedAt = now,
        estimated = base.estimated || uncertainSplit),
        StepCounterState(date = reading.date, rawCounter = reading.raw, bootCount = reading.boot,
            eventTime = now, createdAt = previous?.createdAt ?: now, updatedAt = now))
}

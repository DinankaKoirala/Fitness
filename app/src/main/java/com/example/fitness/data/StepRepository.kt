package com.example.fitness.data

import androidx.room.withTransaction
import com.example.fitness.activity.*
import java.time.LocalDate

class StepRepository(private val database: FitnessDatabase) {
    private val dao = database.stepDao()
    fun observeDay(date: String) = dao.observeDay(date)
    suspend fun record(reading: StepReading, reset: Boolean = false) = database.withTransaction {
        LocalDate.parse(reading.date)
        val previous = dao.baseline()
        val result = updateSteps(previous, dao.day(reading.date), reading, reset)
        if (previous != null && !reset && previous.bootCount == reading.boot &&
            reading.time > previous.eventTime && reading.raw >= previous.rawCounter) {
            val delta = reading.raw - previous.rawCounter
            if (delta > 0) dao.insertInterval(StepInterval(start = previous.eventTime, end = reading.time, steps = delta))
        }
        dao.saveDay(result.day)
        dao.saveBaseline(result.baseline)
    }
    suspend fun correct(date: String, steps: Long) = database.withTransaction {
        LocalDate.parse(date)
        require(steps in 0..1_000_000) { "Enter steps between 0 and 1,000,000." }
        val old = dao.day(date)
        dao.saveDay(StepDay(date, steps, old?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis(), estimated = false))
    }
}

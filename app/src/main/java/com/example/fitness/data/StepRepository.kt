package com.example.fitness.data

import androidx.room.withTransaction
import com.example.fitness.activity.*
import java.time.LocalDate

class StepRepository(private val database: FitnessDatabase) {
    private val dao = database.stepDao()
    fun observeDay(date: String) = dao.observeDay(date)
    suspend fun record(reading: StepReading, reset: Boolean = false) = database.withTransaction {
        LocalDate.parse(reading.date)
        val result = updateSteps(dao.baseline(), dao.day(reading.date), reading, reset)
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

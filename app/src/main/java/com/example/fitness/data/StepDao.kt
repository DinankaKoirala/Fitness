package com.example.fitness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM StepDay WHERE date = :date") fun observeDay(date: String): Flow<StepDay?>
    @Query("SELECT * FROM StepDay WHERE date = :date") suspend fun day(date: String): StepDay?
    @Query("SELECT * FROM StepCounterState WHERE id = 1") suspend fun baseline(): StepCounterState?
    @Upsert suspend fun saveDay(day: StepDay)
    @Upsert suspend fun saveBaseline(state: StepCounterState)
}

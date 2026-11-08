package com.example.fitness.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface TrendsDao {
    @Query("SELECT * FROM MealEntry WHERE date >= :from AND date <= :to ORDER BY date, createdAt") fun meals(from: String, to: String): Flow<List<MealEntry>>
    @Query("SELECT * FROM StepDay WHERE date >= :from AND date <= :to ORDER BY date") fun steps(from: String, to: String): Flow<List<StepDay>>
    @Query("SELECT * FROM WeightEntry WHERE date >= :from AND date <= :to ORDER BY date, createdAt, id") fun weights(from: String, to: String): Flow<List<WeightEntry>>
    @Query("SELECT * FROM SetEntry WHERE performedAt < :to ORDER BY performedAt, id") fun sets(to: Long): Flow<List<SetEntry>>
    @Query("SELECT * FROM Exercise ORDER BY name COLLATE NOCASE") fun exercises(): Flow<List<Exercise>>
    @Insert suspend fun addWeight(weight: WeightEntry)
}

package com.example.fitness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert suspend fun insert(points: List<LocationPoint>)
    @Query("SELECT * FROM LocationPoint WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp, id")
    fun observe(from: Long, to: Long): Flow<List<LocationPoint>>
    @Query("SELECT * FROM LocationPoint WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp, id")
    suspend fun points(from: Long, to: Long): List<LocationPoint>
}

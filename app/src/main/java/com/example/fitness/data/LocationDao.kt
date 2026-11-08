package com.example.fitness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM LocationPoint WHERE (timestamp >= :from AND timestamp < :to) OR id = (SELECT id FROM LocationPoint WHERE timestamp < :from ORDER BY timestamp DESC, id DESC LIMIT 1) OR id = (SELECT id FROM LocationPoint WHERE timestamp >= :to ORDER BY timestamp, id LIMIT 1) ORDER BY timestamp, id")
    fun observeWithBoundaries(from: Long, to: Long): Flow<List<LocationPoint>>
    @Insert suspend fun insert(points: List<LocationPoint>)
    @Query("SELECT * FROM LocationPoint WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp, id")
    fun observe(from: Long, to: Long): Flow<List<LocationPoint>>
    @Query("SELECT * FROM LocationPoint WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp, id")
    suspend fun points(from: Long, to: Long): List<LocationPoint>
}

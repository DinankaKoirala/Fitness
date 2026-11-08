package com.example.fitness.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface AiQueueDao {
    @Query("SELECT * FROM QueuedAiRequest ORDER BY createdAt") fun observe(): Flow<List<QueuedAiRequest>>
    @Query("SELECT * FROM QueuedAiRequest WHERE id = :id") suspend fun request(id: Long): QueuedAiRequest?
    @Insert suspend fun insert(request: QueuedAiRequest): Long
    @Update suspend fun update(request: QueuedAiRequest)
    @Query("DELETE FROM QueuedAiRequest WHERE id = :id") suspend fun delete(id: Long)
}

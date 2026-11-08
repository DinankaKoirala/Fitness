package com.example.fitness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {
    @Query("SELECT * FROM StepInterval WHERE start < :to AND end > :from ORDER BY start")
    fun observeSteps(from: Long, to: Long): Flow<List<StepInterval>>
    @Query("SELECT * FROM SegmentLabel WHERE start < :to AND end > :from ORDER BY updatedAt, id")
    fun observeLabels(from: Long, to: Long): Flow<List<SegmentLabel>>
    @Query("SELECT * FROM Segment WHERE start < :to AND end > :from ORDER BY start")
    suspend fun segments(from: Long, to: Long): List<Segment>
    @Insert suspend fun label(label: SegmentLabel)
    @Query("DELETE FROM Segment WHERE start < :to AND end > :from")
    suspend fun deleteRange(from: Long, to: Long)
    @Insert suspend fun insert(segments: List<Segment>)
}

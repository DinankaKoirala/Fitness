package com.example.fitness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {
    @Query("SELECT * FROM Exercise ORDER BY name COLLATE NOCASE") fun observeExercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM Routine ORDER BY name COLLATE NOCASE") fun observeRoutines(): Flow<List<Routine>>
    @Query("SELECT * FROM RoutineExercise ORDER BY routineId, position") fun observeRoutineExercises(): Flow<List<RoutineExercise>>
    @Query("SELECT * FROM Workout ORDER BY startedAt DESC, id DESC") fun observeWorkouts(): Flow<List<Workout>>
    @Query("SELECT * FROM WorkoutExercise ORDER BY workoutId, position") fun observeWorkoutExercises(): Flow<List<WorkoutExercise>>
    @Query("SELECT * FROM SetEntry ORDER BY performedAt, id") fun observeSets(): Flow<List<SetEntry>>
    @Query("SELECT COUNT(*) FROM Exercise WHERE custom = 0") suspend fun libraryCount(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seed(exercises: List<Exercise>)
    @Insert suspend fun insertExercise(exercise: Exercise): Long
    @Query("SELECT * FROM Exercise WHERE id = :id") suspend fun exercise(id: Long): Exercise?
    @Query("SELECT * FROM Routine WHERE id = :id") suspend fun routine(id: Long): Routine?
    @Insert suspend fun insertRoutine(routine: Routine): Long
    @Update suspend fun updateRoutine(routine: Routine)
    @Query("DELETE FROM RoutineExercise WHERE routineId = :id") suspend fun clearRoutine(id: Long)
    @Insert suspend fun insertRoutineExercises(exercises: List<RoutineExercise>)
    @Query("SELECT * FROM RoutineExercise WHERE routineId = :id ORDER BY position") suspend fun routineExercises(id: Long): List<RoutineExercise>
    @Query("SELECT * FROM Workout WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun active(): Workout?
    @Query("SELECT * FROM Workout WHERE id = :id") suspend fun workout(id: Long): Workout?
    @Insert suspend fun insertWorkout(workout: Workout): Long
    @Update suspend fun updateWorkout(workout: Workout)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWorkoutExercises(exercises: List<WorkoutExercise>)
    @Query("SELECT * FROM WorkoutExercise WHERE workoutId = :id ORDER BY position") suspend fun workoutExercises(id: Long): List<WorkoutExercise>
    @Insert suspend fun insertSet(set: SetEntry): Long
    @Update suspend fun updateSet(set: SetEntry)
    @Query("SELECT * FROM SetEntry WHERE id = :id") suspend fun set(id: Long): SetEntry?
    @Query("DELETE FROM SetEntry WHERE id = :id") suspend fun deleteSet(id: Long)
}

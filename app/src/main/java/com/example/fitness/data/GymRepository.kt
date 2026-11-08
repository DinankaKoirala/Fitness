package com.example.fitness.data

import android.content.Context
import androidx.room.withTransaction
import com.example.fitness.gym.validateSet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray

data class GymState(val loading: Boolean = true, val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(), val routineExercises: List<RoutineExercise> = emptyList(),
    val workouts: List<Workout> = emptyList(), val workoutExercises: List<WorkoutExercise> = emptyList(),
    val sets: List<SetEntry> = emptyList(), val error: String? = null)

class GymRepository(private val database: FitnessDatabase, private val context: Context) {
    private val dao = database.gymDao()
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (dao.libraryCount() == 0) {
                val rows = context.assets.open("exercises/library.json").bufferedReader().use { JSONArray(it.readText()) }
                val exercises = (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
                    Exercise(id = it.getLong("id"), name = it.getString("name"), muscleGroup = it.getString("muscleGroup"), equipment = it.getString("equipment"), custom = false)
                } }
                dao.seed(exercises)
            }
        }
    }
    fun observe(): Flow<GymState> = flow {
        ensureSeeded()
        val library = combine(dao.observeExercises(), dao.observeRoutines(), dao.observeRoutineExercises()) { a, b, c -> Triple(a, b, c) }
        val log = combine(dao.observeWorkouts(), dao.observeWorkoutExercises(), dao.observeSets()) { a, b, c -> Triple(a, b, c) }
        emitAll(combine(library, log) { (exercises, routines, routineExercises), (workouts, workoutExercises, sets) ->
            GymState(false, exercises, routines, routineExercises, workouts, workoutExercises, sets)
        })
    }.catch { emit(GymState(loading = false, error = "Could not load the gym log. Try again.")) }

    suspend fun createExercise(name: String, muscle: String, equipment: String): Long {
        require(listOf(name, muscle, equipment).all { it.isNotBlank() && it.length <= 200 }) { "Enter a name, muscle group and equipment (up to 200 characters each)." }
        return dao.insertExercise(Exercise(name = name.trim(), muscleGroup = muscle.trim(), equipment = equipment.trim(), custom = true))
    }
    suspend fun saveRoutine(id: Long?, name: String, exerciseIds: List<Long>): Long = database.withTransaction {
        require(name.isNotBlank() && name.length <= 200) { "Enter a routine name up to 200 characters." }
        require(exerciseIds.isNotEmpty() && exerciseIds.distinct().size == exerciseIds.size) { "Choose at least one exercise, without duplicates." }
        exerciseIds.forEach { requireNotNull(dao.exercise(it)) }
        val now = System.currentTimeMillis()
        val old = id?.let { requireNotNull(dao.routine(it)) }
        val routineId = old?.id ?: dao.insertRoutine(Routine(name = name.trim()))
        if (old != null) dao.updateRoutine(old.copy(name = name.trim(), updatedAt = now))
        dao.clearRoutine(routineId)
        dao.insertRoutineExercises(exerciseIds.mapIndexed { position, exercise -> RoutineExercise(routineId, position, exercise) })
        routineId
    }
    suspend fun start(routineId: Long?): Long = database.withTransaction {
        check(dao.active() == null) { "Finish your active workout first." }
        val routine = routineId?.let { requireNotNull(dao.routine(it)) }
        val id = dao.insertWorkout(Workout(routineId = routineId, startedAt = System.currentTimeMillis(), name = routine?.name ?: "Free workout"))
        if (routine != null) dao.insertWorkoutExercises(dao.routineExercises(routine.id).map { WorkoutExercise(id, it.exerciseId, it.position) })
        id
    }
    suspend fun addExercise(workoutId: Long, exerciseId: Long) = database.withTransaction {
        requireNotNull(dao.workout(workoutId)).also { check(it.endedAt == null) }
        requireNotNull(dao.exercise(exerciseId))
        val position = (dao.workoutExercises(workoutId).maxOfOrNull { it.position } ?: -1) + 1
        dao.insertWorkoutExercises(listOf(WorkoutExercise(workoutId, exerciseId, position)))
    }
    suspend fun saveSet(workoutId: Long, exerciseId: Long, reps: Int, weight: Double, id: Long? = null, restSeconds: Int = 90): Long = database.withTransaction {
        validateSet(reps, weight)
        require(restSeconds in 0..3600)
        val workout = requireNotNull(dao.workout(workoutId))
        check(dao.workoutExercises(workoutId).any { it.exerciseId == exerciseId })
        val now = System.currentTimeMillis()
        val setId = if (id == null) {
            check(workout.endedAt == null)
            dao.insertSet(SetEntry(workoutId = workoutId, exerciseId = exerciseId, reps = reps, weightKg = weight, performedAt = now))
        } else {
            val old = requireNotNull(dao.set(id))
            require(old.workoutId == workoutId && old.exerciseId == exerciseId)
            dao.updateSet(old.copy(reps = reps, weightKg = weight, updatedAt = now)); id
        }
        if (id == null) dao.updateWorkout(workout.copy(restUntil = if (restSeconds > 0) now + restSeconds * 1000L else null, updatedAt = now))
        setId
    }
    suspend fun finish(id: Long) = database.withTransaction {
        val workout = requireNotNull(dao.workout(id))
        check(workout.endedAt == null)
        val now = System.currentTimeMillis()
        dao.updateWorkout(workout.copy(endedAt = maxOf(now, workout.startedAt), restUntil = null, updatedAt = now))
    }
    suspend fun cancelRest(id: Long) = database.withTransaction {
        val workout = requireNotNull(dao.workout(id))
        dao.updateWorkout(workout.copy(restUntil = null, updatedAt = System.currentTimeMillis()))
    }
    suspend fun deleteSet(id: Long) { dao.deleteSet(id) }
}

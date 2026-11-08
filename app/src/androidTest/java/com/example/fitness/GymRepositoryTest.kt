package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymRepositoryTest {
    @Test fun routineWorkoutAndRestSurviveReopenWithoutChangingHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "gym-repository.db"
        context.deleteDatabase(name)
        var db = FitnessDatabase.create(context, name)
        try {
            var repo = GymRepository(db, context)
            repo.ensureSeeded()
            val count = db.gymDao().libraryCount()
            assertEquals(876, count)
            repo.ensureSeeded(); assertEquals(count, db.gymDao().libraryCount())
            val custom = repo.createExercise("Test press", "Chest", "Dumbbell")
            assertTrue(db.gymDao().exercise(custom)!!.custom)
            val routine = repo.saveRoutine(null, "Push", listOf(custom))
            val workout = repo.start(routine)
            val another = repo.createExercise("Test squat", "Legs", "Barbell")
            repo.saveRoutine(routine, "Renamed routine", listOf(another))
            assertEquals(custom, db.gymDao().workoutExercises(workout).single().exerciseId)
            assertEquals("Push", db.gymDao().workout(workout)!!.name)
            val set = repo.saveSet(workout, custom, 8, 20.0, restSeconds = 90)
            assertTrue(db.gymDao().workout(workout)!!.restUntil!! > System.currentTimeMillis())
            db.close(); db = FitnessDatabase.create(context, name); repo = GymRepository(db, context)
            assertEquals(8, db.gymDao().set(set)!!.reps)
            assertNotNull(db.gymDao().active())
            repo.finish(workout)
            assertNotNull(db.gymDao().workout(workout)!!.endedAt)
            repo.saveSet(workout, custom, 10, 22.5, id = set)
            assertEquals(22.5, db.gymDao().set(set)!!.weightKg, 0.0)
            assertEquals(225.0, repo.observe().first().sets.single().let { it.weightKg * it.reps }, 0.0)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

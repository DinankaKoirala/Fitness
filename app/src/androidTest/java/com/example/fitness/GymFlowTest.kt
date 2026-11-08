package com.example.fitness

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FitnessApplication
    private var scenario: ActivityScenario<MainActivity>? = null
    private suspend fun clearGym() = app.database.withTransaction {
        val sql = app.database.openHelper.writableDatabase
        listOf("SetEntry", "WorkoutExercise", "RoutineExercise", "Workout", "Routine", "Exercise").forEach { sql.execSQL("DELETE FROM $it") }
    }
    @Before fun prepare() = runBlocking {
        clearGym()
        app.profiles.saveProfile(ProfileInput(180.0, 80.0, 30, EquationSex.MALE, ActivityLevel.MODERATE))
    }
    @After fun clean() {
        scenario?.close()
        runBlocking { clearGym(); app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM UserProfile") } }
    }
    private fun waitTag(tag: String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun field(tag: String, value: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    @Test fun createRoutineLogWorkoutAndReviewAfterRecreation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("foodGoal")
        compose.onNodeWithContentDescription("Gym tab").performClick()
        waitTag("newCustomExercise")
        click("newCustomExercise")
        field("exerciseName", "Test press")
        field("exerciseMuscle", "Chest")
        field("exerciseEquipment", "Dumbbell")
        click("saveExercise")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("saveExercise").fetchSemanticsNodes().isEmpty() }
        val exercise = runBlocking { app.gym.observe().first().exercises.first { it.custom }.id }
        click("newRoutine")
        field("routineName", "Test push day")
        click("routineAddExercise")
        field("exerciseSearch", "Test press")
        compose.onNodeWithTag("chooseExercise_$exercise").performClick()
        click("saveRoutine")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("saveRoutine").fetchSemanticsNodes().isEmpty() }
        val routine = runBlocking { app.gym.observe().first().routines.single().id }
        click("startRoutine_$routine")
        waitTag("workoutTimer")
        click("logSet_$exercise")
        field("setReps", "8"); field("setWeight", "20")
        click("saveSet")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("saveSet").fetchSemanticsNodes().isEmpty() }
        val set = runBlocking { app.database.gymDao().observeSets().first().single().id }
        waitTag("loggedSet_$set")
        scenario!!.recreate()
        waitTag("restTimer")
        compose.onNodeWithTag("loggedSet_$set").assertTextContains("8 × 20 kg", substring = true)
        click("finishWorkout"); compose.onNodeWithTag("confirmFinishWorkout").performClick()
        compose.waitUntil(5000) { runBlocking { app.database.gymDao().active() == null } }
        compose.onNodeWithText("Back to gym").performScrollTo().performClick()
        val workout = runBlocking { app.gym.observe().first().workouts.single().id }
        click("reviewWorkout_$workout")
        compose.onNodeWithTag("loggedSet_$set").performScrollTo().assertExists()
    }
}

package com.example.fitness

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.*
import com.example.fitness.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FitnessApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun reset() = runBlocking(Dispatchers.IO) {
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM UserProfile")
        GoalSettings(app.goalDataStore).setOverride(null)
    }

    @After fun cleanUp() {
        scenario?.close()
        runBlocking(Dispatchers.IO) {
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM UserProfile")
            GoalSettings(app.goalDataStore).setOverride(null)
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun fillProfile() {
        compose.onNodeWithTag("height").performScrollTo().performTextInput("180")
        compose.onNodeWithTag("weight").performScrollTo().performTextInput("80")
        compose.onNodeWithTag("age").performScrollTo().performTextInput("30")
        compose.onNodeWithText("Male").performScrollTo().performClick()
        compose.onNodeWithTag("activityPicker").performScrollTo().performClick()
        compose.onNodeWithTag("activity_MODERATE").performClick()
    }

    @Test fun onboardingOverrideEditingRestorationAndNavigation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("height")
        compose.onNodeWithTag("saveProfile").performScrollTo().performClick()
        compose.onNodeWithTag("profileMessage").performScrollTo().assertTextContains("height", substring = true)
        fillProfile()
        compose.onNodeWithTag("estimate").assertTextEquals("2759 kcal / day")
        scenario!!.recreate()
        compose.onNodeWithTag("height").assertTextContains("180")
        compose.onNodeWithTag("saveProfile").performScrollTo().performClick()
        waitForTag("foodGoal")
        compose.onNodeWithTag("foodGoal").assertTextEquals("2759 kcal / day")
        compose.onNodeWithTag("goalSource").assertTextContains("Estimated", substring = true)

        listOf("Activity", "Gym", "Profile", "Food").forEach { tab ->
            compose.onNodeWithContentDescription("$tab tab").performClick().assertIsSelected()
        }
        compose.onNodeWithContentDescription("Gym tab").performClick()
        scenario!!.recreate()
        compose.onNodeWithContentDescription("Gym tab").assertIsSelected()
        compose.onNodeWithContentDescription("Profile tab").performClick()
        compose.onNodeWithTag("manualGoal").performScrollTo().performTextInput("0")
        compose.onNodeWithTag("saveManualGoal").performScrollTo().performClick()
        compose.onNodeWithTag("goalMessage").performScrollTo().assertTextContains("positive", substring = true)
        compose.onNodeWithTag("manualGoal").performScrollTo().performTextReplacement("2200")
        compose.onNodeWithTag("saveManualGoal").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { GoalSettings(app.goalDataStore).override.first() == 2200 } }
        compose.onNodeWithContentDescription("Food tab").performClick()
        compose.onNodeWithTag("foodGoal").assertTextEquals("2200 kcal / day")
        compose.onNodeWithTag("goalSource").assertTextContains("Manual", substring = true)
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("foodGoal")
        compose.onNodeWithTag("foodGoal").assertTextEquals("2200 kcal / day")
        compose.onNodeWithContentDescription("Profile tab").performClick()
        compose.onNodeWithTag("weight").performScrollTo().performTextReplacement("81")
        compose.onNodeWithTag("saveProfile").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { app.database.profileDao().getProfile()?.weightKg == 81.0 } }
        compose.onNodeWithTag("activeGoal").performScrollTo().assertTextContains("2200", substring = true)
        compose.onNodeWithTag("restoreEstimate").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { GoalSettings(app.goalDataStore).override.first() == null } }
        compose.onNodeWithContentDescription("Food tab").performClick()
        compose.onNodeWithTag("foodGoal").assertTextEquals("2775 kcal / day")
        compose.onNodeWithTag("goalSource").assertTextContains("Estimated", substring = true)
    }

    @Test fun dataStoreOverrideSurvivesClosingAndReopening() = runBlocking {
        val file = java.io.File(app.cacheDir, "goal-test-${System.nanoTime()}.preferences_pb")
        var job = SupervisorJob()
        fun open(scope: CoroutineScope) = GoalSettings(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) { file }
        )
        try {
            var settings = open(CoroutineScope(job + Dispatchers.IO))
            assertNull(settings.override.first())
            settings.setOverride(2200)
            job.cancelAndJoin()
            job = SupervisorJob()
            settings = open(CoroutineScope(job + Dispatchers.IO))
            assertEquals(2200, settings.override.first())
            settings.setOverride(null)
            assertNull(settings.override.first())
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun repositoryReloadsPersistentProfileAndSettings() = runBlocking {
        val input = ProfileInput(165.0, 60.0, 30, EquationSex.FEMALE, ActivityLevel.SEDENTARY)
        app.profiles.saveProfile(input)
        app.profiles.setManualGoal(1900)
        val original = app.database.profileDao().getProfile()!!
        app.profiles.saveProfile(input.copy(weightKg = 61.0))
        val database = FitnessDatabase.create(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val fresh = ProfileRepository(database.profileDao(), GoalSettings(app.goalDataStore), scope)
            val state = withTimeout(5_000) { fresh.state.first { !it.loading } }
            assertNull(state.error)
            assertEquals(1900, state.goal)
            assertEquals(1596, state.profile!!.calorieGoal)
            assertEquals(original.createdAt, state.profile.createdAt)
            assertEquals(61.0, state.profile.weightKg, 0.0)
            fresh.setManualGoal(null)
            val estimated = withTimeout(5_000) { fresh.state.first { !it.loading && it.manualGoal == null } }
            assertEquals(1596, estimated.goal)
        } finally { scope.cancel(); database.close() }
    }
}

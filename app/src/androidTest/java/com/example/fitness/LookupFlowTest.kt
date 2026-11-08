package com.example.fitness

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.food.lookup.*
import com.example.fitness.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LookupFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FitnessApplication
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun prepare() = runBlocking {
        app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM MealEntry"); app.database.openHelper.writableDatabase.execSQL("DELETE FROM Food") }
        app.profiles.saveProfile(ProfileInput(180.0, 80.0, 30, EquationSex.MALE, ActivityLevel.MODERATE))
        LookupSettings(app).mode(LookupMode.DATABASE_ONLY)
    }
    @After fun clean() {
        scenario?.close()
        runBlocking {
            app.database.withTransaction { listOf("MealEntry", "Food", "UserProfile").forEach { app.database.openHelper.writableDatabase.execSQL("DELETE FROM $it") } }
            LookupSettings(app).mode(LookupMode.DATABASE_FIRST)
        }
    }
    private fun waitTag(tag: String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    @Test fun offlineSwissLookupRequiresFoodAndAmountConfirmation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("dayCalories")
        click("add_BREAKFAST"); click("openLookup")
        compose.onNodeWithTag("lookupQuery").performScrollTo().performTextInput("Apple, fresh")
        click("searchFoods"); waitTag("lookupResult_0"); click("lookupResult_0")
        compose.onNodeWithTag("calories").performScrollTo().assertTextContains("52.0")
        click("saveFood"); waitTag("saveEntry"); click("saveEntry")
        waitTag("dayCalories")
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextContains("52 /", substring = true)
    }
}

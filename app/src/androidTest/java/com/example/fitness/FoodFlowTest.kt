package com.example.fitness

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.*
import com.example.fitness.profile.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FitnessApplication
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun prepare() = runBlocking(Dispatchers.IO) {
        app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM MealEntry") }
        app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM Food") }
        app.profiles.saveProfile(ProfileInput(180.0, 80.0, 30, EquationSex.MALE, ActivityLevel.MODERATE))
        app.profiles.setManualGoal(2200)
    }
    @After fun clean() {
        scenario?.close()
        runBlocking(Dispatchers.IO) {
            app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM MealEntry") }
            app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM Food") }
            app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM UserProfile") }
            app.profiles.setManualGoal(null)
        }
    }
    private fun waitTag(tag: String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun field(tag: String, value: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }

    @Test fun logFullDayWithSavedFoodsEditDeleteAndSwitchDates() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("dayCalories")
        click("add_BREAKFAST")
        click("createFood")
        field("foodName", "Test oats")
        field("servingAmount", "100")
        field("calories", "400")
        field("protein", "20")
        field("fat", "10")
        field("carbs", "60")
        field("fiber", "8")
        click("saveFood")
        waitTag("entryAmount")
        field("entryAmount", "50")
        click("unit_g")
        click("saveEntry")
        waitTag("dayCalories")
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextEquals("200 / 2200 kcal")
        val id = runBlocking { app.foods.observeDay(LocalDate.now().toString()).first().foods.single().id }
        listOf("LUNCH", "DINNER", "SNACKS").forEach { meal ->
            click("add_$meal")
            click("recentFoods")
            click("chooseFood_$id")
            click("saveEntry")
            waitTag("dayCalories")
        }
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextEquals("1400 / 2200 kcal")
        compose.onNodeWithTag("micro_FIBER").performScrollTo().assertTextEquals("28 g")
        compose.onNodeWithTag("micro_SODIUM").performScrollTo().assertTextEquals("Unknown")
        val entries = runBlocking { app.foods.observeDay(LocalDate.now().toString()).first().entries }
        val breakfast = entries.first { it.mealType == MealType.BREAKFAST }
        click("editEntry_${breakfast.id}")
        field("entryAmount", "2")
        click("unit_serving")
        click("saveEntry")
        waitTag("dayCalories")
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextEquals("2000 / 2200 kcal")
        val snack = entries.first { it.mealType == MealType.SNACKS }
        click("deleteEntry_${snack.id}")
        compose.onNodeWithTag("confirmDeleteEntry").performClick()
        compose.waitUntil(5000) { runBlocking { app.database.foodDao().entry(snack.id) == null } }
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextEquals("1600 / 2200 kcal")
        compose.onNodeWithContentDescription("Next week").performScrollTo().performClick()
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextEquals("0 / 2200 kcal")
        scenario!!.recreate()
        waitTag("dayCalories")
        compose.onNodeWithTag("day_${LocalDate.now().plusWeeks(1)}").assertIsSelected()
        click("today")
        compose.onNodeWithTag("dayCalories").performScrollTo().assertTextEquals("1600 / 2200 kcal")
        click("monthPicker")
        compose.onNodeWithText("Cancel").performClick()
    }
}

package com.example.fitness
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.activity.*
import com.example.fitness.data.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class TrendsRepositoryTest {
    @Test fun trendsQueriesIncludeOnlySelectedDaysAndTimestampedEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "trends-data.db"; context.deleteDatabase(name)
        val db = FitnessDatabase.create(context, name)
        try {
            val foods = FoodRepository(db)
            val food = foods.saveFood(Food(name = "Test food", servingAmount = 1.0, servingUnit = "serving", nutrients = Nutrients(500.0)))
            foods.addEntry(food, "2026-01-01", MealType.BREAKFAST, 1.0, "serving")
            foods.addEntry(food, "2026-01-02", MealType.LUNCH, 1.0, "serving")
            foods.addEntry(food, "2026-01-03", MealType.DINNER, 1.0, "serving")
            val repo = TrendsRepository(db)
            repo.logWeight("2026-01-01", 70.0); repo.logWeight("2026-01-02", 71.0)
            StepRepository(db).correct("2026-01-01", 1234)
            val range = TimeRange(dayRange(LocalDate.parse("2026-01-01")).from, dayRange(LocalDate.parse("2026-01-02")).to)
            val state = repo.observe(range).first()
            assertEquals(2, state.meals.size); assertEquals(2, state.weights.size)
            assertEquals(1234L, state.steps.single().steps)
            assertTrue(state.sets.isEmpty()); assertTrue(state.segments.isEmpty()); assertNull(state.error)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

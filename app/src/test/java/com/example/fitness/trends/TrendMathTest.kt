package com.example.fitness.trends
import com.example.fitness.data.*
import com.example.fitness.food.*
import org.junit.Assert.*
import org.junit.Test
class TrendMathTest {
    @Test fun weeklyAveragesUseCompleteLoggedDaysOnly() {
        val food = Food(name = "Test", servingAmount = 1.0, servingUnit = "serving", nutrients = Nutrients(fiberG = 8.0))
        fun meal(date: String, snapshot: FoodSnapshot) = MealEntry(foodId = 1, date = date, mealType = MealType.BREAKFAST, amount = 1.0, unit = "serving", snapshot = snapshot)
        val rows = listOf(meal("2026-01-01", food.snapshot()), meal("2026-01-02", food.snapshot().copy(nutrients = Nutrients())))
        val average = weeklyMicros(rows, Micro.FIBER).single()
        assertEquals(8.0, average.amount!!, 0.0)
        assertEquals(1, average.completeDays)
        assertEquals(2, average.loggedDays)
        assertNull(weeklyMicros(rows, Micro.CALCIUM).single().amount)
        assertTrue(weeklyMicros(emptyList(), Micro.FIBER).isEmpty())
    }
    @Test fun emptySinglePointAndOutOfBoundsChartCoordinatesRemainFinite() {
        assertEquals(0.5f, chartX(0, 0)); assertEquals(0.5f, chartX(0, 1))
        assertEquals(0f, chartX(0, 90)); assertEquals(1f, chartX(89, 90))
        assertEquals(1f, chartY(0.0, 0.0)); assertEquals(1f, chartY(Double.NaN, 1.0))
        assertEquals(0f, chartY(200.0, 100.0)); assertEquals(0.5f, chartY(50.0, 100.0))
    }
}

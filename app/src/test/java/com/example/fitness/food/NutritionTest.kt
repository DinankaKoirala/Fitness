package com.example.fitness.food

import com.example.fitness.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    private val food = Food(name = "Oats", servingAmount = 100.0, servingUnit = "g",
        nutrients = Nutrients(400.0, 20.0, 10.0, 60.0, fiberG = 8.0, sodiumMg = 0.0))
    private fun entry(amount: Double, unit: String, snapshot: FoodSnapshot = food.snapshot()) =
        MealEntry(foodId = 1, date = "2026-11-08", mealType = MealType.BREAKFAST, amount = amount, unit = unit, snapshot = snapshot)

    @Test fun portionsAndMatchingUnitsScaleAllNutrients() {
        val half = food.snapshot().scaled(50.0, "g")
        assertEquals(200.0, half.caloriesKcal, 0.0)
        assertEquals(10.0, half.proteinG, 0.0)
        assertEquals(4.0, half.fiberG!!, 0.0)
        assertNull(half.calciumMg)
        assertEquals(800.0, food.snapshot().scaled(2.0, "serving").caloriesKcal, 0.0)
        assertEquals(200.0, food.copy(servingUnit = "ml").snapshot().scaled(50.0, "ml").caloriesKcal, 0.0)
        assertThrows(IllegalArgumentException::class.java) { food.snapshot().scaled(50.0, "ml") }
    }

    @Test fun totalsKeepUnknownAndMeasuredZeroDistinct() {
        val unknown = food.snapshot().copy(nutrients = food.nutrients.copy(fiberG = null))
        val total = nutritionTotals(listOf(entry(50.0, "g"), entry(1.0, "serving", unknown)))
        assertEquals(600.0, total.calories, 0.0)
        assertEquals(MicroTotal(4.0, 1, 2), total.micros[Micro.FIBER])
        assertEquals(MicroTotal(0.0, 2, 2), total.micros[Micro.SODIUM])
        assertEquals(MicroTotal(0.0, 0, 2), total.micros[Micro.CALCIUM])
        assertEquals(0.0, nutritionTotals(emptyList()).calories, 0.0)
        assertEquals(MicroTotal(0.0, 0, 0), nutritionTotals(emptyList()).micros[Micro.IRON])
    }

    @Test fun overGoalBarsFillAndNeverOverflow() {
        assertEquals(1f, progressFraction(3000.0, 2000.0))
        assertEquals(0.5f, progressFraction(1000.0, 2000.0))
        assertEquals(0f, progressFraction(-1.0, 2000.0))
        assertEquals(0f, progressFraction(1000.0, 0.0))
        assertEquals(0f, progressFraction(Double.NaN, 2000.0))
    }

    @Test fun pickerDatesUseUtcCalendarDatesAcrossBoundaries() {
        listOf("2024-02-29", "2026-12-31", "2027-01-01").forEach {
            val date = LocalDate.parse(it)
            assertEquals(date, pickerDate(pickerMillis(date)))
        }
    }

    @Test fun foodValidationRejectsInvalidNutritionAndAmounts() {
        listOf("", "-1", "NaN", "Infinity", "1e30").forEach {
            assertThrows(IllegalArgumentException::class.java) { foodNumber(it, "Nutrient") }
        }
        assertEquals(0.5, foodNumber("0,5", "Amount", true), 0.0)
        assertThrows(IllegalArgumentException::class.java) { foodNumber("0", "Amount", true) }
        assertThrows(IllegalArgumentException::class.java) { validateFood(food.copy(name = " ")) }
        assertThrows(IllegalArgumentException::class.java) { validateFood(food.copy(servingUnit = "serving", servingAmount = 2.0)) }
        assertThrows(IllegalArgumentException::class.java) { validateFood(food.copy(nutrients = food.nutrients.copy(ironMg = -1.0))) }
    }
}

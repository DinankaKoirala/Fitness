package com.example.fitness.profile

import org.junit.Assert.*
import org.junit.Test

class CalorieGoalTest {
    private val male = ProfileInput(180.0, 80.0, 30, EquationSex.MALE, ActivityLevel.MODERATE)

    @Test fun publishedEquationAndActivityFactor() {
        assertEquals(1780.0, CalorieGoal.bmr(male), 0.0001)
        assertEquals(2759, CalorieGoal.maintenance(male))
        val female = ProfileInput(165.0, 60.0, 30, EquationSex.FEMALE, ActivityLevel.SEDENTARY)
        assertEquals(1320.25, CalorieGoal.bmr(female), 0.0001)
        assertEquals(1584, CalorieGoal.maintenance(female))
    }

    @Test fun roundsOnlyAfterApplyingFactor() {
        val input = ProfileInput(165.0, 60.0, 30, EquationSex.FEMALE, ActivityLevel.LIGHT)
        assertEquals(1815, CalorieGoal.maintenance(input))
        assertEquals(listOf(2136, 2448, 2759, 3071, 3382), ActivityLevel.entries.map {
            CalorieGoal.maintenance(male.copy(activity = it))
        })
    }

    @Test fun acceptsDecimalCommaAndWhitespace() {
        val input = parseProfile(" 180,5 ", "80.25", "30", "MALE", "MODERATE")
        assertEquals(180.5, input.heightCm, 0.0)
        assertEquals(80.25, input.weightKg, 0.0)
    }

    @Test fun rejectsInvalidOrUnusableMeasurements() {
        listOf("", "0", "-1", "NaN", "Infinity", "1e309").forEach { height ->
            assertThrows(IllegalArgumentException::class.java) { parseProfile(height, "80", "30", "MALE", "LIGHT") }
        }
        listOf("0", "-1", "NaN", "Infinity").forEach { weight ->
            assertThrows(IllegalArgumentException::class.java) { parseProfile("180", weight, "30", "MALE", "LIGHT") }
        }
        listOf("", "0", "-1", "30.5", "2147483648").forEach { age ->
            assertThrows(IllegalArgumentException::class.java) { parseProfile("180", "80", age, "MALE", "LIGHT") }
        }
        assertThrows(IllegalArgumentException::class.java) { parseProfile("180", "80", "30", "", "LIGHT") }
        assertThrows(IllegalArgumentException::class.java) { parseProfile("180", "80", "30", "MALE", "") }
        assertThrows(IllegalArgumentException::class.java) { CalorieGoal.maintenance(male.copy(heightCm = Double.MAX_VALUE)) }
        assertThrows(IllegalArgumentException::class.java) { CalorieGoal.maintenance(male.copy(heightCm = 1.0, weightKg = 1.0, age = 120)) }
    }

    @Test fun manualGoalRequiresPositiveWholeKcal() {
        assertEquals(2200, parseManualGoal(" 2200 "))
        listOf("", "0", "-100", "2000.5", "NaN", "2147483648").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseManualGoal(it) }
        }
    }
}

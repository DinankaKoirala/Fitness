package com.example.fitness.profile

import kotlin.math.roundToInt

enum class EquationSex(val label: String, val adjustment: Int) {
    MALE("Male", 5), FEMALE("Female", -161),
}

enum class ActivityLevel(val label: String, val detail: String, val factor: Double) {
    SEDENTARY("Sedentary", "Little or no exercise", 1.2),
    LIGHT("Lightly active", "Light exercise 1–3 days/week", 1.375),
    MODERATE("Moderately active", "Moderate exercise 3–5 days/week", 1.55),
    VERY_ACTIVE("Very active", "Hard exercise 6–7 days/week", 1.725),
    EXTRA_ACTIVE("Extra active", "Physical job and hard training", 1.9),
}

data class ProfileInput(
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val sex: EquationSex,
    val activity: ActivityLevel,
) {
    init {
        require(heightCm.isFinite() && heightCm > 0) { "Enter a positive height in cm." }
        require(weightKg.isFinite() && weightKg > 0) { "Enter a positive weight in kg." }
        require(age > 0) { "Enter your age as a positive whole number." }
    }
}

object CalorieGoal {
    // Mifflin et al., AJCN 1990;51:241–247, doi:10.1093/ajcn/51.2.241.
    fun bmr(input: ProfileInput): Double =
        10 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.age + input.sex.adjustment

    fun maintenance(input: ProfileInput): Int {
        val result = bmr(input) * input.activity.factor
        require(result.isFinite() && result >= 1 && result <= Int.MAX_VALUE) {
            "These measurements do not produce a valid estimate. Check your entries."
        }
        return result.roundToInt()
    }
}

fun parseProfile(height: String, weight: String, age: String, sex: String, activity: String): ProfileInput {
    val input = ProfileInput(
        height.trim().replace(',', '.').toDoubleOrNull()
            ?: throw IllegalArgumentException("Enter a height in cm."),
        weight.trim().replace(',', '.').toDoubleOrNull()
            ?: throw IllegalArgumentException("Enter a weight in kg."),
        age.trim().toIntOrNull() ?: throw IllegalArgumentException("Enter your age as a positive whole number."),
        EquationSex.entries.find { it.name == sex }
            ?: throw IllegalArgumentException("Choose a sex input for the equation."),
        ActivityLevel.entries.find { it.name == activity }
            ?: throw IllegalArgumentException("Choose an activity level."),
    )
    CalorieGoal.maintenance(input)
    return input
}

fun parseManualGoal(value: String): Int = value.trim().toIntOrNull()?.takeIf { it > 0 }
    ?: throw IllegalArgumentException("Enter a positive whole number of kcal per day.")

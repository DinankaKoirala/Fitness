package com.example.fitness.food

import com.example.fitness.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

enum class Micro(val label: String, val unit: String, val value: (Nutrients) -> Double?) {
    FIBER("Fiber", "g", { it.fiberG }), SODIUM("Sodium", "mg", { it.sodiumMg }),
    CALCIUM("Calcium", "mg", { it.calciumMg }), IRON("Iron", "mg", { it.ironMg }),
    POTASSIUM("Potassium", "mg", { it.potassiumMg }), MAGNESIUM("Magnesium", "mg", { it.magnesiumMg }),
    VITAMIN_C("Vitamin C", "mg", { it.vitaminCMg }), VITAMIN_D("Vitamin D", "µg", { it.vitaminDMcg }),
}

data class MicroTotal(val amount: Double, val known: Int, val count: Int)
data class NutritionTotals(val calories: Double, val protein: Double, val fat: Double, val carbs: Double,
    val micros: Map<Micro, MicroTotal>)

fun FoodSnapshot.factor(amount: Double, unit: String): Double {
    require(amount.isFinite() && amount in 0.000001..1_000_000_000.0) { "Enter a positive amount up to 1 billion." }
    require(servingAmount.isFinite() && servingAmount > 0)
    return when {
        unit == "serving" -> amount
        unit == servingUnit && unit in listOf("g", "ml") -> amount / servingAmount
        else -> throw IllegalArgumentException("Use the saved unit or servings; mass and volume cannot be converted.")
    }
}

fun FoodSnapshot.scaled(amount: Double, unit: String): Nutrients {
    val factor = factor(amount, unit)
    return nutrients.run { Nutrients(caloriesKcal * factor, proteinG * factor, fatG * factor, carbsG * factor,
        fiberG?.times(factor), sodiumMg?.times(factor), calciumMg?.times(factor), ironMg?.times(factor),
        potassiumMg?.times(factor), magnesiumMg?.times(factor), vitaminCMg?.times(factor), vitaminDMcg?.times(factor)) }
}

fun nutritionTotals(entries: List<MealEntry>): NutritionTotals {
    val values = entries.map { entry -> requireNotNull(entry.snapshot).scaled(entry.amount, entry.unit) }
    return NutritionTotals(values.sumOf { it.caloriesKcal }, values.sumOf { it.proteinG },
        values.sumOf { it.fatG }, values.sumOf { it.carbsG }, Micro.entries.associateWith { micro ->
            val known = values.mapNotNull(micro.value)
            MicroTotal(known.sum(), known.size, values.size)
        })
}

fun progressFraction(value: Double, goal: Double): Float =
    if (goal <= 0 || !value.isFinite() || !goal.isFinite()) 0f else (value / goal).coerceIn(0.0, 1.0).toFloat()

fun displayNumber(value: Double): String = String.format(Locale.getDefault(), "%.1f", value).trimEnd('0').trimEnd('.', ',')
fun pickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun pickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun foodNumber(value: String, label: String, positive: Boolean = false): Double {
    val number = value.trim().replace(',', '.').toDoubleOrNull()
    require(number != null && number.isFinite() && number <= 1_000_000_000 &&
        (if (positive) number >= 0.000001 else number >= 0)) {
        "$label must be ${if (positive) "positive" else "zero or positive"} and at most 1 billion."
    }
    return number
}

fun validateFood(food: Food) {
    require(food.name.trim().isNotEmpty()) { "Enter a food name." }
    require(food.name.length <= 200) { "Use a food name of at most 200 characters." }
    require(food.servingUnit in listOf("g", "ml", "serving")) { "Choose g, ml or serving." }
    foodNumber(food.servingAmount.toString(), "Serving amount", positive = true)
    require(food.servingUnit != "serving" || food.servingAmount == 1.0) { "A serving is one saved portion." }
    listOf(food.nutrients.caloriesKcal, food.nutrients.proteinG, food.nutrients.fatG, food.nutrients.carbsG)
        .forEach { foodNumber(it.toString(), "Nutrition") }
    Micro.entries.forEach { micro -> micro.value(food.nutrients)?.let { foodNumber(it.toString(), micro.label) } }
}

package com.example.fitness.trends

import com.example.fitness.data.*
import com.example.fitness.food.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class WeeklyMicro(val week: String, val amount: Double?, val completeDays: Int, val loggedDays: Int)
fun weeklyMicros(entries: List<MealEntry>, micro: Micro): List<WeeklyMicro> = entries.groupBy {
    LocalDate.parse(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
}.toSortedMap().map { (week, meals) ->
    val days = meals.groupBy { it.date }.values
    val complete = days.map { nutritionTotals(it).micros.getValue(micro) }.filter { it.known == it.count }
    WeeklyMicro(week, complete.takeIf { it.isNotEmpty() }?.map { it.amount }?.average(), complete.size, days.size)
}
fun chartX(index: Int, count: Int): Float = if (count <= 1) 0.5f else index.toFloat() / (count - 1)
fun chartY(value: Double, maximum: Double): Float = if (!value.isFinite() || maximum <= 0 || !maximum.isFinite()) 1f else (1 - value / maximum).coerceIn(0.0, 1.0).toFloat()

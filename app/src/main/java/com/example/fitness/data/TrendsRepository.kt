package com.example.fitness.data

import com.example.fitness.activity.*
import java.time.*
import kotlinx.coroutines.flow.*

data class TrendsState(val range: TimeRange, val loading: Boolean = true, val meals: List<MealEntry> = emptyList(),
    val steps: List<StepDay> = emptyList(), val weights: List<WeightEntry> = emptyList(), val sets: List<SetEntry> = emptyList(),
    val exercises: List<Exercise> = emptyList(), val segments: List<Segment> = emptyList(), val error: String? = null)
class TrendsRepository(private val database: FitnessDatabase) {
    fun observe(range: TimeRange): Flow<TrendsState> {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(range.from).atZone(zone).toLocalDate().toString()
        val to = Instant.ofEpochMilli(range.to - 1).atZone(zone).toLocalDate().toString()
        val dao = database.trendsDao()
        val daily = combine(dao.meals(from, to), dao.steps(from, to), dao.weights(from, to)) { a, b, c -> Triple(a, b, c) }
        val sessions = combine(dao.sets(range.to), dao.exercises(), SegmentRepository(database).observe(range)) { a, b, c -> Triple(a, b, c) }
        return combine(daily, sessions) { (meals, steps, weights), (sets, exercises, route) ->
            TrendsState(range, false, meals, steps, weights, sets, exercises, route.segments, route.error)
        }.catch { emit(TrendsState(range, loading = false, error = "Could not load trends. Try again.")) }
    }
    suspend fun logWeight(date: String, kg: Double) {
        LocalDate.parse(date)
        require(kg.isFinite() && kg > 0 && kg <= 1000) { "Enter a weight between 0 and 1,000 kg." }
        database.trendsDao().addWeight(WeightEntry(date = date, weightKg = kg))
    }
}

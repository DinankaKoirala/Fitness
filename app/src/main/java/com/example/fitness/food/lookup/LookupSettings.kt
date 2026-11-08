package com.example.fitness.food.lookup

import android.content.Context
import androidx.datastore.preferences.core.*
import com.example.fitness.data.goalDataStore
import kotlinx.coroutines.flow.map

enum class LookupMode(val label: String) {
    DATABASE_FIRST("Database first, then AI fallback"), AI_ONLY("AI only"), DATABASE_ONLY("Database only")
}
class LookupSettings(context: Context) {
    private val store = context.applicationContext.goalDataStore
    private val modeKey = stringPreferencesKey("food_lookup_mode")
    private val modelKey = stringPreferencesKey("gemini_model")
    val mode = store.data.map { p -> LookupMode.entries.find { it.name == p[modeKey] } ?: LookupMode.DATABASE_FIRST }
    val model = store.data.map { it[modelKey] ?: "gemini-flash-lite-latest" }
    suspend fun mode(value: LookupMode) { store.edit { it[modeKey] = value.name } }
    suspend fun model(value: String) {
        require(value.matches(Regex("gemini-[A-Za-z0-9._-]+"))) { "Enter a valid Gemini model ID." }
        store.edit { it[modelKey] = value }
    }
}

suspend fun lookupFood(query: String, mode: LookupMode, sources: List<FoodSource>, estimate: (suspend () -> FoodCandidate)?): List<FoodCandidate> {
    require(query.isNotBlank()) { "Enter a food or meal description." }
    if (mode != LookupMode.AI_ONLY) {
        for (source in sources) {
            val found = try { source.search(query) } catch (_: java.io.IOException) { emptyList() }
            if (found.isNotEmpty()) return found
        }
    }
    return if (mode != LookupMode.DATABASE_ONLY && estimate != null) listOf(estimate()) else emptyList()
}

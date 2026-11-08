package com.example.fitness.data

import androidx.room.withTransaction
import com.example.fitness.food.*
import java.time.LocalDate
import kotlinx.coroutines.flow.*

data class FoodDayState(
    val date: String,
    val loading: Boolean = true,
    val entries: List<MealEntry> = emptyList(),
    val foods: List<Food> = emptyList(),
    val recent: List<Food> = emptyList(),
    val error: String? = null,
)

class FoodRepository(private val database: FitnessDatabase) {
    private val dao = database.foodDao()
    fun observeDay(date: String): Flow<FoodDayState> {
        LocalDate.parse(date)
        return combine(dao.observeEntries(date), dao.observeFoods(), dao.observeRecentFoods()) { entries, foods, recent ->
            check(entries.all { it.snapshot != null }) { "Missing meal nutrition" }
            FoodDayState(date, loading = false, entries, foods, recent)
        }.catch { emit(FoodDayState(date, loading = false, error = "Could not load your food log. Try again.")) }
    }

    suspend fun saveFood(food: Food): Long = database.withTransaction {
        validateFood(food)
        val now = System.currentTimeMillis()
        if (food.id == 0L) dao.insertFood(food.copy(name = food.name.trim(), createdAt = now, updatedAt = now))
        else {
            val old = requireNotNull(dao.food(food.id)) { "This saved food no longer exists." }
            check(dao.updateFood(food.copy(name = food.name.trim(), createdAt = old.createdAt, updatedAt = now)) == 1)
            food.id
        }
    }

    suspend fun addEntry(foodId: Long, date: String, meal: MealType, amount: Double, unit: String): Long = database.withTransaction {
        LocalDate.parse(date)
        val food = requireNotNull(dao.food(foodId)) { "This saved food no longer exists." }
        val snapshot = food.snapshot()
        snapshot.factor(amount, unit)
        dao.insertEntry(MealEntry(foodId = foodId, date = date, mealType = meal, amount = amount, unit = unit, snapshot = snapshot))
    }

    suspend fun editEntry(id: Long, meal: MealType, amount: Double, unit: String) = database.withTransaction {
        val old = requireNotNull(dao.entry(id)) { "This entry no longer exists." }
        requireNotNull(old.snapshot).factor(amount, unit)
        check(dao.updateEntry(old.copy(mealType = meal, amount = amount, unit = unit, updatedAt = System.currentTimeMillis())) == 1)
    }

    suspend fun editLoggedNutrition(id: Long, food: Food) = database.withTransaction {
        validateFood(food)
        val old = requireNotNull(dao.entry(id)) { "This entry no longer exists." }
        val snapshot = food.copy(name = food.name.trim()).snapshot()
        snapshot.factor(old.amount, old.unit)
        check(dao.updateEntry(old.copy(snapshot = snapshot, updatedAt = System.currentTimeMillis())) == 1)
    }

    suspend fun deleteEntry(id: Long) {
        check(dao.deleteEntry(id) == 1) { "This entry no longer exists." }
    }
}

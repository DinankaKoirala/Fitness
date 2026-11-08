package com.example.fitness.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM Food ORDER BY name COLLATE NOCASE, id")
    fun observeFoods(): Flow<List<Food>>

    @Query("SELECT Food.* FROM Food JOIN MealEntry ON Food.id = MealEntry.foodId GROUP BY Food.id ORDER BY MAX(MealEntry.createdAt) DESC, Food.id DESC LIMIT 20")
    fun observeRecentFoods(): Flow<List<Food>>

    @Query("SELECT * FROM MealEntry WHERE date = :date ORDER BY createdAt, id")
    fun observeEntries(date: String): Flow<List<MealEntry>>

    @Query("SELECT * FROM Food WHERE id = :id")
    suspend fun food(id: Long): Food?

    @Query("SELECT * FROM MealEntry WHERE id = :id")
    suspend fun entry(id: Long): MealEntry?

    @Insert suspend fun insertFood(food: Food): Long
    @Update suspend fun updateFood(food: Food): Int
    @Insert suspend fun insertEntry(entry: MealEntry): Long
    @Update suspend fun updateEntry(entry: MealEntry): Int
    @Query("DELETE FROM MealEntry WHERE id = :id")
    suspend fun deleteEntry(id: Long): Int
}

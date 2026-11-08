package com.example.fitness

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.*
import com.example.fitness.food.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodDataTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample = Food(name = "Oats", servingAmount = 100.0, servingUnit = "g", estimated = true,
        nutrients = Nutrients(400.0, 20.0, 10.0, 60.0, fiberG = 8.0))

    @Test fun completeDayEditsDeletesAndSavedFoodHistory() = runBlocking {
        val name = "food-data-test.db"
        context.deleteDatabase(name)
        var database = FitnessDatabase.create(context, name)
        try {
            var repo = FoodRepository(database)
            val id = repo.saveFood(sample)
            val date = "2026-11-08"
            val entries = listOf(
                repo.addEntry(id, date, MealType.BREAKFAST, 25.0, "g"),
                repo.addEntry(id, date, MealType.LUNCH, 50.0, "g"),
                repo.addEntry(id, date, MealType.DINNER, 1.0, "serving"),
                repo.addEntry(id, date, MealType.SNACKS, 2.0, "serving"),
            )
            suspend fun day() = withTimeout(5000) { repo.observeDay(date).first { !it.loading } }
            assertEquals(1500.0, nutritionTotals(day().entries).calories, 0.0)
            assertEquals(30.0, nutritionTotals(day().entries).micros[Micro.FIBER]!!.amount, 0.0)
            repo.deleteEntry(entries[0])
            repo.editEntry(entries[1], MealType.LUNCH, 75.0, "g")
            assertEquals(1500.0, nutritionTotals(day().entries).calories, 0.0)
            repo.saveFood(sample.copy(id = id, name = "Updated oats", estimated = false, nutrients = sample.nutrients.copy(caloriesKcal = 800.0)))
            assertEquals(1500.0, nutritionTotals(day().entries).calories, 0.0)
            assertEquals("Oats", day().entries.first().snapshot!!.name)
            repo.editLoggedNutrition(entries[2], sample.copy(nutrients = sample.nutrients.copy(caloriesKcal = 500.0)))
            assertEquals(1600.0, nutritionTotals(day().entries).calories, 0.0)
            assertEquals(id, day().recent.single().id)
            assertTrue(repo.observeDay("2026-11-09").first().entries.isEmpty())
            database.close()
            database = FitnessDatabase.create(context, name)
            repo = FoodRepository(database)
            assertEquals(1600.0, nutritionTotals(day().entries).calories, 0.0)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationPreservesVersionOneDataAndBackfillsSnapshots() = runBlocking {
        val name = "food-migration-test.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.fitness.data.FitnessDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\u0024{TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO Food (id, name, servingAmount, servingUnit, caloriesKcal, proteinG, fatG, carbsG, source, estimated, createdAt, updatedAt) VALUES (1, 'Original', 100, 'g', 200, 10, 5, 30, 'MANUAL', 0, 1, 1)")
            old.execSQL("INSERT INTO MealEntry (id, foodId, date, mealType, amount, unit, createdAt, updatedAt) VALUES (1, 1, '2026-11-08', 'BREAKFAST', 50, 'g', 1, 1)")
            old.execSQL("INSERT INTO StepDay (date, steps, createdAt, updatedAt) VALUES ('2026-11-08', 4321, 1, 1)")
            old.version = 1
        }
        val database = FitnessDatabase.create(context, name)
        try {
            assertEquals(4, database.openHelper.writableDatabase.version)
            val snapshot = database.foodDao().entry(1)!!.snapshot!!
            assertEquals("Original", snapshot.name)
            assertEquals(100.0, snapshot.scaled(50.0, "g").caloriesKcal, 0.0)
            assertNull(snapshot.nutrients.ironMg)
            database.openHelper.readableDatabase.query("SELECT steps FROM StepDay").use { it.moveToFirst(); assertEquals(4321, it.getInt(0)) }
        } finally { database.close(); context.deleteDatabase(name) }
    }
}

package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.*
import com.example.fitness.food.lookup.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodLookupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun databaseParsersPreserveUnitsUnknownValuesAndProvenance() = runBlocking {
        val off = OpenFoodFacts.parseProduct(JSONObject("""{"product_name":"Test cereal","nutriments":{"energy-kcal_100g":200,"proteins_100g":8,"fat_100g":5,"carbohydrates_100g":30,"sodium_100g":0.12,"iron_100g":0.002,"vitamin-d_100g":0.000002}}"""))!!
        assertEquals(120.0, off.food.nutrients.sodiumMg!!, 0.0001)
        assertEquals(2.0, off.food.nutrients.ironMg!!, 0.0001)
        assertEquals(2.0, off.food.nutrients.vitaminDMcg!!, 0.0001)
        assertNull(off.food.nutrients.fiberG)
        val usda = Usda.parseFood(JSONObject("""{"description":"Test food","foodNutrients":[{"nutrientId":1008,"unitName":"KCAL","value":123},{"nutrientId":1003,"unitName":"G","value":5}]}"""))!!
        assertTrue("carbsG" in usda.missing)
        assertEquals(123.0, usda.food.nutrients.caloriesKcal, 0.0)
        val raw = InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/swiss-apple.json").bufferedReader().use { it.readText() }
        val swiss = SwissFoods.parseOnline(JSONObject(raw))
        assertEquals(52.0, swiss.food.nutrients.caloriesKcal, 0.0)
        val local = SwissFoods(context).search("Apple, fresh").first()
        assertEquals(52.0, local.food.nutrients.caloriesKcal, 0.0)
        assertEquals(com.example.fitness.data.FoodSource.SWISS, local.food.source)
    }
    @Test fun failedAiRequestPersistsThenRequiresExplicitConfirmation() = runBlocking {
        val name = "lookup-queue.db"
        context.deleteDatabase(name)
        var db = FitnessDatabase.create(context, name)
        val settings = LookupSettings(context)
        val oldMode = settings.mode.first()
        try {
            settings.mode(LookupMode.AI_ONLY)
            val failing = object : AiProvider { override suspend fun estimate(description: String, image: File?): FoodCandidate { throw java.io.IOException("Offline") } }
            try { LookupController(context, db, failing).estimate("Test lunch", null); fail("Must be queued") } catch (_: IllegalStateException) { }
            val queued = db.aiQueueDao().observe().first().single()
            db.close(); db = FitnessDatabase.create(context, name)
            val result = FoodCandidate(Food(name = "Estimated lunch", servingAmount = 1.0, servingUnit = "serving", nutrients = Nutrients(500.0, 20.0, 15.0, 60.0), source = com.example.fitness.data.FoodSource.AI, estimated = true), "Gemini · estimated")
            val ready = object : AiProvider { override suspend fun estimate(description: String, image: File?) = result }
            val controller = LookupController(context, db, ready)
            val draft = controller.perform(queued.id)
            assertTrue(db.foodDao().observeFoods().first().isEmpty())
            assertNotNull(db.aiQueueDao().request(queued.id)!!.response)
            val food = controller.confirm(draft, draft.food)
            assertTrue(db.foodDao().food(food)!!.estimated)
            assertTrue(db.aiQueueDao().observe().first().isEmpty())
            assertTrue(db.foodDao().observeEntries("2026-11-08").first().isEmpty())
        } finally { settings.mode(oldMode); db.close(); context.deleteDatabase(name) }
    }
}

package com.example.fitness.food.lookup

import com.example.fitness.data.Food
import com.example.fitness.data.Nutrients
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LookupPolicyTest {
    private val result = FoodCandidate(Food(name = "Test", servingAmount = 1.0, servingUnit = "serving", nutrients = Nutrients()), "Test")
    @Test fun databaseOnlyNeverCallsAiAndAiOnlyNeverCallsDatabases() = runBlocking {
        var databaseCalls = 0; var aiCalls = 0
        val source = object : FoodSource { override val name = "Fake"; override suspend fun search(query: String): List<FoodCandidate> { databaseCalls++; return emptyList() } }
        assertTrue(lookupFood("food", LookupMode.DATABASE_ONLY, listOf(source)) { aiCalls++; result }.isEmpty())
        assertEquals(0, aiCalls)
        lookupFood("food", LookupMode.AI_ONLY, listOf(source)) { aiCalls++; result }
        assertEquals(1, databaseCalls); assertEquals(1, aiCalls)
    }
    @Test fun databaseFirstUsesAiOnlyAfterNoDatabaseMatch() = runBlocking {
        var aiCalls = 0
        val source = object : FoodSource { override val name = "Fake"; override suspend fun search(query: String) = listOf(result) }
        assertEquals(listOf(result), lookupFood("food", LookupMode.DATABASE_FIRST, listOf(source)) { aiCalls++; result })
        assertEquals(0, aiCalls)
        assertEquals(listOf(result), lookupFood("food", LookupMode.DATABASE_FIRST, emptyList()) { aiCalls++; result })
        assertEquals(1, aiCalls)
    }
    @Test fun labelParserConvertsUnitsAndAvoidsSaturatedFatOrPercentGuessing() {
        val draft = parseLabel("Per 100g\nEnergy 840 kJ / 200 kcal\nTotal fat 10 g\nSaturated fat 4 g\nCarbohydrate 20 g\nProtein 8 g\nSodium 100 mg\nVitamin D 2 µg")
        assertEquals(200.0, draft.food.nutrients.caloriesKcal, 0.0)
        assertEquals(10.0, draft.food.nutrients.fatG, 0.0)
        assertEquals(100.0, draft.food.nutrients.sodiumMg!!, 0.0001)
        assertEquals(2.0, draft.food.nutrients.vitaminDMcg!!, 0.0001)
        assertEquals("g", draft.food.servingUnit)
        assertTrue(draft.food.estimated)
        val partial = parseLabel("Calories 100\nSaturated fat 4g\nCalcium 10%")
        assertTrue("fatG" in partial.missing)
        assertNull(partial.food.nutrients.calciumMg)
    }
}

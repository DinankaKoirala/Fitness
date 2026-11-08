package com.example.fitness.food.lookup

import android.content.Context
import com.example.fitness.core.ForegroundNetwork
import com.example.fitness.data.Food
import com.example.fitness.data.FoodSource as Provenance
import com.example.fitness.data.Nutrients
import com.example.fitness.food.validateFood
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FoodCandidate(val food: Food, val attribution: String, val missing: Set<String> = emptySet(), val requestId: Long? = null)
interface FoodSource {
    val name: String
    suspend fun search(query: String): List<FoodCandidate>
    suspend fun barcode(code: String): FoodCandidate? = null
}

suspend fun requestJson(client: OkHttpClient, request: Request): String = suspendCancellableCoroutine { continuation ->
    val call = client.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val text = response.use {
                    if (!it.isSuccessful) throw IOException("Provider request failed (HTTP ${it.code}).")
                    val body = it.body ?: throw IOException("Provider returned no data.")
                    if (body.contentLength() > 5_000_000) throw IOException("Provider response is too large.")
                    body.string().also { value -> if (value.length > 5_000_000) throw IOException("Provider response is too large.") }
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
        }
    })
}
internal fun JSONObject.number(name: String): Double? = opt(name)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= 1_000_000_000 }
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun candidate(name: String, values: Map<String, Double?>, source: Provenance, attribution: String, estimated: Boolean, amount: Double = 100.0, unit: String = "g"): FoodCandidate {
    val required = setOf("caloriesKcal", "proteinG", "fatG", "carbsG")
    val missing = required.filterTo(mutableSetOf()) { values[it] == null }
    val food = Food(name = name.take(200).ifBlank { "Food" }, servingAmount = amount, servingUnit = unit,
        source = source, estimated = estimated || missing.isNotEmpty(), nutrients = Nutrients(
            values["caloriesKcal"] ?: 0.0, values["proteinG"] ?: 0.0, values["fatG"] ?: 0.0, values["carbsG"] ?: 0.0,
            values["fiberG"], values["sodiumMg"], values["calciumMg"], values["ironMg"], values["potassiumMg"], values["magnesiumMg"], values["vitaminCMg"], values["vitaminDMcg"]))
    validateFood(food)
    return FoodCandidate(food, attribution, missing)
}

class OpenFoodFacts(private val client: OkHttpClient) : FoodSource {
    override val name = "Open Food Facts"
    override suspend fun search(query: String): List<FoodCandidate> {
        val url = "https://world.openfoodfacts.org/cgi/search.pl".toHttpUrl().newBuilder()
            .addQueryParameter("search_terms", query).addQueryParameter("search_simple", "1").addQueryParameter("action", "process")
            .addQueryParameter("json", "1").addQueryParameter("page_size", "15").addQueryParameter("fields", "code,product_name,nutriments").build()
        return JSONObject(requestJson(client, Request.Builder().url(url).build())).optJSONArray("products")?.objects()?.mapNotNull(::parseProduct) ?: emptyList()
    }
    override suspend fun barcode(code: String): FoodCandidate? {
        require(code.matches(Regex("[0-9]{8,14}"))) { "Enter a numeric product barcode." }
        val url = "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=code,product_name,nutriments"
        val root = JSONObject(requestJson(client, Request.Builder().url(url).build()))
        return root.optJSONObject("product")?.let(::parseProduct)
    }
    companion object {
        fun parseProduct(product: JSONObject): FoodCandidate? {
            val name = product.optString("product_name").takeIf { it.isNotBlank() } ?: return null
            val n = product.optJSONObject("nutriments") ?: return null
            fun grams(field: String) = n.number("${field}_100g")
            val converted = n.number("energy-kcal_100g") == null || grams("sodium") == null && grams("salt") != null
            return candidate(name, mapOf("caloriesKcal" to (n.number("energy-kcal_100g") ?: n.number("energy-kj_100g")?.div(4.184)),
                "proteinG" to grams("proteins"), "fatG" to grams("fat"), "carbsG" to grams("carbohydrates"), "fiberG" to grams("fiber"),
                "sodiumMg" to (grams("sodium")?.times(1000) ?: grams("salt")?.times(400)),
                "calciumMg" to grams("calcium")?.times(1000), "ironMg" to grams("iron")?.times(1000),
                "potassiumMg" to grams("potassium")?.times(1000), "magnesiumMg" to grams("magnesium")?.times(1000),
                "vitaminCMg" to grams("vitamin-c")?.times(1000), "vitaminDMcg" to grams("vitamin-d")?.times(1_000_000)),
                Provenance.OFF, "Open Food Facts · ODbL database, individual contents DbCL · verify community-contributed data", converted)
        }
    }
}

class Usda(private val client: OkHttpClient, private val key: String) : FoodSource {
    override val name = "USDA FoodData Central"
    override suspend fun search(query: String): List<FoodCandidate> {
        require(key.isNotBlank()) { "Add your USDA key in Settings." }
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", key).addQueryParameter("query", query).addQueryParameter("pageSize", "15").build()
        return JSONObject(requestJson(client, Request.Builder().url(url).build())).optJSONArray("foods")?.objects()?.mapNotNull(::parseFood) ?: emptyList()
    }
    companion object {
        fun parseFood(food: JSONObject): FoodCandidate? {
            val name = food.optString("description").takeIf { it.isNotBlank() } ?: return null
            val nutrients = food.optJSONArray("foodNutrients")?.objects().orEmpty()
            fun value(id: Int, unit: String): Double? {
                val nutrient = nutrients.firstOrNull { it.optInt("nutrientId") == id && it.optString("unitName").equals(unit, true) } ?: return null
                return nutrient.number("value")
            }
            return candidate(name, mapOf("caloriesKcal" to value(1008, "kcal"), "proteinG" to value(1003, "g"), "fatG" to value(1004, "g"),
                "carbsG" to value(1005, "g"), "fiberG" to value(1079, "g"), "sodiumMg" to value(1093, "mg"), "calciumMg" to value(1087, "mg"),
                "ironMg" to value(1089, "mg"), "potassiumMg" to value(1092, "mg"), "magnesiumMg" to value(1090, "mg"),
                "vitaminCMg" to value(1162, "mg"), "vitaminDMcg" to (value(1114, "µg") ?: value(1114, "UG"))),
                Provenance.USDA, "USDA Agricultural Research Service · FoodData Central · CC0", false)
        }
    }
}

class SwissFoods(private val context: Context, private val client: OkHttpClient? = null, private val online: Boolean = false) : FoodSource {
    override val name = if (online) "Swiss API" else "Swiss offline"
    override suspend fun search(query: String): List<FoodCandidate> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (online) {
            val http = requireNotNull(client)
            val url = "$BASE/foods".toHttpUrl().newBuilder().addQueryParameter("search", query).addQueryParameter("lang", "en").addQueryParameter("limit", "5").build()
            JSONArray(requestJson(http, Request.Builder().url(url).build())).objects().map { row ->
                parseOnline(JSONObject(requestJson(http, Request.Builder().url("$BASE/food/${row.getLong("id")}?lang=en").build())))
            }
        } else {
            val data = context.assets.open("foods/swiss-v7.1.json").bufferedReader().use { JSONArray(it.readText()) }
            data.objects().filter { it.optString("name").contains(query, true) }.take(20).map { row ->
                val nutrients = row.getJSONObject("nutrients")
                candidate(row.getString("name"), nutrients.keys().asSequence().associateWith { nutrients.number(it) }, Provenance.SWISS,
                    "Swiss Food Composition Database · FSVO · v7.1 (2026), per 100 g edible portion", true)
            }
        }
    }
    companion object {
        const val BASE = "https://api.webapp.prod.blv.foodcase-services.com/BLV_WebApp_WS/webresources/BLV-api"
        fun parseOnline(food: JSONObject): FoodCandidate {
            val values = food.optJSONArray("values")?.objects().orEmpty()
            fun nutrient(code: String, unit: String): Double? = values.firstOrNull {
                it.optJSONObject("component")?.optString("code") == code && it.optJSONObject("unit")?.optString("code") == unit &&
                    it.optString("valueTypeCode") !in listOf("TR", "LT", "ND")
            }?.number("value")
            return candidate(food.getString("name"), mapOf("caloriesKcal" to nutrient("ENERCC", "kcal"), "proteinG" to nutrient("PROT625", "g"),
                "fatG" to nutrient("FAT", "g"), "carbsG" to nutrient("CHO", "g"), "fiberG" to nutrient("FIBT", "g"),
                "sodiumMg" to nutrient("NA", "mg"), "calciumMg" to nutrient("CA", "mg"), "ironMg" to nutrient("FE", "mg"),
                "potassiumMg" to nutrient("K", "mg"), "magnesiumMg" to nutrient("MG", "mg"), "vitaminCMg" to nutrient("VITC", "mg"),
                "vitaminDMcg" to nutrient("VITD", "µg")), Provenance.SWISS, "Swiss Food Composition Database · FSVO · public API", true,
                unit = if (food.optString("matrixunitcode") == "V") "ml" else "g")
        }
    }
}

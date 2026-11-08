package com.example.fitness.food.lookup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.room.withTransaction
import com.example.fitness.core.*
import com.example.fitness.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class LookupController(private val context: Context, private val database: FitnessDatabase, private val ai: AiProvider = GeminiProvider(context)) {
    val settings = LookupSettings(context)
    val queued = database.aiQueueDao().observe()
    private val secrets = Secrets(context)
    private val client get() = ForegroundNetwork.client(context)
    suspend fun search(query: String, source: String): List<FoodCandidate> {
        require(query.isNotBlank() && query.length <= 5000) { "Enter a description up to 5,000 characters." }
        val mode = settings.mode.first()
        if (mode == LookupMode.AI_ONLY) return listOf(estimate(query, null))
        val databases: List<FoodSource> = when (source) {
            "Swiss offline" -> listOf(SwissFoods(context))
            "Swiss API" -> listOf(SwissFoods(context, client, true))
            "Open Food Facts" -> listOf(OpenFoodFacts(client))
            "USDA" -> listOf(Usda(client, secrets.get("usda")))
            else -> buildList {
                add(SwissFoods(context)); add(OpenFoodFacts(client))
                val key = secrets.get("usda"); if (key.isNotBlank()) add(Usda(client, key))
            }
        }
        val ai = if (mode == LookupMode.DATABASE_FIRST && secrets.get("gemini").isNotBlank()) suspend { estimate(query, null) } else null
        return lookupFood(query, mode, databases, ai)
    }
    suspend fun barcode(code: String): FoodCandidate? {
        require(settings.mode.first() != LookupMode.AI_ONLY) { "Barcode lookup needs a database mode." }
        return OpenFoodFacts(client).barcode(code)
    }
    suspend fun estimate(description: String, photo: File?): FoodCandidate {
        require(settings.mode.first() != LookupMode.DATABASE_ONLY) { "AI is disabled in database-only mode." }
        val image = photo?.let {
            withContext(Dispatchers.IO) {
                val folder = File(context.filesDir, "ai_images").apply { mkdirs() }
                File(folder, UUID.randomUUID().toString() + ".jpg").also { target -> it.copyTo(target) }
            }
        }
        val id = database.aiQueueDao().insert(QueuedAiRequest(prompt = description, imagePath = image?.name))
        return try { perform(id) } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            throw IllegalStateException("Request saved on this device. Add/check your key and retry here on Wi-Fi or while charging.")
        }
    }
    suspend fun perform(id: Long): FoodCandidate {
        val request = requireNotNull(database.aiQueueDao().request(id)) { "Request no longer exists." }
        request.response?.let { return deserializeCandidate(it, id) }
        require(settings.mode.first() != LookupMode.DATABASE_ONLY) { "Switch from database-only mode to retry AI requests." }
        val image = request.imagePath?.let { safeImage(it) }
        require(image == null || image.isFile) { "The saved image is unavailable. Discard this request and select it again." }
        val candidate = ai.estimate(request.prompt, image)
        database.aiQueueDao().update(request.copy(response = serializeCandidate(candidate), updatedAt = System.currentTimeMillis()))
        return candidate.copy(requestId = id)
    }
    fun canRetry(): Boolean {
        val connection = context.getSystemService(ConnectivityManager::class.java)
        val wifi = connection.getNetworkCapabilities(connection.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return wifi || context.getSystemService(BatteryManager::class.java).isCharging
    }
    fun safeImage(name: String): File {
        require(name.matches(Regex("[a-fA-F0-9-]+\\.jpg"))) { "Invalid queued image name." }
        return File(File(context.filesDir, "ai_images"), name)
    }
    suspend fun discard(id: Long) {
        val old = database.aiQueueDao().request(id)
        database.aiQueueDao().delete(id)
        old?.imagePath?.let { withContext(Dispatchers.IO) { safeImage(it).delete() } }
    }
    suspend fun confirm(value: FoodCandidate, food: Food): Long {
        val result = database.withTransaction {
            val request = value.requestId?.let { requireNotNull(database.aiQueueDao().request(it)) }
            val id = FoodRepository(database).saveFood(food.copy(id = 0))
            request?.let { database.aiQueueDao().delete(it.id) }
            id to request?.imagePath
        }
        result.second?.let { withContext(Dispatchers.IO) { safeImage(it).delete() } }
        return result.first
    }
}

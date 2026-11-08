package com.example.fitness.backup

import android.content.Context
import androidx.datastore.preferences.core.*
import com.example.fitness.data.goalDataStore
import com.example.fitness.food.lookup.LookupMode
import kotlinx.coroutines.flow.first
import org.json.JSONObject

// User preferences only. Keys and device-specific recording/permission state
// deliberately never move to another device or start recording after restore.
internal val backupSettings = mapOf("manual_calorie_goal_kcal" to "int", "food_lookup_mode" to "string",
    "gemini_model" to "string", "battery_saver" to "boolean")
internal suspend fun readSettings(context: Context): JSONObject {
    val current = context.goalDataStore.data.first()
    return JSONObject().apply { current.asMap().forEach { (key, value) -> if (key.name in backupSettings) put(key.name, value) } }
}
internal fun validateSettings(settings: JSONObject) {
    settings.keys().forEach { key ->
        require(key in backupSettings) { "Unsupported backup setting." }
        when (backupSettings.getValue(key)) {
            "int" -> require(settings.get(key) is Number && settings.getLong(key) in 1..Int.MAX_VALUE) { "Invalid calorie goal." }
            "boolean" -> require(settings.get(key) is Boolean)
            else -> {
                require(settings.get(key) is String)
                if (key == "food_lookup_mode") require(LookupMode.entries.any { it.name == settings.getString(key) })
                if (key == "gemini_model") require(settings.getString(key).matches(Regex("gemini-[A-Za-z0-9._-]+")))
            }
        }
    }
}
internal suspend fun writeSettings(context: Context, settings: JSONObject) {
    validateSettings(settings)
    context.goalDataStore.edit { values ->
        backupSettings.forEach { (name, type) ->
            when (type) {
                "int" -> { val key = intPreferencesKey(name); if (settings.has(name)) values[key] = settings.getInt(name) else values.remove(key) }
                "boolean" -> { val key = booleanPreferencesKey(name); if (settings.has(name)) values[key] = settings.getBoolean(name) else values.remove(key) }
                else -> { val key = stringPreferencesKey(name); if (settings.has(name)) values[key] = settings.getString(name) else values.remove(key) }
            }
        }
        values[booleanPreferencesKey("steps_enabled")] = false
        values[booleanPreferencesKey("route_enabled")] = false
        values.remove(stringPreferencesKey("tracking_error")); values.remove(stringPreferencesKey("route_error"))
    }
}

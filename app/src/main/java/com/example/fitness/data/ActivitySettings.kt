package com.example.fitness.data

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map

class ActivitySettings(context: Context) {
    private val store = context.applicationContext.goalDataStore
    private val stepsKey = booleanPreferencesKey("steps_enabled")
    private val errorKey = stringPreferencesKey("tracking_error")
    val stepsEnabled = store.data.map { it[stepsKey] ?: false }
    val error = store.data.map { it[errorKey] }
    suspend fun stepsEnabled(enabled: Boolean) { store.edit { it[stepsKey] = enabled; it.remove(errorKey) } }
    suspend fun error(message: String?) { store.edit { if (message == null) it.remove(errorKey) else it[errorKey] = message } }
}

package com.example.fitness.data

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map

class ActivitySettings(context: Context) {
    private val store = context.applicationContext.goalDataStore
    private val stepsKey = booleanPreferencesKey("steps_enabled")
    private val errorKey = stringPreferencesKey("tracking_error")
    private val routeKey = booleanPreferencesKey("route_enabled")
    private val saverKey = booleanPreferencesKey("battery_saver")
    private val routeErrorKey = stringPreferencesKey("route_error")
    val routeEnabled = store.data.map { it[routeKey] ?: false }
    val batterySaver = store.data.map { it[saverKey] ?: false }
    val routeError = store.data.map { it[routeErrorKey] }
    suspend fun routeEnabled(enabled: Boolean) { store.edit { it[routeKey] = enabled; it.remove(routeErrorKey) } }
    suspend fun batterySaver(enabled: Boolean) { store.edit { it[saverKey] = enabled } }
    suspend fun routeError(message: String?) { store.edit { if (message == null) it.remove(routeErrorKey) else it[routeErrorKey] = message } }
    val stepsEnabled = store.data.map { it[stepsKey] ?: false }
    val error = store.data.map { it[errorKey] }
    suspend fun stepsEnabled(enabled: Boolean) { store.edit { it[stepsKey] = enabled; it.remove(errorKey) } }
    suspend fun error(message: String?) { store.edit { if (message == null) it.remove(errorKey) else it[errorKey] = message } }
}

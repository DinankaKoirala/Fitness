package com.example.fitness.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

// One DataStore instance per file for the entire application process.
val Context.goalDataStore by preferencesDataStore(name = "goal_settings")

class GoalSettings(private val store: DataStore<Preferences>) {
    private val manualGoal = intPreferencesKey("manual_calorie_goal_kcal")
    val override = store.data.map { values -> values[manualGoal] }

    suspend fun setOverride(kcal: Int?) {
        require(kcal == null || kcal > 0)
        store.edit { values ->
            if (kcal == null) values.remove(manualGoal) else values[manualGoal] = kcal
        }
    }
}

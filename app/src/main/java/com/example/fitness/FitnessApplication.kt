package com.example.fitness

import android.app.Application
import com.example.fitness.data.FitnessDatabase
import com.example.fitness.data.StepRepository
import com.example.fitness.data.FoodRepository
import com.example.fitness.data.ProfileRepository
import com.example.fitness.data.GoalSettings
import com.example.fitness.data.goalDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executors

class FitnessApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val steps by lazy { StepRepository(database) }
    val foods by lazy { FoodRepository(database) }
    val profiles by lazy { ProfileRepository(database.profileDao(), GoalSettings(goalDataStore), applicationScope) }
    val database by lazy { FitnessDatabase.create(this) }

    override fun onCreate() {
        super.onCreate()
        // Force the initial schema creation off the main thread, even before any data is logged.
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try { database.openHelper.writableDatabase } finally { executor.shutdown() }
        }
    }
}

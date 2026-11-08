package com.example.fitness

import android.app.Application
import com.example.fitness.data.FitnessDatabase
import java.util.concurrent.Executors

class FitnessApplication : Application() {
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

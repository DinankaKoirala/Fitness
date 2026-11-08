package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationStorageTest {
    @Test fun batchedPointsPersistAndRespectHalfOpenRanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "location-storage.db"
        context.deleteDatabase(name)
        var db = FitnessDatabase.create(context, name)
        try {
            db.locationDao().insert(listOf(1000L, 2000L, 3000L).map {
                LocationPoint(timestamp = it, lat = 27.7, lon = 85.3, speed = null, accuracy = 5.0)
            })
            db.close(); db = FitnessDatabase.create(context, name)
            assertEquals(listOf(1000L, 2000L), db.locationDao().points(1000, 3000).map { it.timestamp })
            assertTrue(db.locationDao().points(3001, 4000).isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

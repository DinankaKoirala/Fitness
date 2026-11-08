package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.data.FitnessDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkeletonTest {
    @Test fun schemaCreatesAndDataSurvivesReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Separate file so the launch database is never modified by this test.
        val name = "skeleton-test.db"
        context.deleteDatabase(name)
        fun open() = androidx.room.Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        try {
            open().withDatabase { database ->
                val sql = database.openHelper.writableDatabase
                val expected = setOf("UserProfile", "Food", "MealEntry", "StepDay", "LocationPoint",
                    "Segment", "Exercise", "Routine", "Workout", "SetEntry", "WeightEntry")
                val actual = mutableSetOf<String>()
                sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
                    while (it.moveToNext()) actual.add(it.getString(0))
                }
                assertTrue(actual.containsAll(expected))
                assertEquals(1, sql.version)
                sql.execSQL("INSERT INTO StepDay (date, steps, createdAt, updatedAt) VALUES ('2026-11-08', 123, 1, 1)")
                sql.query("PRAGMA foreign_keys").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                sql.query("PRAGMA index_list('LocationPoint')").use { assertTrue(it.moveToFirst()) }
            }
            open().withDatabase { database ->
                database.openHelper.readableDatabase.query("SELECT steps FROM StepDay WHERE date = '2026-11-08'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(123L, it.getLong(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

private inline fun <T> FitnessDatabase.withDatabase(block: (FitnessDatabase) -> T): T {
    return try { block(this) } finally { close() }
}

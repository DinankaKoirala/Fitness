package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.activity.*
import com.example.fitness.data.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SegmentRangeTest {
    @Test fun partialQueriesNeverReplaceWholeDayHistoryAndCorrectionsPersist() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "segment-range.db"
        context.deleteDatabase(name)
        val db = FitnessDatabase.create(context, name)
        try {
            val range = dayRange(LocalDate.parse("2026-11-08"))
            val start = range.from + 12 * 3600_000L
            db.locationDao().insert(listOf(
                LocationPoint(timestamp = start, lat = 27.7, lon = 85.3, speed = 1.2, accuracy = 1.0),
                LocationPoint(timestamp = start + 10_000, lat = 27.7001, lon = 85.3, speed = 1.2, accuracy = 1.0)))
            val repo = SegmentRepository(db)
            val whole = repo.observe(range).first()
            val cached = db.segmentDao().segments(range.from, range.to)
            assertEquals(1, cached.size)
            val partial = repo.observe(TimeRange(start + 2500, start + 7500)).first()
            assertEquals(whole.stats.distance / 2, partial.stats.distance, 0.001)
            assertEquals(cached, db.segmentDao().segments(range.from, range.to))
            repo.correct(partial.segments.single(), SegmentType.VEHICLE)
            val corrected = repo.observe(range).first()
            assertTrue(corrected.stats.vehicle > 0)
            assertEquals(whole.stats.distance, corrected.stats.distance, 0.001)
            assertTrue(corrected.segments.any { !it.estimated })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

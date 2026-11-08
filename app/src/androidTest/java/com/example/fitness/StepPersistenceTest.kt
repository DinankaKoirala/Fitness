package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.activity.StepReading
import com.example.fitness.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepPersistenceTest {
    @Test fun stepTotalsAndBaselinesSurviveReopenAndSimulatedReboot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "step-persistence.db"
        context.deleteDatabase(name)
        var db = FitnessDatabase.create(context, name)
        try {
            var steps = StepRepository(db)
            steps.record(StepReading("2026-11-08", 1000, 1, 1000))
            steps.record(StepReading("2026-11-08", 1030, 1, 2000))
            db.close(); db = FitnessDatabase.create(context, name); steps = StepRepository(db)
            steps.record(StepReading("2026-11-08", 1040, 1, 3000))
            assertEquals(40L, steps.observeDay("2026-11-08").first()!!.steps)
            steps.record(StepReading("2026-11-08", 5, 2, 4000))
            assertEquals(45L, steps.observeDay("2026-11-08").first()!!.steps)
            steps.correct("2026-11-08", 500)
            steps.record(StepReading("2026-11-08", 8, 2, 5000))
            assertEquals(503L, steps.observeDay("2026-11-08").first()!!.steps)
            steps.record(StepReading("2026-11-09", 10, 2, 6000))
            assertEquals(503L, steps.observeDay("2026-11-08").first()!!.steps)
            assertTrue(steps.observeDay("2026-11-09").first()!!.estimated)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

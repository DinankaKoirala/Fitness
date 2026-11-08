package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.backup.*
import com.example.fitness.data.*
import com.example.fitness.activity.TimeRange
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(db: FitnessDatabase) {
        val food = Food(name = "Oats, \"plain\"", servingAmount = 100.0, servingUnit = "g", nutrients = Nutrients(400.0, 20.0, 10.0, 60.0, fiberG = 8.0))
        val repo = FoodRepository(db)
        val id = repo.saveFood(food)
        repo.addEntry(id, "2026-11-08", MealType.BREAKFAST, 50.0, "g")
        db.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO UserProfile VALUES (1, 175, 70, 30, 'MALE', 'MODERATE', 2500, 8000, 1, 1)")
            execSQL("INSERT INTO StepDay VALUES ('2026-11-08', 4321, 1, 1, 0)")
            execSQL("INSERT INTO WeightEntry VALUES (1, '2026-11-08', 70, 1, 1)")
            execSQL("INSERT INTO Exercise VALUES (1, 'Squat', 'legs', 'barbell', 1, 1, 1)")
            execSQL("INSERT INTO Routine VALUES (1, 'Legs', 1, 1)")
            execSQL("INSERT INTO RoutineExercise VALUES (1, 0, 1, 1)")
            execSQL("INSERT INTO Workout (id, routineId, startedAt, endedAt, createdAt, updatedAt, name) VALUES (1, 1, 1000, 2000, 1, 1, 'Legs')")
            execSQL("INSERT INTO WorkoutExercise VALUES (1, 1, 0, 1)")
            execSQL("INSERT INTO SetEntry VALUES (1, 1, 1, 10, 50, 1500, 1, 1)")
            execSQL("INSERT INTO StepCounterState VALUES (1, '2026-11-08', 5000, 1, 1000, 1, 1)")
            execSQL("INSERT INTO StepInterval VALUES (1, 1000, 2000, 10, 1)")
            execSQL("INSERT INTO SegmentLabel VALUES (1, 1000, 2000, 'WALKING', 1, 1)")
            execSQL("INSERT INTO Segment VALUES (1, 1000, 2000, 'WALKING', 10, 1, 1, 1, 10)")
            execSQL("INSERT INTO LocationPoint VALUES (1, 1000, 27.7, 85.3, 1.5, 5, 1)")
            execSQL("INSERT INTO LocationPoint VALUES (2, 2000, 27.7001, 85.3, 1.5, 5, 1)")
            execSQL("INSERT INTO LocationPoint VALUES (3, 300000, 27.7002, 85.3, NULL, 5, 1)")
            execSQL("INSERT INTO QueuedAiRequest VALUES (1, 'oats', NULL, NULL, 1, 1)")
        }
    }
    @Test fun fullBackupFreshDatabaseRoundTripAndInterruptedRecovery() = runBlocking {
        val source = FitnessDatabase.create(context, "backup-source-test.db")
        val target = FitnessDatabase.create(context, "backup-target-test.db")
        val media = java.io.File(context.cacheDir, "backup-media-test").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getFilesDir() = media
            override fun getNoBackupFilesDir() = java.io.File(media, "journals").apply { mkdirs() }
        }
        val imageName = "${java.util.UUID.randomUUID()}.jpg"
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val oldSettings = readSettings(context)
        try {
            fixture(source)
            val image = java.io.File(media, "ai_images/$imageName").apply { parentFile!!.mkdirs(); writeBytes(imageBytes) }
            source.openHelper.writableDatabase.execSQL("UPDATE QueuedAiRequest SET imagePath = ?", arrayOf(imageName))
            writeSettings(context, JSONObject().put("manual_calorie_goal_kcal", 2200).put("battery_saver", true))
            val out = ByteArrayOutputStream(); BackupManager(isolated, source).export(out)
            assertFalse(out.toString("UTF-8").contains("provider_keys"))
            writeSettings(context, JSONObject().put("manual_calorie_goal_kcal", 999))
            val manager = BackupManager(isolated, target) { error("Simulated process interruption after DB commit") }
            val stage = manager.stage(out.toByteArray().inputStream())
            assertEquals(1L, stage.counts["MealEntry"])
            try { manager.restore(stage); fail("Expected injected failure") } catch (_: IllegalStateException) { }
            image.delete()
            BackupManager(isolated, target).recover()
            assertArrayEquals(imageBytes, java.io.File(media, "ai_images/$imageName").readBytes())
            assertEquals(2200, readSettings(context).getInt("manual_calorie_goal_kcal"))
            val restored = ByteArrayOutputStream(); BackupManager(isolated, target).export(restored)
            val before = JSONObject(out.toString("UTF-8")); val after = JSONObject(restored.toString("UTF-8"))
            assertEquals(before.getJSONArray("tables").toString(), after.getJSONArray("tables").toString())
            assertEquals(before.getJSONObject("settings").toString(), after.getJSONObject("settings").toString())
            assertEquals(200.0, target.foodDao().entry(1)!!.snapshot!!.nutrients.caloriesKcal * 0.5, 0.0)
        } finally { writeSettings(context, oldSettings); source.close(); target.close(); media.deleteRecursively(); context.deleteDatabase("backup-source-test.db"); context.deleteDatabase("backup-target-test.db") }
    }
    @Test fun corruptBackupRejectedAndCsvGpxRespectContentAndRange() = runBlocking {
        val db = FitnessDatabase.create(context, "backup-format-test.db")
        try {
            fixture(db)
            val manager = BackupManager(context, db)
            val out = ByteArrayOutputStream(); manager.export(out)
            val corrupt = JSONObject(out.toString("UTF-8"))
            val tables = corrupt.getJSONArray("tables")
            for (i in 0 until tables.length()) if (tables.getJSONObject(i).getString("name") == "MealEntry") tables.getJSONObject(i).getJSONArray("rows").getJSONObject(0).put("foodId", 999)
            try { manager.stage(corrupt.toString().byteInputStream()); fail("Broken reference accepted") } catch (_: Exception) { }
            assertNotNull(db.foodDao().entry(1))
            corrupt.put("formatVersion", 999)
            try { manager.stage(corrupt.toString().byteInputStream()); fail("Unsupported format accepted") } catch (_: Exception) { }
            val csv = ByteArrayOutputStream()
            val day = com.example.fitness.activity.dayRange(LocalDate.parse("2026-11-08"))
            TabularExport(db).csv(csv, "Food log", day)
            assertTrue(csv.toString("UTF-8").contains("\"Oats, \"\"plain\"\"\""))
            assertTrue(csv.toString("UTF-8").contains("200.0,10.0,5.0,30.0,4.0"))
            val gpx = ByteArrayOutputStream(); TabularExport(db).gpx(gpx, TimeRange(1000, 300001))
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val xml = factory.newDocumentBuilder().parse(gpx.toByteArray().inputStream())
            assertEquals(3, xml.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").length)
            assertEquals(2, xml.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkseg").length)
            assertEquals(2, xml.getElementsByTagNameNS("urn:fitness:extensions:1", "speed").length)
            assertEquals(0, xml.getElementsByTagName("ele").length)
            val clipped = ByteArrayOutputStream(); TabularExport(db).gpx(clipped, TimeRange(1000, 2000))
            assertEquals(1, factory.newDocumentBuilder().parse(clipped.toByteArray().inputStream()).getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").length)
        } finally { db.close(); context.deleteDatabase("backup-format-test.db") }
    }
    @Test fun storageFailureRollsBackReplacementAndRetainsPreviousSettings() = runBlocking {
        val source = FitnessDatabase.create(context, "backup-fail-source.db")
        val target = FitnessDatabase.create(context, "backup-fail-target.db")
        val old = readSettings(context)
        try {
            fixture(source)
            target.openHelper.writableDatabase.execSQL("INSERT INTO WeightEntry VALUES (1, '2026-01-01', 85, 1, 1)")
            val out = ByteArrayOutputStream(); BackupManager(context, source).export(out)
            writeSettings(context, JSONObject().put("manual_calorie_goal_kcal", 1800))
            val manager = BackupManager(context, target, beforeCommit = { throw android.database.sqlite.SQLiteFullException("Injected disk full") })
            val stage = manager.stage(out.toByteArray().inputStream())
            try { manager.restore(stage); fail("Expected disk-full simulation") } catch (_: android.database.sqlite.SQLiteFullException) { }
            BackupManager(context, target).recover()
            target.openHelper.readableDatabase.query("SELECT date, weightKg FROM WeightEntry").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("2026-01-01", c.getString(0)); assertEquals(85.0, c.getDouble(1), 0.0); assertFalse(c.moveToNext())
            }
            assertEquals(1800, readSettings(context).getInt("manual_calorie_goal_kcal"))
            assertNull(target.foodDao().entry(1))
        } finally { writeSettings(context, old); source.close(); target.close(); context.deleteDatabase("backup-fail-source.db"); context.deleteDatabase("backup-fail-target.db") }
    }

}

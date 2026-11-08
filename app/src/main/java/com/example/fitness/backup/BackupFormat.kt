package com.example.fitness.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.*
import java.time.LocalDate
import org.json.JSONObject

internal val backupTables = listOf("UserProfile", "Food", "StepDay", "LocationPoint", "Segment", "Exercise", "Routine", "Workout", "WeightEntry",
    "StepCounterState", "StepInterval", "SegmentLabel", "RoutineExercise", "WorkoutExercise", "SetEntry", "MealEntry", "QueuedAiRequest")
internal data class BackupColumn(val name: String, val type: String, val required: Boolean)
internal fun columns(db: SupportSQLiteDatabase, table: String): List<BackupColumn> = db.query("PRAGMA table_info(`$table`)").use { c ->
    buildList { while (c.moveToNext()) add(BackupColumn(c.getString(1), c.getString(2), c.getInt(3) == 1)) }
}
internal fun writeRow(writer: JsonWriter, cursor: Cursor) {
    writer.beginObject()
    cursor.columnNames.forEachIndexed { index, name ->
        writer.name(name)
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> writer.nullValue()
            Cursor.FIELD_TYPE_INTEGER -> writer.value(cursor.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> writer.value(cursor.getDouble(index))
            Cursor.FIELD_TYPE_STRING -> writer.value(cursor.getString(index))
            else -> error("Unsupported backup column.")
        }
    }
    writer.endObject()
}
internal fun readRow(reader: JsonReader, metadata: List<BackupColumn>): ContentValues {
    val byName = metadata.associateBy { it.name }
    val seen = mutableSetOf<String>()
    val row = ContentValues()
    reader.beginObject()
    while (reader.hasNext()) {
        val name = reader.nextName()
        val column = requireNotNull(byName[name]) { "Unknown backup column." }
        require(seen.add(name)) { "Duplicate backup column." }
        if (reader.peek() == JsonToken.NULL) { require(!column.required); reader.nextNull(); row.putNull(name) }
        else when (column.type.uppercase()) {
            "INTEGER" -> {
                require(reader.peek() == JsonToken.NUMBER)
                row.put(name, requireNotNull(reader.nextString().toLongOrNull()) { "Invalid integer." })
            }
            "REAL" -> {
                require(reader.peek() == JsonToken.NUMBER)
                val number = reader.nextDouble(); require(number.isFinite()); row.put(name, number)
            }
            "TEXT" -> { require(reader.peek() == JsonToken.STRING); val text = reader.nextString(); require(text.length <= 100_000); row.put(name, text) }
            else -> error("Unsupported backup column.")
        }
    }
    reader.endObject()
    require(seen == byName.keys) { "Backup row is missing fields." }
    return row
}
internal fun readPreferences(reader: JsonReader): JSONObject {
    val result = JSONObject(); reader.beginObject()
    while (reader.hasNext()) {
        val name = reader.nextName(); require(!result.has(name) && name in backupSettings)
        when (reader.peek()) {
            JsonToken.STRING -> result.put(name, reader.nextString())
            JsonToken.NUMBER -> result.put(name, reader.nextLong())
            JsonToken.BOOLEAN -> result.put(name, reader.nextBoolean())
            else -> error("Invalid backup setting.")
        }
    }
    reader.endObject(); validateSettings(result); return result
}
internal fun checkData(db: SupportSQLiteDatabase) {
    fun rejectIf(query: String) { db.query(query).use { require(!it.moveToFirst()) { "Backup contains invalid fitness records." } } }
    db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Backup contains broken relationships." } }
    rejectIf("SELECT id FROM UserProfile WHERE id != 1 OR heightCm <= 0 OR weightKg <= 0 OR age <= 0 OR calorieGoal <= 0 OR stepGoal < 0 OR sex NOT IN ('MALE','FEMALE') OR activityLevel NOT IN ('SEDENTARY','LIGHT','MODERATE','VERY_ACTIVE','EXTRA_ACTIVE')")
    rejectIf("SELECT id FROM Food WHERE id <= 0 OR length(trim(name)) = 0 OR length(name) > 200 OR servingAmount < 0.000001 OR servingAmount > 1000000000 OR servingUnit NOT IN ('g','ml','serving') OR (servingUnit = 'serving' AND servingAmount != 1) OR source NOT IN ('MANUAL','OFF','USDA','SWISS','AI','OCR') OR estimated NOT IN (0,1)")
    val nutrientNames = listOf("caloriesKcal", "proteinG", "fatG", "carbsG", "fiberG", "sodiumMg", "calciumMg", "ironMg", "potassiumMg", "magnesiumMg", "vitaminCMg", "vitaminDMcg")
    rejectIf("SELECT id FROM Food WHERE " + nutrientNames.joinToString(" OR ") { "`$it` < 0 OR `$it` > 1000000000" })
    rejectIf("SELECT id FROM MealEntry WHERE id <= 0 OR amount < 0.000001 OR amount > 1000000000 OR mealType NOT IN ('BREAKFAST','LUNCH','DINNER','SNACKS') OR snapshot_name IS NULL OR snapshot_servingAmount IS NULL OR snapshot_servingUnit IS NULL OR snapshot_source IS NULL OR snapshot_estimated IS NULL OR snapshot_caloriesKcal IS NULL OR snapshot_proteinG IS NULL OR snapshot_fatG IS NULL OR snapshot_carbsG IS NULL OR snapshot_servingAmount <= 0 OR (unit != 'serving' AND unit != snapshot_servingUnit) OR snapshot_servingUnit NOT IN ('g','ml','serving') OR snapshot_source NOT IN ('MANUAL','OFF','USDA','SWISS','AI','OCR') OR snapshot_estimated NOT IN (0,1)")
    rejectIf("SELECT id FROM MealEntry WHERE " + nutrientNames.joinToString(" OR ") { "snapshot_$it < 0 OR snapshot_$it > 1000000000" })
    rejectIf("SELECT date FROM StepDay WHERE steps < 0 OR estimated NOT IN (0,1)")
    rejectIf("SELECT id FROM WeightEntry WHERE weightKg <= 0 OR weightKg > 1000")
    rejectIf("SELECT id FROM LocationPoint WHERE timestamp <= 0 OR lat < -90 OR lat > 90 OR lon < -180 OR lon > 180 OR accuracy < 0 OR (speed IS NOT NULL AND speed < 0)")
    rejectIf("SELECT id FROM Segment WHERE end <= start OR distance < 0 OR maxSpeed < 0 OR type NOT IN ('STILL','WALKING','VEHICLE') OR estimated NOT IN (0,1)")
    rejectIf("SELECT id FROM SegmentLabel WHERE end <= start OR type NOT IN ('STILL','WALKING','VEHICLE')")
    rejectIf("SELECT id FROM StepInterval WHERE end <= start OR steps < 0")
    rejectIf("SELECT id FROM SetEntry WHERE reps < 1 OR reps > 1000 OR weightKg < 0 OR weightKg > 1000")
    rejectIf("SELECT id FROM Workout WHERE endedAt IS NOT NULL AND endedAt < startedAt")
    db.query("SELECT COUNT(*) FROM Workout WHERE endedAt IS NULL").use { it.moveToFirst(); require(it.getInt(0) <= 1) }
    listOf("MealEntry", "StepDay", "WeightEntry", "StepCounterState").forEach { table ->
        db.query("SELECT DISTINCT date FROM `$table`").use { while (it.moveToNext()) { val raw = it.getString(0); require(LocalDate.parse(raw).toString() == raw) } }
    }
}

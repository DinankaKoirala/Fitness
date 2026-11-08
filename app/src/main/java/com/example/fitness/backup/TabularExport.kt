package com.example.fitness.backup

import android.util.Xml
import com.example.fitness.activity.TimeRange
import com.example.fitness.data.*
import com.example.fitness.food.scaled
import java.io.OutputStream
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun csvCell(value: Any?): String {
    if (value == null) return ""
    var text = value.toString()
    if (value is String && text.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) text = "'$text"
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${text.replace("\"", "\"\"")}\"" else text
}
fun csvRow(writer: Writer, values: List<Any?>) { writer.write(values.joinToString(",", transform = ::csvCell)); writer.write("\r\n") }

class TabularExport(private val database: FitnessDatabase) {
    suspend fun csv(output: OutputStream, type: String, range: TimeRange) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(range.from).atZone(zone).toLocalDate().toString()
        val to = Instant.ofEpochMilli(range.to - 1).atZone(zone).toLocalDate().toString()
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            if (type == "Food log") {
                val columns = listOf("id", "date", "meal", "food", "amount", "unit", "source", "estimated", "kcal", "protein_g", "fat_g", "carbs_g", "fiber_g", "sodium_mg", "calcium_mg", "iron_mg", "potassium_mg", "magnesium_mg", "vitamin_c_mg", "vitamin_d_mcg", "created_at_utc", "updated_at_utc")
                csvRow(writer, columns)
                database.openHelper.readableDatabase.query("SELECT * FROM MealEntry WHERE date >= ? AND date <= ? ORDER BY date, createdAt, id", arrayOf(from, to)).use { c ->
                    while (c.moveToNext()) {
                        fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name))
                        fun d(name: String) = c.getDouble(c.getColumnIndexOrThrow(name))
                        fun n(name: String): Double? { val i = c.getColumnIndexOrThrow(name); return if (c.isNull(i)) null else c.getDouble(i) }
                        val amount = d("amount"); val unit = s("unit")
                        val nutrition = FoodSnapshot(s("snapshot_name"), d("snapshot_servingAmount"), s("snapshot_servingUnit"), FoodSource.valueOf(s("snapshot_source")), c.getInt(c.getColumnIndexOrThrow("snapshot_estimated")) == 1,
                            Nutrients(d("snapshot_caloriesKcal"), d("snapshot_proteinG"), d("snapshot_fatG"), d("snapshot_carbsG"), n("snapshot_fiberG"), n("snapshot_sodiumMg"), n("snapshot_calciumMg"), n("snapshot_ironMg"), n("snapshot_potassiumMg"), n("snapshot_magnesiumMg"), n("snapshot_vitaminCMg"), n("snapshot_vitaminDMcg"))).scaled(amount, unit)
                        csvRow(writer, listOf(c.getLong(c.getColumnIndexOrThrow("id")), s("date"), s("mealType"), s("snapshot_name"), amount, unit, s("snapshot_source"), c.getInt(c.getColumnIndexOrThrow("snapshot_estimated")),
                            nutrition.caloriesKcal, nutrition.proteinG, nutrition.fatG, nutrition.carbsG, nutrition.fiberG, nutrition.sodiumMg, nutrition.calciumMg, nutrition.ironMg, nutrition.potassiumMg, nutrition.magnesiumMg, nutrition.vitaminCMg, nutrition.vitaminDMcg,
                            Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("createdAt"))), Instant.ofEpochMilli(c.getLong(c.getColumnIndexOrThrow("updatedAt")))))
                    }
                }
            } else {
                val query: String
                val args: Array<Any>
                when (type) {
                    "Steps" -> { query = "SELECT * FROM StepDay WHERE date >= ? AND date <= ? ORDER BY date"; args = arrayOf(from, to) }
                    "Weight" -> { query = "SELECT * FROM WeightEntry WHERE date >= ? AND date <= ? ORDER BY date, createdAt"; args = arrayOf(from, to) }
                    "Gym" -> { query = "SELECT SetEntry.*, Exercise.name AS exerciseName, Workout.name AS workoutName FROM SetEntry JOIN Exercise ON Exercise.id = SetEntry.exerciseId JOIN Workout ON Workout.id = SetEntry.workoutId WHERE performedAt >= ? AND performedAt < ? ORDER BY performedAt, SetEntry.id"; args = arrayOf(range.from, range.to) }
                    else -> error("Choose Food log, Steps, Weight or Gym.")
                }
                database.openHelper.readableDatabase.query(query, args).use { c ->
                    csvRow(writer, c.columnNames.toList())
                    while (c.moveToNext()) csvRow(writer, c.columnNames.indices.map { i -> when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        else -> c.getString(i)
                    } })
                }
            }
        }
    }
    suspend fun gpx(output: OutputStream, range: TimeRange) = withContext(Dispatchers.IO) {
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            val xml = Xml.newSerializer(); xml.setOutput(writer)
            val ns = "http://www.topografix.com/GPX/1/1"; val ext = "urn:fitness:extensions:1"
            xml.startDocument("UTF-8", true); xml.setPrefix("", ns); xml.setPrefix("fitness", ext)
            xml.startTag(ns, "gpx").attribute(null, "version", "1.1").attribute(null, "creator", "Fitness")
            xml.startTag(ns, "trk").startTag(ns, "name").text("Fitness recorded route").endTag(ns, "name")
            var open = false; var previous: Long? = null
            database.openHelper.readableDatabase.query("SELECT * FROM LocationPoint WHERE timestamp >= ? AND timestamp < ? ORDER BY timestamp, id", arrayOf(range.from, range.to)).use { c ->
                while (c.moveToNext()) {
                    val time = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    if (previous != null && time <= previous!!) continue
                    if (!open || previous != null && time - previous!! > 180_000) {
                        if (open) xml.endTag(ns, "trkseg")
                        xml.startTag(ns, "trkseg"); open = true
                    }
                    xml.startTag(ns, "trkpt").attribute(null, "lat", c.getDouble(c.getColumnIndexOrThrow("lat")).toString())
                        .attribute(null, "lon", c.getDouble(c.getColumnIndexOrThrow("lon")).toString())
                        .startTag(ns, "time").text(Instant.ofEpochMilli(time).toString()).endTag(ns, "time")
                    xml.startTag(ns, "extensions")
                    val speed = c.getColumnIndexOrThrow("speed")
                    if (!c.isNull(speed)) xml.startTag(ext, "speed").text(c.getDouble(speed).toString()).endTag(ext, "speed")
                    xml.startTag(ext, "accuracy").text(c.getDouble(c.getColumnIndexOrThrow("accuracy")).toString()).endTag(ext, "accuracy")
                    xml.endTag(ns, "extensions").endTag(ns, "trkpt"); previous = time
                }
            }
            if (open) xml.endTag(ns, "trkseg")
            xml.endTag(ns, "trk").endTag(ns, "gpx"); xml.endDocument(); xml.flush()
        }
    }
}

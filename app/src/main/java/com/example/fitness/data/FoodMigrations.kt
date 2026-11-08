package com.example.fitness.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columns = listOf(
            "name" to "TEXT", "servingAmount" to "REAL", "servingUnit" to "TEXT",
            "source" to "TEXT", "estimated" to "INTEGER",
        ) + listOf("caloriesKcal", "proteinG", "fatG", "carbsG", "fiberG", "sodiumMg",
            "calciumMg", "ironMg", "potassiumMg", "magnesiumMg", "vitaminCMg", "vitaminDMcg")
            .map { it to "REAL" }
        columns.forEach { (name, type) -> db.execSQL("ALTER TABLE MealEntry ADD COLUMN snapshot_$name $type") }
        // Snapshot existing rows during migration, before any saved-food editing is possible.
        val values = columns.joinToString(", ") { (name, _) ->
            "snapshot_$name = (SELECT `$name` FROM Food WHERE Food.id = MealEntry.foodId)"
        }
        db.execSQL("UPDATE MealEntry SET $values")
    }
}

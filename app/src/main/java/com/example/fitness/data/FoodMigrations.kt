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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE StepDay ADD COLUMN estimated INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS StepCounterState (id INTEGER NOT NULL, date TEXT NOT NULL, rawCounter INTEGER NOT NULL, bootCount INTEGER NOT NULL, eventTime INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Segment ADD COLUMN estimated INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE Segment ADD COLUMN maxSpeed REAL NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS StepInterval (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, steps INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_StepInterval_start ON StepInterval(start)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_StepInterval_end ON StepInterval(end)")
        db.execSQL("CREATE TABLE IF NOT EXISTS SegmentLabel (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, type TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_SegmentLabel_start ON SegmentLabel(start)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_SegmentLabel_end ON SegmentLabel(end)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Workout ADD COLUMN name TEXT NOT NULL DEFAULT 'Workout'")
        db.execSQL("ALTER TABLE Workout ADD COLUMN restUntil INTEGER")
        db.execSQL("UPDATE Workout SET name = COALESCE((SELECT name FROM Routine WHERE Routine.id = Workout.routineId), 'Workout')")
        db.execSQL("CREATE TABLE IF NOT EXISTS RoutineExercise (routineId INTEGER NOT NULL, position INTEGER NOT NULL, exerciseId INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(routineId, position), FOREIGN KEY(routineId) REFERENCES Routine(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(exerciseId) REFERENCES Exercise(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_RoutineExercise_exerciseId ON RoutineExercise(exerciseId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS WorkoutExercise (workoutId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, position INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(workoutId, exerciseId), FOREIGN KEY(workoutId) REFERENCES Workout(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(exerciseId) REFERENCES Exercise(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorkoutExercise_exerciseId ON WorkoutExercise(exerciseId)")
        db.execSQL("INSERT INTO WorkoutExercise (workoutId, exerciseId, position, createdAt) SELECT workoutId, exerciseId, MIN(id), MIN(createdAt) FROM SetEntry GROUP BY workoutId, exerciseId")
    }
}

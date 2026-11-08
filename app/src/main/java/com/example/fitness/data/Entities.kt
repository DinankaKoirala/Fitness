package com.example.fitness.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Instant fields are UTC epoch milliseconds. Calendar dates use ISO-8601 local dates.
// Measurements: kg, cm, metres, m/s; nutrition amounts are per declared serving.
enum class FoodSource { MANUAL, OFF, USDA, SWISS, AI, OCR }
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACKS }
enum class SegmentType { STILL, WALKING, VEHICLE }

@Entity
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val sex: String,
    val activityLevel: String,
    val calorieGoal: Int,
    val stepGoal: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class Nutrients(
    val caloriesKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0,
    // Null means unknown, rather than a measured zero.
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
    val calciumMg: Double? = null,
    val ironMg: Double? = null,
    val potassiumMg: Double? = null,
    val magnesiumMg: Double? = null,
    val vitaminCMg: Double? = null,
    val vitaminDMcg: Double? = null,
)

@Entity(indices = [Index("createdAt")])
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servingAmount: Double,
    val servingUnit: String,
    @Embedded val nutrients: Nutrients,
    val source: FoodSource = FoodSource.MANUAL,
    val estimated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

// Immutable values used for a logged meal; saved-food edits never rewrite history.
data class FoodSnapshot(
    val name: String,
    val servingAmount: Double,
    val servingUnit: String,
    val source: FoodSource,
    val estimated: Boolean,
    @Embedded val nutrients: Nutrients,
)

fun Food.snapshot() = FoodSnapshot(name, servingAmount, servingUnit, source, estimated, nutrients)

@Entity(
    foreignKeys = [ForeignKey(entity = Food::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("foodId"), Index("date"), Index("createdAt")],
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val date: String,
    val mealType: MealType,
    val amount: Double,
    val unit: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @Embedded(prefix = "snapshot_") val snapshot: FoodSnapshot? = null,
)

@Entity(indices = [Index("createdAt")])
data class StepDay(
    @PrimaryKey val date: String,
    val steps: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "0") val estimated: Boolean = false,
)

@Entity(indices = [Index("timestamp")])
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val speed: Double?,
    val accuracy: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(indices = [Index("start"), Index("end")])
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val type: SegmentType,
    val distance: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "1") val estimated: Boolean = true,
    @ColumnInfo(defaultValue = "0") val maxSpeed: Double = 0.0,
)

@Entity(indices = [Index("createdAt")])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val custom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(indices = [Index("createdAt")])
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("routineId"), Index("startedAt")],
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "'Workout'") val name: String = "Workout",
    val restUntil: Long? = null,
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("workoutId"), Index("exerciseId"), Index("performedAt")],
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val reps: Int,
    val weightKg: Double,
    val performedAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(indices = [Index("date"), Index("createdAt")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity
data class StepCounterState(
    @PrimaryKey val id: Long = 1,
    val date: String,
    val rawCounter: Long,
    val bootCount: Int,
    val eventTime: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(indices = [Index("start"), Index("end")])
data class StepInterval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val steps: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(indices = [Index("start"), Index("end")])
data class SegmentLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val type: SegmentType,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(primaryKeys = ["routineId", "position"],
    foreignKeys = [ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("exerciseId")])
data class RoutineExercise(val routineId: Long, val position: Int, val exerciseId: Long,
    val createdAt: Long = System.currentTimeMillis())

@Entity(primaryKeys = ["workoutId", "exerciseId"],
    foreignKeys = [ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("exerciseId")])
data class WorkoutExercise(val workoutId: Long, val exerciseId: Long, val position: Int,
    val createdAt: Long = System.currentTimeMillis())

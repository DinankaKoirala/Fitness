package com.example.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, Food::class, MealEntry::class, StepDay::class,
        LocationPoint::class, Segment::class, Exercise::class, Routine::class,
        Workout::class, SetEntry::class, WeightEntry::class, StepCounterState::class],
    version = 3,
    exportSchema = true,
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun stepDao(): StepDao

    companion object {
        fun create(context: Context, name: String = "fitness.db"): FitnessDatabase = Room.databaseBuilder(
            context.applicationContext,
            FitnessDatabase::class.java,
            name,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        // No destructive fallback. Every future version must supply an explicit,
        // tested Migration and preserve existing user data. See schemas/README.md.
    }
}

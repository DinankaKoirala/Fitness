package com.example.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, Food::class, MealEntry::class, StepDay::class,
        LocationPoint::class, Segment::class, Exercise::class, Routine::class,
        Workout::class, SetEntry::class, WeightEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        fun create(context: Context): FitnessDatabase = Room.databaseBuilder(
            context.applicationContext,
            FitnessDatabase::class.java,
            "fitness.db",
        ).build()
        // No destructive fallback. Every future version must supply an explicit,
        // tested Migration and preserve existing user data. See schemas/README.md.
    }
}

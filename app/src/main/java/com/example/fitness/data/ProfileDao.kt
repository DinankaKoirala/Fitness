package com.example.fitness.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM UserProfile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM UserProfile WHERE id = 1")
    suspend fun getProfile(): UserProfile?

    @Upsert
    suspend fun saveProfile(profile: UserProfile)
    @Query("SELECT * FROM WeightEntry WHERE date = :date ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun weightOn(date: String): WeightEntry?
    @Upsert suspend fun saveWeight(weight: WeightEntry)
    @Transaction suspend fun saveProfileWithWeight(profile: UserProfile, date: String, recordWeight: Boolean) {
        saveProfile(profile)
        if (recordWeight) {
            val old = weightOn(date)
            saveWeight(WeightEntry(id = old?.id ?: 0, date = date, weightKg = profile.weightKg,
                createdAt = old?.createdAt ?: profile.updatedAt, updatedAt = profile.updatedAt))
        }
    }
}

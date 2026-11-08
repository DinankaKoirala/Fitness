package com.example.fitness.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM UserProfile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM UserProfile WHERE id = 1")
    suspend fun getProfile(): UserProfile?

    @Upsert
    suspend fun saveProfile(profile: UserProfile)
}

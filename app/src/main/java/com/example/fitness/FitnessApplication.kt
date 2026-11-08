package com.example.fitness

import android.app.Application
import com.example.fitness.data.FitnessDatabase
import com.example.fitness.data.StepRepository
import com.example.fitness.data.SegmentRepository
import com.example.fitness.data.GymRepository
import com.example.fitness.data.FoodRepository
import com.example.fitness.data.ProfileRepository
import com.example.fitness.data.GoalSettings
import com.example.fitness.data.goalDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.fitness.backup.BackupManager
import com.example.fitness.backup.StagedBackup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

class FitnessApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val gym by lazy { GymRepository(database, this) }
    val segments by lazy { SegmentRepository(database) }
    val steps by lazy { StepRepository(database) }
    val foods by lazy { FoodRepository(database) }
    val profiles by lazy { ProfileRepository(database.profileDao(), GoalSettings(goalDataStore), applicationScope) }
    val database by lazy { FitnessDatabase.create(this) }

    data class StorageState(val loading: Boolean = true, val error: String? = null, val notice: String? = null)
    private val storageState = MutableStateFlow(StorageState())
    val storage: StateFlow<StorageState> = storageState
    private val recovering = AtomicBoolean(false)
    @Volatile private var needsProfileRefresh = false
    fun clearStorageNotice() { storageState.value = storageState.value.copy(notice = null) }
    fun retryStorage() {
        if (!recovering.compareAndSet(false, true)) return
        storageState.value = StorageState()
        applicationScope.launch {
            try { database.openHelper.writableDatabase; BackupManager(this@FitnessApplication, database).recover(); if (needsProfileRefresh) { profiles.refreshAfterRestore(); needsProfileRefresh = false }; storageState.value = StorageState(loading = false) }
            catch (_: Exception) { storageState.value = StorageState(loading = false, error = "Could not open or recover local data. Free storage and try again; existing files are retained.") }
            finally { recovering.set(false) }
        }
    }
    fun restoreBackup(stage: StagedBackup) {
        needsProfileRefresh = true
        storageState.value = StorageState()
        applicationScope.launch {
            try {
                BackupManager(this@FitnessApplication, database).restore(stage)
                profiles.refreshAfterRestore()
                needsProfileRefresh = false
                storageState.value = StorageState(loading = false, notice = "Backup restored. Tracking is off; re-enable it in Activity when ready.")
            } catch (_: Exception) {
                if (!stage.handedOff) stage.discard(this@FitnessApplication)
                storageState.value = StorageState(loading = false, error = "Restore could not finish. Stop tracking, free storage, then try recovery. Existing files are retained.")
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        retryStorage()
    }
}

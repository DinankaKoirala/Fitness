package com.example.fitness.data

import com.example.fitness.profile.CalorieGoal
import com.example.fitness.profile.ProfileInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Measurements and calculated goal are one Room row. A manual override is an
// independent setting: profile edits never silently remove or overwrite it.
data class ProfileState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val manualGoal: Int? = null,
    val error: String? = null,
) {
    val goal: Int? get() = manualGoal ?: profile?.calorieGoal
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepository(
    private val dao: ProfileDao,
    private val settings: GoalSettings,
    scope: CoroutineScope,
) {
    private val reload = MutableStateFlow(0)
    private val saveMutex = Mutex()
    val state: StateFlow<ProfileState> = reload.flatMapLatest {
        combine(dao.observeProfile(), settings.override) { profile, manual ->
            ProfileState(loading = false, profile = profile, manualGoal = manual)
        }.catch { emit(ProfileState(loading = false, error = "Could not load your profile and goals. Try again.")) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun retry() { reload.value += 1 }

    suspend fun saveProfile(input: ProfileInput) = saveMutex.withLock {
        val old = dao.getProfile()
        val now = System.currentTimeMillis()
        dao.saveProfileWithWeight(UserProfile(
            heightCm = input.heightCm,
            weightKg = input.weightKg,
            age = input.age,
            sex = input.sex.name,
            activityLevel = input.activity.name,
            calorieGoal = CalorieGoal.maintenance(input),
            // Zero means unset; step goals belong to a future workflow.
            stepGoal = old?.stepGoal ?: 0,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        ), java.time.LocalDate.now().toString(), old == null || old.weightKg != input.weightKg)
    }

    suspend fun setManualGoal(kcal: Int?) = settings.setOverride(kcal)
}

package com.example.fitness.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fitness.core.ProviderKeysCard
import com.example.fitness.data.ProfileRepository
import com.example.fitness.data.ProfileState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(state: ProfileState, repository: ProfileRepository, onboarding: Boolean = false) {
    val profile = state.profile
    var height by rememberSaveable { mutableStateOf(profile?.heightCm?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(profile?.weightKg?.toString().orEmpty()) }
    var age by rememberSaveable { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var sex by rememberSaveable { mutableStateOf(profile?.sex.orEmpty()) }
    var activity by rememberSaveable { mutableStateOf(profile?.activityLevel.orEmpty()) }
    var manual by rememberSaveable { mutableStateOf(state.manualGoal?.toString().orEmpty()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var goalMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savingGoal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preview = runCatching { CalorieGoal.maintenance(parseProfile(height, weight, age, sex, activity)) }.getOrNull()

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (onboarding) "Make it yours" else "Profile", style = MaterialTheme.typography.headlineLarge)
        Text(if (onboarding) "Add your details to estimate a daily calorie goal. You can edit everything later."
             else "Your details and daily goal. Changes stay on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = height, onValueChange = { height = it; message = null },
            label = { Text("Height (cm)") }, singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("height"),
        )
        OutlinedTextField(
            value = weight, onValueChange = { weight = it; message = null },
            label = { Text("Weight (kg)") }, singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("weight"),
        )
        OutlinedTextField(
            value = age, onValueChange = { age = it; message = null },
            label = { Text("Age (years)") }, singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("age"),
        )
        Text("Sex input for the equation", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EquationSex.entries.forEach { option ->
                FilterChip(selected = sex == option.name, enabled = !saving,
                    onClick = { sex = option.name; message = null }, label = { Text(option.label) })
            }
        }
        ActivityPicker(activity, !saving) { activity = it; message = null }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estimated maintenance goal", style = MaterialTheme.typography.titleMedium)
                Text(preview?.let { "$it kcal / day" } ?: "Complete your details to see an estimate",
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("estimate"))
                Text("Mifflin–St Jeor × activity level. This is an estimate, not a measured calorie requirement.",
                    style = MaterialTheme.typography.bodySmall)
                if (state.manualGoal != null) Text("Your manual goal stays active when you save these details.")
            }
        }
        Button(
            enabled = !saving && !savingGoal,
            onClick = {
                val input = try { parseProfile(height, weight, age, sex, activity) }
                    catch (error: IllegalArgumentException) { message = error.message; return@Button }
                saving = true
                message = null
                scope.launch {
                    try {
                        repository.saveProfile(input)
                        message = "Profile saved"
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        message = "Could not save your profile. Your entries are still here; try again."
                    } finally { saving = false }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("saveProfile"),
        ) { Text(if (saving) "Saving…" else if (onboarding) "Save and continue" else "Save profile") }

        message?.let { Text(it, modifier = Modifier.testTag("profileMessage")) }

        if (!onboarding) {
            HorizontalDivider()
            Text("Daily calorie goal", style = MaterialTheme.typography.titleLarge)
            Text("${state.goal} kcal / day · ${if (state.manualGoal == null) "estimated" else "manual"}",
                modifier = Modifier.testTag("activeGoal"))
            Text("Use your own target, or return to the estimate calculated from your saved profile.")
            OutlinedTextField(value = manual, onValueChange = { manual = it; goalMessage = null },
                label = { Text("Manual goal (kcal / day)") }, singleLine = true, enabled = !savingGoal,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("manualGoal"))
            Button(enabled = !saving && !savingGoal, onClick = {
                val kcal = try { parseManualGoal(manual) }
                    catch (error: IllegalArgumentException) { goalMessage = error.message; return@Button }
                savingGoal = true
                scope.launch {
                    try { repository.setManualGoal(kcal); goalMessage = "Manual goal saved" }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        goalMessage = "Could not save your goal. Try again."
                    } finally { savingGoal = false }
                }
            }, modifier = Modifier.fillMaxWidth().testTag("saveManualGoal")) {
                Text(if (savingGoal) "Saving…" else "Use manual goal")
            }
            OutlinedButton(enabled = state.manualGoal != null && !saving && !savingGoal, onClick = {
                savingGoal = true
                scope.launch {
                    try { repository.setManualGoal(null); manual = ""; goalMessage = "Estimated goal restored" }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        goalMessage = "Could not restore the estimate. Try again."
                    } finally { savingGoal = false }
                }
            }, modifier = Modifier.fillMaxWidth().testTag("restoreEstimate")) { Text("Use estimated goal") }
            goalMessage?.let { Text(it, modifier = Modifier.testTag("goalMessage")) }
            ProviderKeysCard()
        }

    }
}

@Composable
private fun ActivityPicker(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = ActivityLevel.entries.find { it.name == selected }
    Column {
        Text("Activity level", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("activityPicker")) {
                Text(current?.let { "${it.label} · ${it.factor}×" } ?: "Choose activity level")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ActivityLevel.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Column { Text("${option.label} · ${option.factor}×"); Text(option.detail, style = MaterialTheme.typography.bodySmall) } },
                        onClick = { onSelect(option.name); expanded = false },
                        modifier = Modifier.testTag("activity_${option.name}"),
                    )
                }
            }
        }
    }
}

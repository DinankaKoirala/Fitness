package com.example.fitness.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.backup.BackupCard
import com.example.fitness.core.ProviderKeysCard
import com.example.fitness.data.ActivitySettings
import com.example.fitness.food.FoodDialog
import com.example.fitness.food.lookup.LookupMode
import com.example.fitness.food.lookup.LookupSettings
import kotlinx.coroutines.*

@Composable
fun SettingsButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("openSettings")) { Text("Settings, backup and export") }
    if (open) SettingsScreen { open = false }
}

@Composable
internal fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { ActivitySettings(context) }
    val lookup = remember { LookupSettings(context) }
    val saver by activity.batterySaver.collectAsStateWithLifecycle(initialValue = false)
    val mode by lookup.mode.collectAsStateWithLifecycle(initialValue = LookupMode.DATABASE_FIRST)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notices by rememberSaveable { mutableStateOf(false) }
    fun save(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { block(); error = null }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not save this setting. Check free storage and try again." }
            finally { busy = false }
        }
    }
    FoodDialog("Settings", onClose = onClose) {
        ProviderKeysCard()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Food lookup", style = MaterialTheme.typography.titleLarge)
                LookupMode.entries.forEach { value ->
                    FilterChip(selected = mode == value, enabled = !busy, onClick = { save { lookup.mode(value) } }, label = { Text(value.label) }, modifier = Modifier.testTag("settingsMode_${value.name}"))
                }
                Text("Manual logging and the bundled Swiss database work offline. AI estimates need your key and confirmation. Queued AI retries run only when you request them while the app is visible.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tracking battery saver", modifier = Modifier.weight(1f))
                    Switch(checked = saver, enabled = !busy, onCheckedChange = { save { activity.batterySaver(it) } }, modifier = Modifier.testTag("settingsBatterySaver"))
                }
                Text("Uses longer GPS intervals while moving. Tracking controls and permission guidance are in Activity. Battery usage depends on the device; a physical full-day measurement is still pending.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        BackupCard()
        Text("About Fitness", style = MaterialTheme.typography.titleLarge)
        Text("Version 0.1.0 · Android 8+ · metric units\nLocal fitness records, no account or analytics. Explicit provider searches and viewed maps use the internet only while the app is visible. Device cloud backup is disabled; use your own JSON export.")
        Text("Nutrition, calories, automatic motion labels and AI output are estimates. Unknown micronutrients remain unknown. Review and edit estimates before relying on them.")
        Text("Swiss Food Composition Database: Federal Food Safety and Veterinary Office (FSVO), version 7.1. Exercise definitions: free-exercise-db. Street maps © OpenStreetMap contributors; satellite and terrain © MapTiler with your key.")
        TextButton(onClick = { notices = true }, modifier = Modifier.testTag("openLicenses")) { Text("Bundled licenses and data notices") }
    }
    if (notices) {
        var text by remember { mutableStateOf("Loading licenses…") }
        LaunchedEffect(Unit) {
            text = withContext(Dispatchers.IO) {
                buildString {
                    context.assets.list("").orEmpty().filter { it.endsWith(".txt") }.sorted().forEach { name ->
                        appendLine(name); appendLine(context.assets.open(name).bufferedReader().use { it.readText() }); appendLine()
                    }
                    listOf("exercises/NOTICE.txt", "foods/NOTICE.txt").forEach { name ->
                        runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()?.let { appendLine(it) }
                    }
                }
            }
        }
        FoodDialog("Licenses and notices", onClose = { notices = false }) { Text(text, style = MaterialTheme.typography.bodySmall) }
    }
}

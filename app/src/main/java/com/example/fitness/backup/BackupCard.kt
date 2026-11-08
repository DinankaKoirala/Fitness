package com.example.fitness.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.fitness.FitnessApplication
import com.example.fitness.activity.*
import com.example.fitness.core.TimeRangePicker
import java.time.LocalDate
import kotlinx.coroutines.*

@Composable
fun BackupCard() {
    val context = LocalContext.current
    val app = context.applicationContext as FitnessApplication
    val manager = remember { BackupManager(app, app.database) }
    val exporter = remember { TabularExport(app.database) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var range by remember { mutableStateOf<TimeRange?>(dayRange(LocalDate.parse(date))) }
    var csvType by rememberSaveable { mutableStateOf("Food log") }
    var staged by remember { mutableStateOf<StagedBackup?>(null) }
    DisposableEffect(Unit) { onDispose { staged?.takeIf { !it.handedOff }?.discard(app) } }
    fun operation(block: suspend () -> Unit) {
        busy = true; message = null
        scope.launch {
            try { block() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message = "Could not complete the file operation. Check the file, available storage and whether tracking has stopped." }
            finally { busy = false }
        }
    }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { operation {
        manager.export(requireNotNull(context.contentResolver.openOutputStream(it, "wt"))); message = "Full JSON backup saved."
    } } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { operation {
        exporter.csv(requireNotNull(context.contentResolver.openOutputStream(it, "wt")), csvType, requireNotNull(range)); message = "$csvType CSV saved."
    } } }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri -> uri?.let { operation {
        exporter.gpx(requireNotNull(context.contentResolver.openOutputStream(it, "wt")), requireNotNull(range)); message = "GPX route saved."
    } } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { operation {
        staged?.discard(app)
        staged = manager.stage(requireNotNull(context.contentResolver.openInputStream(it)))
        message = "Backup validated. Review the record counts before replacing data."
    } } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Export and restore", style = MaterialTheme.typography.titleLarge)
            Text("Backups contain personal fitness and route data. Choose the file destination. Provider keys and device permissions are excluded; restored tracking starts off.")
            Button(enabled = !busy, onClick = { json.launch("fitness-backup-${LocalDate.now()}.json") }, modifier = Modifier.testTag("exportJson")) { Text("Full JSON backup") }
            OutlinedButton(enabled = !busy, onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.testTag("importJson")) { Text("Restore JSON backup") }
            Text("CSV / GPX range", style = MaterialTheme.typography.titleMedium)
            TimeRangePicker(date, onDate = { date = it }, timeSlider = false) { range = it }
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text("CSV: $csvType") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) { listOf("Food log", "Steps", "Weight", "Gym").forEach { type -> DropdownMenuItem(text = { Text(type) }, onClick = { csvType = type; expanded = false }) } }
            }
            Row {
                Button(enabled = !busy && range != null, onClick = { csv.launch("fitness-${csvType.lowercase().replace(' ', '-')}.csv") }) { Text("Export CSV") }
                TextButton(enabled = !busy && range != null, onClick = { gpx.launch("fitness-route.gpx") }, modifier = Modifier.testTag("exportGpx")) { Text("Export GPX") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
        }
    }
    staged?.let { stage -> AlertDialog(onDismissRequest = { stage.discard(app); staged = null }, title = { Text("Replace current fitness data?") },
        text = { Text("Validated backup: ${stage.counts["MealEntry"] ?: 0} food entries, ${stage.counts["Workout"] ?: 0} workouts, ${stage.counts["LocationPoint"] ?: 0} route points, ${stage.counts.values.sum()} total records. Current data will be replaced. Stop tracking first. Keys stay unchanged and tracking remains off after restore.") },
        confirmButton = { TextButton(onClick = { stage.handedOff = true; staged = null; app.restoreBackup(stage) }, modifier = Modifier.testTag("confirmRestore")) { Text("Replace data") } },
        dismissButton = { TextButton(onClick = { stage.discard(app); staged = null }) { Text("Cancel") } }) }
}

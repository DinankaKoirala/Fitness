package com.example.fitness.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.FitnessApplication
import com.example.fitness.data.ActivitySettings
import java.time.LocalDate
import kotlinx.coroutines.*

@Composable
fun ActivityScreen() {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Activity", style = MaterialTheme.typography.headlineLarge)
        Text(date)
        ActivityRanges(date) { date = it }
        StepsCard(date)
        RouteControls()
    }
}

@Composable
fun StepsCard(date: String) {
    val context = LocalContext.current
    val app = context.applicationContext as FitnessApplication
    val settings = remember { ActivitySettings(context) }
    val enabled by settings.stepsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val trackingError by settings.error.collectAsStateWithLifecycle(initialValue = null)
    val flow = remember(date) { app.steps.observeDay(date) }
    val day by flow.collectAsStateWithLifecycle(initialValue = null)
    val available = remember { context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
    val scope = rememberCoroutineScope()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var correction by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun start() {
        scope.launch {
            try { settings.stepsEnabled(true); StepService.start(context, reset = true); error = null }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                settings.stepsEnabled(false); error = "Could not start tracking. Check permissions and try again."
            }
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (StepService.permitted(context)) start() else error = "Allow activity recognition to start step tracking."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Steps", style = MaterialTheme.typography.titleLarge)
            Text((day?.steps ?: 0).toString(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("stepCount"))
            if (day?.estimated == true) Text("Estimated day split · editable")
            Text("Counts begin when tracking is enabled. A daily split after a gap may be estimated; correct it below.")
            if (!available) Text("No step-counter sensor is available on this device. You can enter a total manually.")
            Button(enabled = available && !busy, onClick = {
                if (enabled) scope.launch { settings.stepsEnabled(false); context.stopService(Intent(context, StepService::class.java)) }
                else {
                    val needed = buildList {
                        if (Build.VERSION.SDK_INT >= 29 && !StepService.permitted(context)) add(Manifest.permission.ACTIVITY_RECOGNITION)
                        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (needed.isEmpty()) start() else permissions.launch(needed.toTypedArray())
                }
            }, modifier = Modifier.testTag("toggleSteps")) { Text(if (enabled) "Stop step tracking" else "Start step tracking") }
            OutlinedButton(onClick = { correction = (day?.steps ?: 0).toString(); editing = true; error = null }, modifier = Modifier.testTag("correctSteps")) { Text("Correct daily total") }
            (error ?: trackingError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text("Correct daily steps") },
        text = { Column {
            Text("$date · future sensor increments will be added to this corrected total.")
            OutlinedTextField(correction, onValueChange = { correction = it }, label = { Text("Steps") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.testTag("stepCorrection"))
            error?.let { Text(it) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            val value = correction.toLongOrNull()
            if (value == null || value !in 0..1_000_000) { error = "Enter steps between 0 and 1,000,000."; return@TextButton }
            busy = true
            scope.launch {
                try { app.steps.correct(date, value); editing = false; error = null }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not save the total. Try again." }
                finally { busy = false }
            }
        }, modifier = Modifier.testTag("saveSteps")) { Text("Save steps") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editing = false }) { Text("Cancel") } })
}

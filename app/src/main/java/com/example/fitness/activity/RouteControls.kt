package com.example.fitness.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.data.ActivitySettings
import kotlinx.coroutines.*

@Composable
fun RouteControls() {
    val context = LocalContext.current
    val settings = remember { ActivitySettings(context) }
    val enabled by settings.routeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val saver by settings.batterySaver.collectAsStateWithLifecycle(initialValue = false)
    val routeError by settings.routeError.collectAsStateWithLifecycle(initialValue = null)
    var backgroundExplanation by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun start() {
        scope.launch {
            try { settings.routeEnabled(true); RouteService.start(context); message = null }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                settings.routeEnabled(false); message = "Could not start route recording. Check location permissions."
            }
        }
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    fun askActivity() {
        if (Build.VERSION.SDK_INT >= 29 && !StepService.permitted(context)) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        else start()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askActivity() }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else askActivity()
    }
    val backgroundSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (RouteService.hasBackground(context)) askNotifications() else backgroundExplanation = true
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) askNotifications() else backgroundExplanation = true
    }
    fun askBackground() { if (!RouteService.hasBackground(context)) backgroundExplanation = true else askNotifications() }
    val foregroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (RouteService.hasLocation(context)) askBackground() else message = "Precise location is needed to record a route. Other features still work without it."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("All-day route", style = MaterialTheme.typography.titleLarge)
            Text(if (enabled) "Recording enabled · GPS sleeps while still" else "Recording is off")
            Text("When enabled, location is stored on this device even while the app is closed. Motion detection pauses GPS while still. A persistent notification lets you stop recording.")
            Button(onClick = {
                if (enabled) context.startService(Intent(context, RouteService::class.java).setAction("stop"))
                else if (!RouteService.hasLocation(context)) foregroundPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else askBackground()
            }, modifier = Modifier.testTag("toggleRoute")) { Text(if (enabled) "Stop route recording" else "Start route recording") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Battery saver", modifier = Modifier.weight(1f))
                Switch(saver, onCheckedChange = { value -> scope.launch { settings.batterySaver(value) } })
            }
            if (!RouteService.hasBackground(context)) Text("All-the-time location is not granted. Start recording here; automatic recovery after boot may be unavailable.")
            val power = context.getSystemService(PowerManager::class.java)
            if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
                Text("For reliable all-day recording, allow unrestricted battery use in Android settings. Some manufacturers also require auto-start permission.")
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        .onFailure { message = "Open Android Settings → Apps → Fitness → Battery and allow unrestricted use." }
                }) { Text("Open battery settings") }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com"))) }) { Text("Manufacturer battery guidance") }
            (message ?: routeError)?.let { Text(it) }
        }
    }
    if (backgroundExplanation) AlertDialog(onDismissRequest = { backgroundExplanation = false },
        title = { Text("Allow all-day location?") }, text = {
            Text("For recording and restarting after boot, allow location all the time in Android's app permissions. You can decline and keep using Fitness; recording started here can still run with its foreground notification.")
        }, confirmButton = { TextButton(onClick = {
            backgroundExplanation = false
            if (Build.VERSION.SDK_INT == 29) backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else backgroundSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Open permissions") } }, dismissButton = { TextButton(onClick = { backgroundExplanation = false; askNotifications() }) { Text("Continue without it") } })
}

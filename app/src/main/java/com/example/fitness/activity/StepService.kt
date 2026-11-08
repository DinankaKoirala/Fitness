package com.example.fitness.activity

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.provider.Settings
import com.example.fitness.FitnessApplication
import com.example.fitness.MainActivity
import com.example.fitness.R
import com.example.fitness.data.ActivitySettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel

class StepService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readings = Channel<StepReading>(Channel.CONFLATED)
    private lateinit var sensors: SensorManager
    @Volatile private var reset = false
    private var registered = false
    private var boot = 0
    private val settings by lazy { ActivitySettings(this) }
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SensorManager::class.java)
        boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("steps", "Step tracking", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "steps").setSmallIcon(R.drawable.ic_activity)
            .setContentTitle("Fitness step tracking").setContentText("Counting steps on this device")
            .setContentIntent(pending).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(30, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else startForeground(30, notification)
        } catch (failure: SecurityException) { fail("Step tracking permission is missing."); return }
        scope.launch {
            for (reading in readings) {
                try {
                    (application as FitnessApplication).steps.record(reading, reset)
                    reset = false
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    settings.error("Could not save steps. Reopen Activity to retry.")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!permitted(this)) { fail("Allow activity recognition to track steps."); return START_NOT_STICKY }
        if (intent?.getBooleanExtra("reset", false) == true) reset = true
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true) ?: sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) { fail("This device has no step-counter sensor. Manual correction is available."); return START_NOT_STICKY }
        if (!registered) {
            registered = sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 30_000_000)
            if (!registered) { fail("Could not start the step sensor."); return START_NOT_STICKY }
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val raw = event.values.firstOrNull()?.toDouble() ?: return
        if (!raw.isFinite() || raw < 0 || raw > Long.MAX_VALUE.toDouble()) return
        val time = System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000
        val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        readings.trySend(StepReading(date, raw.toLong(), boot, time))
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun fail(message: String) { scope.launch { settings.stepsEnabled(false); settings.error(message); withContext(Dispatchers.Main) { stopSelf() } } }
    override fun onDestroy() { if (::sensors.isInitialized) sensors.unregisterListener(this); readings.close(); scope.cancel(); super.onDestroy() }
    companion object {
        fun permitted(context: Context) = Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        fun start(context: Context, reset: Boolean = false) { context.startForegroundService(Intent(context, StepService::class.java).putExtra("reset", reset)) }
    }
}

class TrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FitnessApplication
                val storage = app.storage.first { !it.loading }
                if (storage.error != null) return@launch
                val enabled = ActivitySettings(context).stepsEnabled.first()
                if (enabled && StepService.permitted(context)) StepService.start(context)
                val route = ActivitySettings(context).routeEnabled.first()
                if (route && RouteService.hasLocation(context) && RouteService.hasBackground(context)) RouteService.start(context)
                else if (route) ActivitySettings(context).routeError("Open Activity to resume recording; all-the-time location is required after boot.")
            } catch (_: Exception) { runCatching { ActivitySettings(context).error("Open Activity to resume tracking after restart.") } }
            finally { result.finish() }
        }
    }
}

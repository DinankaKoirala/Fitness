package com.example.fitness.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.location.*
import android.os.*
import com.example.fitness.*
import com.example.fitness.data.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.*

class RouteService : Service(), LocationListener, SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { ActivitySettings(this) }
    private val database get() = (application as FitnessApplication).database
    private lateinit var locations: LocationManager
    private lateinit var sensors: SensorManager
    private val flushMutex = Mutex()
    private val buffer = mutableListOf<LocationPoint>()
    private var previous: LocationPoint? = null
    private var motion = Motion.UNKNOWN
    private var saver = false
    private var currentSampling: Sampling? = null
    private var registeredMotion = false
    private var lastMoving = SystemClock.elapsedRealtime()
    private var lastBearing: Float? = null
    private var lastSensorWake = 0L
    private var stopping = false
    private val motionIntent by lazy {
        PendingIntent.getBroadcast(this, 40, Intent(this, MotionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    }
    private val trigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) { wakeMotion() }
    }
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        locations = getSystemService(LocationManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("route", "Route recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 40, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 41, Intent(this, RouteService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "route").setSmallIcon(R.drawable.ic_activity)
            .setContentTitle("Fitness route recording").setContentText("GPS sleeps while still · all data stays on this device")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(40, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(40, notification)
        } catch (_: SecurityException) { stopWithError("Allow precise location before starting route recording."); return }
        scope.launch { settings.batterySaver.collect { saver = it; if (currentSampling != null) requestGps() } }
        scope.launch {
            while (isActive) {
                delay(60_000)
                if (!hasLocation(this@RouteService)) { stopWithError("Location permission was revoked. Recording stopped."); break }
                flush()
                if (SystemClock.elapsedRealtime() - lastMoving > 120_000) setMotion(Motion.STILL)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            scope.launch { stopping = true; settings.routeEnabled(false); stopGps(); flush(); stopSelf() }
            return START_NOT_STICKY
        }
        if (!hasLocation(this)) { stopWithError("Location permission is missing."); return START_NOT_STICKY }
        scope.launch {
            if (!settings.routeEnabled.first()) { stopSelf(); return@launch }
            if (!registeredMotion) { registeredMotion = true; registerMotion(); requestGps() }
            intent?.getStringExtra("motion")?.let { value -> runCatching { Motion.valueOf(value) }.getOrNull()?.let(::setMotion) }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // Both runtime entry points check precise location.
    private fun requestGps(turning: Boolean = false) {
        if (stopping || !hasLocation(this)) { if (!stopping) stopWithError("Location permission is missing."); return }
        val policy = sampling(motion, saver, turning)
        if (policy == null) { stopGps(); armMotion(); return }
        if (policy == currentSampling) return
        try {
            locations.removeUpdates(this)
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, policy.intervalMs, policy.minDistanceM, this, Looper.getMainLooper())
            currentSampling = policy
            if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) scope.launch { settings.routeError("GPS is off. Enable device location to resume recording.") }
        } catch (_: SecurityException) { stopWithError("Location permission was revoked. Recording stopped.") }
        catch (_: IllegalArgumentException) { stopWithError("This device has no GPS provider.") }
    }
    private fun stopGps() { if (::locations.isInitialized) locations.removeUpdates(this); currentSampling = null }
    private fun setMotion(value: Motion) {
        motion = value
        if (value != Motion.STILL) lastMoving = SystemClock.elapsedRealtime()
        requestGps()
    }
    private fun wakeMotion() {
        if (SystemClock.elapsedRealtime() - lastSensorWake < 30_000) return
        lastSensorWake = SystemClock.elapsedRealtime()
        setMotion(Motion.UNKNOWN)
    }
    @SuppressLint("MissingPermission") // ACTIVITY_RECOGNITION is checked immediately before registration.
    private fun registerMotion() {
        if (StepService.permitted(this) && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            ActivityRecognition.getClient(this).requestActivityUpdates(60_000, motionIntent)
                .addOnFailureListener { registerFallback(); scope.launch { settings.routeError("Motion recognition unavailable; using local sensor/GPS fallback.") } }
        } else { registerFallback(); scope.launch { settings.routeError("Using local sensor/GPS motion fallback on this device.") } }
        armMotion()
    }
    private fun registerFallback() {
        // Prefer the low-power one-shot sensor; use 1 Hz batched acceleration only
        // when neither activity recognition nor that sensor is available.
        if (sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) == null) {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, 1_000_000, 30_000_000)
            }
        }
    }
    private fun armMotion() {
        sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { sensors.requestTriggerSensor(trigger, it) }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (currentSampling != null || event.values.size < 3) return
        val acceleration = sqrt(event.values.take(3).sumOf { (it * it).toDouble() })
        if (abs(acceleration - SensorManager.GRAVITY_EARTH) > 1.5) wakeMotion()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onLocationChanged(location: Location) {
        if (!location.hasAccuracy()) return
        val point = LocationPoint(timestamp = location.time, lat = location.latitude, lon = location.longitude,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null, accuracy = location.accuracy.toDouble())
        if (!validLocation(point, previous)) return
        val speed = point.speed ?: previous?.let {
            (distanceM(it.lat, it.lon, point.lat, point.lon) - it.accuracy - point.accuracy).coerceAtLeast(0.0) / ((point.timestamp - it.timestamp) / 1000.0)
        } ?: 0.0
        if (speed >= 0.6) lastMoving = SystemClock.elapsedRealtime()
        val inferred = when { speed >= 3.0 -> Motion.VEHICLE; speed >= 0.6 -> Motion.WALKING; else -> motion }
        val turning = location.hasBearing() && lastBearing?.let { bearingChange(it, location.bearing) >= 30 } == true
        if (location.hasBearing()) lastBearing = location.bearing
        motion = inferred
        previous = point
        buffer.add(point)
        if (buffer.size >= 20) scope.launch { flush() }
        requestGps(turning)
    }
    private suspend fun flush() = flushMutex.withLock {
        if (buffer.isEmpty()) return@withLock
        val batch = buffer.toList(); buffer.clear()
        try { withContext(Dispatchers.IO + NonCancellable) { database.locationDao().insert(batch) } }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            buffer.addAll(0, batch)
            settings.routeError("Could not save route points. Check free storage; recording is paused.")
            stopGps()
            stopping = true
            settings.routeEnabled(false)
            stopSelf()
        }
    }
    override fun onProviderDisabled(provider: String) { scope.launch { settings.routeError("GPS is off. Enable device location to resume recording.") } }
    override fun onProviderEnabled(provider: String) { scope.launch { settings.routeError(null) } }
    @Deprecated("Platform callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    private fun stopWithError(message: String) {
        scope.launch { stopping = true; stopGps(); settings.routeEnabled(false); settings.routeError(message); flush(); stopSelf() }
    }
    override fun onDestroy() {
        stopGps()
        if (::sensors.isInitialized) { sensors.unregisterListener(this); sensors.cancelTriggerSensor(trigger, null) }
        try {
            if (StepService.permitted(this) && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS)
                ActivityRecognition.getClient(this).removeActivityUpdates(motionIntent).addOnFailureListener { }
        } catch (_: SecurityException) { /* Permission can be revoked while tracking. */ }
        val remaining = buffer.toList(); buffer.clear()
        if (remaining.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch { runCatching { database.locationDao().insert(remaining) } }
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        fun hasLocation(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun hasBackground(context: Context) = Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun start(context: Context, motion: Motion? = null) {
            context.startForegroundService(Intent(context, RouteService::class.java).apply { motion?.let { putExtra("motion", it.name) } })
        }
    }
}

class MotionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activity = ActivityRecognitionResult.extractResult(intent)?.mostProbableActivity ?: return
        if (activity.confidence < 60) return
        val motion = when (activity.type) {
            DetectedActivity.STILL -> Motion.STILL
            DetectedActivity.ON_FOOT, DetectedActivity.WALKING, DetectedActivity.RUNNING -> Motion.WALKING
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE -> Motion.VEHICLE
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { if (ActivitySettings(context).routeEnabled.first() && RouteService.hasLocation(context)) RouteService.start(context, motion) }
            catch (_: Exception) { runCatching { ActivitySettings(context).routeError("Open Activity to resume route recording.") } }
            finally { pending.finish() }
        }
    }
}

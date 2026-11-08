package com.example.fitness.activity

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitness.core.ForegroundNetwork
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
internal fun NativeRouteMap(state: com.example.fitness.data.ActivityRangeState, satellite: Boolean, key: String,
    cursor: MapCoordinate?, follow: Boolean, pitch: Boolean, modifier: Modifier, onOpen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val points = remember(state.points, state.segments) {
        (if (state.segments.isEmpty()) state.points.filter { it.timestamp >= state.range.from && it.timestamp < state.range.to } else state.points).distinctBy { it.timestamp }
    }
    val styleJson = remember(points, state.segments, satellite, key) { mapStyle(satellite, key, points, state.segments) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    val view = remember {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(ForegroundNetwork.client(context))
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        HttpRequestUtil.setLogEnabled(false)
        // HTTP cache retains unauthenticated viewed OSM tiles; disable the native
        // resource DB so authenticated URLs are never retained there in plaintext.
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(0, null)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            getMapAsync { map = it; it.setPrefetchZoomDelta(0) }
        }
    }
    DisposableEffect(view, lifecycle) {
        var started = false; var resumed = false; var destroyed = false
        fun stop() { if (resumed) { view.onPause(); resumed = false }; if (started) { view.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { view.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { view.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { view.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> stop()
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) { stop(); view.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); if (!destroyed) { stop(); view.onDestroy() } }
    }
    DisposableEffect(map, onOpen) {
        val listener = MapLibreMap.OnMapClickListener { onOpen?.invoke(); onOpen != null }
        map?.addOnMapClickListener(listener)
        onDispose { map?.removeOnMapClickListener(listener) }
    }
    LaunchedEffect(map, styleJson) {
        ready = false
        map?.setStyle(Style.Builder().fromJson(styleJson)) {
            ready = true
            val visible = state.segments.flatMap { segmentCoordinates(points, it) }.ifEmpty { points.map { p -> MapCoordinate(p.lat, p.lon, p.timestamp, p.speed) } }
            val coordinates = visible.map { p -> LatLng(p.lat, p.lon) }.distinct()
            if (coordinates.size >= 2) map?.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coordinates).build(), 40))
            else coordinates.firstOrNull()?.let { target -> map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0)) }
        }
    }
    LaunchedEffect(map, ready, cursor, follow, pitch) {
        if (!ready) return@LaunchedEffect
        val geometry = cursor?.let { JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(it.lon, it.lat))).toString() }
            ?: "{\"type\":\"FeatureCollection\",\"features\":[]}"
        map?.style?.getSourceAs<GeoJsonSource>("cursor")?.setGeoJson(geometry)
        val position = map?.cameraPosition ?: return@LaunchedEffect
        val builder = CameraPosition.Builder(position).tilt(if (pitch) 55.0 else 0.0)
        if (follow && cursor != null) builder.target(LatLng(cursor.lat, cursor.lon)).zoom(16.0)
        map?.easeCamera(CameraUpdateFactory.newCameraPosition(builder.build()), 250)
    }
    AndroidView(factory = { view }, modifier = modifier)
}

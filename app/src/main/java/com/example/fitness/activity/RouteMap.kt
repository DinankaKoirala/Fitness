package com.example.fitness.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.fitness.core.Secrets
import com.example.fitness.data.ActivityRangeState
import com.example.fitness.food.displayNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

@Composable
fun RouteMap(state: ActivityRangeState) {
    var show by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var satellite by rememberSaveable { mutableStateOf(false) }
    var terrain by rememberSaveable { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var keyDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val secrets = remember { Secrets(context) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { secrets.get("maptiler") }.onSuccess { key = it }.onFailure { keyError = "Could not read the map key. Save it again in map settings." } }
    if (!show) {
        OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth().testTag("showRouteMap")) { Text("Show route map") }
    } else {
        Text("Walking: green · Vehicle: orange · Start: green dot · End: orange dot")
        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
        NativeRouteMap(state, satellite = false, key = "", cursor = null, follow = false, pitch = false,
            modifier = Modifier.fillMaxWidth().height(240.dp), onOpen = { full = true })
        Row {
            TextButton(onClick = { full = true }) { Text("Open full-screen map") }
            TextButton(onClick = { show = false }) { Text("Hide map") }
        }
    }
    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var fraction by rememberSaveable(state.range) { mutableFloatStateOf(0f) }
        var playing by remember { mutableStateOf(false) }
        var pitch by rememberSaveable { mutableStateOf(false) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val cursorTime = state.range.from + ((state.range.to - state.range.from) * fraction.toDouble()).toLong()
        val cursor = remember(state.points, state.segments, cursorTime) { scrubCoordinate(state.points, state.segments, cursorTime) }
        val path = remember(state.points, state.segments) { state.segments.flatMap { segmentCoordinates(state.points, it) }.distinctBy { it.time } }
        LaunchedEffect(playing, path) {
            if (playing && path.isNotEmpty()) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var index = path.indexOfFirst { it.time >= cursorTime }.coerceAtLeast(0)
                while (isActive && index < path.size) {
                    fraction = ((path[index].time - state.range.from).toDouble() / (state.range.to - state.range.from)).toFloat().coerceIn(0f, 1f)
                    index++; delay(300)
                }
                playing = false
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recorded route", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { full = false }) { Text("Close") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!satellite && !terrain, onClick = { satellite = false; terrain = false }, label = { Text("Street") })
                    FilterChip(satellite && !terrain, onClick = { if (key.isBlank()) keyDialog = true else { satellite = true; terrain = false } }, label = { Text("Satellite") })
                    FilterChip(terrain, onClick = { if (key.isBlank()) keyDialog = true else { terrain = true; satellite = true } }, label = { Text("3D terrain") })
                }
                if (terrain) TerrainMap(state, key, cursor, playing, Modifier.fillMaxWidth().weight(1f))
                else NativeRouteMap(state, satellite, key, cursor, playing, pitch || playing, Modifier.fillMaxWidth().weight(1f))
                Slider(value = fraction, onValueChange = { fraction = it; playing = false }, modifier = Modifier.testTag("mapScrubber"))
                Text(Instant.ofEpochMilli(cursorTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm:ss")))
                Text(cursor?.speed?.let { "${displayNumber(it * 3.6)} km/h · estimated between recorded points" }
                    ?: if (cursor == null) "No recorded data at this time" else "Speed unavailable for this point")
                Row {
                    TextButton(onClick = { playing = !playing }, enabled = path.isNotEmpty()) { Text(if (playing) "Pause flyover" else "Flyover") }
                    if (!terrain) TextButton(onClick = { pitch = !pitch }) { Text(if (pitch) "Flat" else "Pitch") }
                    TextButton(onClick = { keyDialog = true; draft = "" }) { Text("Map key") }
                }
                Text(if (satellite || terrain) "© MapTiler · imagery credits in map attribution" else "© OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
                Text("Tiles load only while this app is visible. Satellite/terrain require your MapTiler key and an eligible provider plan. Only viewed tiles are requested.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (keyDialog) AlertDialog(onDismissRequest = { keyDialog = false; draft = "" }, title = { Text("MapTiler key") },
        text = { Column {
            Text("Your key is encrypted with Android Keystore. Satellite and elevation usage follows your MapTiler plan; clearing the field removes the key. No bulk downloads.")
            OutlinedTextField(draft, onValueChange = { draft = it }, label = { Text("MapTiler API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            keyError?.let { Text(it) }
        } }, confirmButton = { TextButton(onClick = {
            scope.launch {
                try { secrets.put("maptiler", draft); key = draft.trim(); draft = ""; keyDialog = false; keyError = null; if (key.isBlank()) { satellite = false; terrain = false } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; keyError = "Could not save the key. Try again." }
            }
        }) { Text("Save key") } }, dismissButton = { TextButton(onClick = { keyDialog = false; draft = "" }) { Text("Cancel") } })
}

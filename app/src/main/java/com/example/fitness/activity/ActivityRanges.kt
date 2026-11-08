package com.example.fitness.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.FitnessApplication
import com.example.fitness.data.*
import com.example.fitness.food.displayNumber
import com.example.fitness.food.pickerDate
import com.example.fitness.food.pickerMillis
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityRanges(selectedDate: String, onDate: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as FitnessApplication
    var mode by rememberSaveable { mutableStateOf("Day") }
    var customFrom by rememberSaveable { mutableStateOf(selectedDate) }
    var customTo by rememberSaveable { mutableStateOf(selectedDate) }
    var fractionStart by rememberSaveable(selectedDate, mode, customFrom, customTo) { mutableFloatStateOf(0f) }
    var fractionEnd by rememberSaveable(selectedDate, mode, customFrom, customTo) { mutableFloatStateOf(1f) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Segment?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selected = LocalDate.parse(selectedDate)
    val zone = ZoneId.systemDefault()
    val baseRange = runCatching {
        when (mode) {
            "Week" -> {
                val start = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                TimeRange(dayRange(start, zone).from, dayRange(start.plusDays(6), zone).to)
            }
            "Custom" -> TimeRange(dayRange(LocalDate.parse(customFrom), zone).from, dayRange(LocalDate.parse(customTo), zone).to)
            else -> dayRange(selected, zone)
        }
    }.getOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Route and range", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = { onDate(selected.minusDays(1).toString()) }) { Text("‹") }
                TextButton(onClick = { calendar = true }, modifier = Modifier.testTag("activityDate")) { Text(selectedDate) }
                TextButton(onClick = { onDate(selected.plusDays(1).toString()) }) { Text("›") }
                TextButton(onClick = { onDate(LocalDate.now().toString()) }) { Text("Today") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Day", "Week", "Custom").forEach { option ->
                    FilterChip(mode == option, onClick = { mode = option }, label = { Text(option) }, modifier = Modifier.testTag("range_$option"))
                }
            }
            if (mode == "Custom") {
                OutlinedTextField(customFrom, onValueChange = { customFrom = it }, label = { Text("From date (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rangeFrom"))
                OutlinedTextField(customTo, onValueChange = { customTo = it }, label = { Text("Through date (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rangeTo"))
            }
            if (baseRange == null) Text("Enter valid dates with the end on or after the start.")
            else {
                val span = baseRange.to - baseRange.from
                RangeSlider(value = fractionStart..fractionEnd, onValueChange = {
                    if (it.endInclusive - it.start >= 0.001f) { fractionStart = it.start; fractionEnd = it.endInclusive }
                }, valueRange = 0f..1f, modifier = Modifier.testTag("activityTimeRange"))
                val from = baseRange.from + (span * fractionStart.toDouble()).toLong()
                val to = baseRange.from + (span * fractionEnd.toDouble()).toLong()
                val range = TimeRange(from, to.coerceAtLeast(from + 1))
                val format = DateTimeFormatter.ofPattern("d MMM HH:mm")
                Text("${Instant.ofEpochMilli(range.from).atZone(zone).format(format)} → ${Instant.ofEpochMilli(range.to).atZone(zone).format(format)}")
                val flow = remember(range, retry) { app.segments.observe(range) }
                val state by flow.collectAsStateWithLifecycle(initialValue = ActivityRangeState(range))
                if (state.loading || state.range != range) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (state.error != null) { Text(state.error.orEmpty()); TextButton(onClick = { retry++ }) { Text("Try again") } }
                else {
                    val stats = state.stats
                    Text("${displayNumber(stats.distance / 1000)} km recorded", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("rangeDistance"))
                    Text("Walking ${displayNumber(stats.walked / 1000)} km · Vehicle ${displayNumber(stats.vehicle / 1000)} km")
                    Text("Average ${displayNumber(stats.averageSpeed * 3.6)} km/h · Max ${displayNumber(stats.maxSpeed * 3.6)} km/h")
                    Text("${stats.observedMs / 60_000} observed minutes. Gaps over 3 minutes are excluded. Average speed uses recorded moving time.")
                    Text("Classification is estimated from speed and step cadence. Tap a segment to correct it; corrections persist for that time interval.", style = MaterialTheme.typography.bodySmall)
                    if (state.segments.isEmpty()) Text("No recorded route in this range.")
                    state.segments.forEach { segment ->
                        TextButton(onClick = { editing = segment; error = null }, modifier = Modifier.testTag("classify_${segment.start}_${segment.end}")) {
                            val time = Instant.ofEpochMilli(segment.start).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                            Text("$time · ${segment.type.name.lowercase()} · ${displayNumber(segment.distance)} m · ${if (segment.estimated) "estimated" else "corrected"}")
                        }
                    }
                }
            }
        }
    }
    if (calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(selected))
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { onDate(pickerDate(it).toString()) }; calendar = false }) { Text("Choose date") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    editing?.let { segment -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("Correct segment type") },
        text = { Column {
            SegmentType.entries.forEach { type -> TextButton(onClick = {
                scope.launch {
                    try { app.segments.correct(segment, type); editing = null }
                    catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not save this correction." }
                }
            }) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) } }
            error?.let { Text(it) }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }) }
}

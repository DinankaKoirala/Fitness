package com.example.fitness.core

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.fitness.activity.*
import com.example.fitness.food.pickerDate
import com.example.fitness.food.pickerMillis
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangePicker(date: String, onDate: (String) -> Unit, timeSlider: Boolean = true, onRange: (TimeRange?) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Day") }
    var fromDate by rememberSaveable { mutableStateOf(date) }
    var toDate by rememberSaveable { mutableStateOf(date) }
    var startFraction by rememberSaveable(date, mode, fromDate, toDate) { mutableFloatStateOf(0f) }
    var endFraction by rememberSaveable(date, mode, fromDate, toDate) { mutableFloatStateOf(1f) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    val selected = LocalDate.parse(date)
    val zone = ZoneId.systemDefault()
    val base = runCatching {
        when (mode) {
            "Week" -> selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { TimeRange(dayRange(it).from, dayRange(it.plusDays(6)).to) }
            "Custom" -> {
                val from = LocalDate.parse(fromDate); val to = LocalDate.parse(toDate)
                require(from.year in 1900..2100 && to.year in 1900..2100)
                TimeRange(dayRange(from).from, dayRange(to).to)
            }
            else -> dayRange(selected)
        }
    }.getOrNull()
    val range = base?.let {
        if (!timeSlider) it else {
            val span = it.to - it.from
            val from = it.from + (span * startFraction.toDouble()).toLong()
            TimeRange(from, (it.from + (span * endFraction.toDouble()).toLong()).coerceAtLeast(from + 1))
        }
    }
    val updatedCallback by rememberUpdatedState(onRange)
    LaunchedEffect(range) { updatedCallback(range) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            TextButton(onClick = { onDate(selected.minusDays(1).toString()) }) { Text("‹") }
            TextButton(onClick = { calendar = true }, modifier = Modifier.testTag("activityDate")) { Text(date) }
            TextButton(onClick = { onDate(selected.plusDays(1).toString()) }) { Text("›") }
            TextButton(onClick = { onDate(LocalDate.now().toString()) }) { Text("Today") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Day", "Week", "Custom").forEach { value -> FilterChip(mode == value, onClick = { mode = value }, label = { Text(value) }, modifier = Modifier.testTag("range_$value")) }
        }
        if (mode == "Custom") {
            OutlinedTextField(fromDate, onValueChange = { fromDate = it }, label = { Text("From date (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rangeFrom"))
            OutlinedTextField(toDate, onValueChange = { toDate = it }, label = { Text("Through date (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rangeTo"))
        }
        if (base == null) Text("Enter valid dates from 1900–2100, with the end on or after the start.")
        else {
            if (timeSlider) RangeSlider(startFraction..endFraction, onValueChange = {
                if ((it.endInclusive - it.start) * (base.to - base.from) >= 1000) { startFraction = it.start; endFraction = it.endInclusive }
            }, modifier = Modifier.testTag("activityTimeRange"))
            val format = DateTimeFormatter.ofPattern(if (timeSlider) "d MMM HH:mm" else "d MMM yyyy")
            range?.let { Text("${Instant.ofEpochMilli(it.from).atZone(zone).format(format)} → ${Instant.ofEpochMilli(it.to - if (timeSlider) 0 else 1).atZone(zone).format(format)}") }
        }
    }
    if (calendar) {
        val state = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(selected))
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onDate(pickerDate(it).toString()) }; calendar = false }) { Text("Choose date") } },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

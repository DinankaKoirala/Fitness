package com.example.fitness.trends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.FitnessApplication
import com.example.fitness.activity.*
import com.example.fitness.core.*
import com.example.fitness.data.*
import com.example.fitness.food.*
import com.example.fitness.gym.*
import java.time.*
import kotlinx.coroutines.*

@Composable
fun TrendsButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("openTrends")) { Text("View trends") }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TrendsScreen { open = false } }
    }
}

@Composable
fun TrendsScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitnessApplication
    val repository = remember { TrendsRepository(app.database) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var range by remember { mutableStateOf<TimeRange?>(dayRange(LocalDate.parse(date))) }
    var retry by remember { mutableIntStateOf(0) }
    var exerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var weightDialog by rememberSaveable { mutableStateOf(false) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var weightDate by rememberSaveable { mutableStateOf(date) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val profile by app.profiles.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { app.gym.ensureSeeded() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Trends", style = MaterialTheme.typography.headlineLarge); TextButton(onClick = onClose) { Text("Close") } }
        TimeRangePicker(date, onDate = { date = it }, timeSlider = false) { range = it }
        Text("Daily records use calendar days. Gaps and missing nutrient values stay unknown; charts do not fill them with invented values.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { weightDate = date; weightDialog = true; error = null }, modifier = Modifier.testTag("logWeight")) { Text("Log a weight") }
        val selectedRange = range
        if (selectedRange != null) {
            val flow = remember(selectedRange, retry) { repository.observe(selectedRange) }
            val state by flow.collectAsStateWithLifecycle(initialValue = TrendsState(selectedRange))
            if (state.loading || state.range != selectedRange) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (state.error != null) { Text(state.error.orEmpty()); TextButton(onClick = { retry++ }) { Text("Try again") } }
            else {
                val zone = ZoneId.systemDefault()
                val from = Instant.ofEpochMilli(selectedRange.from).atZone(zone).toLocalDate()
                val to = Instant.ofEpochMilli(selectedRange.to - 1).atZone(zone).toLocalDate()
                val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { it.toString() }.toList()
                val meals = state.meals.groupBy { it.date }.mapValues { nutritionTotals(it.value) }
                TrendChart("Calories vs goal", days, listOf(ChartSeries("Calories", FitnessColors.Accent, days.map { meals[it]?.calories })), "kcal", profile.goal?.toDouble())
                Text("Goal comparison uses your current setting; historical goal changes are not recorded.", style = MaterialTheme.typography.bodySmall)
                TrendChart("Macros", days, listOf(ChartSeries("Protein", FitnessColors.Protein, days.map { meals[it]?.protein }),
                    ChartSeries("Fat", FitnessColors.Fat, days.map { meals[it]?.fat }), ChartSeries("Carbs", FitnessColors.Carbs, days.map { meals[it]?.carbs })), "g")
                val weights = state.weights.groupBy { it.date }.mapValues { it.value.last().weightKg }
                TrendChart("Weight", days, listOf(ChartSeries("Last weight each day", FitnessColors.Accent, days.map { weights[it] })), "kg")
                val steps = state.steps.associateBy { it.date }
                TrendChart("Steps", days, listOf(ChartSeries("Steps", FitnessColors.Walking, days.map { steps[it]?.steps?.toDouble() })), "steps")
                if (state.steps.any { it.estimated }) Text("Includes estimated step day splits; correct them in Activity.")
                val segments = state.segments.groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate().toString() }
                TrendChart("Walked vs vehicle", days, listOf(
                    ChartSeries("Walking", FitnessColors.Walking, days.map { day -> segments[day]?.filter { it.type == SegmentType.WALKING }?.sumOf { it.distance }?.div(1000) }),
                    ChartSeries("Vehicle", FitnessColors.Vehicle, days.map { day -> segments[day]?.filter { it.type == SegmentType.VEHICLE }?.sumOf { it.distance }?.div(1000) })), "km", stacked = true)
                Text("Gym trends", style = MaterialTheme.typography.titleLarge)
                val selected = exerciseId ?: state.sets.lastOrNull()?.exerciseId
                TextButton(onClick = { picker = true }, modifier = Modifier.testTag("trendExercise")) { Text(state.exercises.find { it.id == selected }?.name ?: "Choose an exercise") }
                if (picker) ExercisePicker(state.exercises, app.gym, onClose = { picker = false }) { id -> exerciseId = id; picker = false }
                val history = state.sets.filter { it.exerciseId == selected }
                val inRange = history.filter { it.performedAt >= selectedRange.from && it.performedAt < selectedRange.to }
                val gymDays = inRange.groupBy { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate().toString() }
                TrendChart("Gym volume", days, listOf(ChartSeries("Reps × kg", FitnessColors.Carbs, days.map { day -> gymDays[day]?.sumOf { it.reps * it.weightKg } })), "kg")
                val records = inRange.associateWith { set -> personalRecord(set, history.filter { it.performedAt < set.performedAt || it.performedAt == set.performedAt && it.id < set.id }) }
                TrendChart("Personal records", days, listOf(ChartSeries("Weight, reps or volume PRs", FitnessColors.Fat, days.map { day -> gymDays[day]?.count { records[it] !in listOf(RecordKind.NONE, RecordKind.FIRST) }?.toDouble() })), "PRs")
                Text("Weekly average micros", style = MaterialTheme.typography.titleLarge)
                Text("Averages use only logged days with complete values for that nutrient. Missing or partial days are excluded, not counted as zero.")
                Micro.entries.forEach { micro ->
                    val weeks = weeklyMicros(state.meals, micro)
                    TrendChart("Weekly ${micro.label}", weeks.map { it.week }, listOf(ChartSeries(micro.label, FitnessColors.Accent, weeks.map { it.amount })), micro.unit)
                    weeks.forEach { week -> Text("${week.week}: ${week.completeDays}/${week.loggedDays} complete logged days", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (weightDialog) AlertDialog(onDismissRequest = { weightDialog = false }, title = { Text("Log weight") }, text = { Column {
        OutlinedTextField(weightDate, onValueChange = { weightDate = it }, label = { Text("Date (yyyy-mm-dd)") })
        NumberField("Weight (kg)", weightText, "trendWeight") { weightText = it }
        Text("Historical logs do not change today's profile or calorie goal. Profile weight changes are also logged automatically.")
        error?.let { Text(it) }
    } }, confirmButton = { TextButton(onClick = { scope.launch {
        try { val kg = foodNumber(weightText, "Weight", true); repository.logWeight(weightDate, kg); weightDialog = false }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Enter a valid date and weight up to 1,000 kg." }
    } }, modifier = Modifier.testTag("saveTrendWeight")) { Text("Save") } }, dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Cancel") } })
}

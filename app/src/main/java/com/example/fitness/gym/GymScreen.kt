package com.example.fitness.gym

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.fitness.FitnessApplication
import com.example.fitness.data.*
import com.example.fitness.food.displayNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

@Composable
fun GymScreen() {
    val repository = (LocalContext.current.applicationContext as FitnessApplication).gym
    var retry by remember { mutableIntStateOf(0) }
    val flow = remember(retry) { repository.observe() }
    val state by flow.collectAsStateWithLifecycle(initialValue = GymState())
    var routineEditor by rememberSaveable { mutableStateOf<Long?>(null) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var selectedWorkout by rememberSaveable { mutableStateOf<Long?>(null) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var setExercise by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingSet by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingSet by rememberSaveable { mutableStateOf<Long?>(null) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val workout = state.workouts.find { it.id == selectedWorkout }
    val active = state.workouts.firstOrNull { it.endedAt == null }
    fun action(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch { try { block() } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Could not save the workout." } finally { busy = false } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Gym", style = MaterialTheme.typography.headlineLarge)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (state.error != null) { Text(state.error.orEmpty()); Button(onClick = { retry++ }) { Text("Try again") } }
        else if (workout == null) {
            Text("${state.exercises.count { !it.custom }} bundled exercises · ${state.exercises.count { it.custom }} custom")
            if (active != null) Button(onClick = { selectedWorkout = active.id }, modifier = Modifier.testTag("resumeWorkout")) { Text("Resume ${active.name}") }
            Button(enabled = active == null && !busy, onClick = { action { selectedWorkout = repository.start(null) } }, modifier = Modifier.testTag("startFreeWorkout")) { Text("Start free workout") }
            Row {
                OutlinedButton(onClick = { routineEditor = 0L }, modifier = Modifier.testTag("newRoutine")) { Text("Create routine") }
                TextButton(onClick = { custom = true }, modifier = Modifier.testTag("newCustomExercise")) { Text("Custom exercise") }
            }
            Text("Routines", style = MaterialTheme.typography.titleLarge)
            if (state.routines.isEmpty()) Text("Create a routine to choose and order your exercises.")
            state.routines.forEach { routine ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                        Text("${state.routineExercises.count { it.routineId == routine.id }} exercises")
                        Row {
                            TextButton(enabled = active == null && !busy, onClick = { action { selectedWorkout = repository.start(routine.id) } }, modifier = Modifier.testTag("startRoutine_${routine.id}")) { Text("Start") }
                            TextButton(onClick = { routineEditor = routine.id }) { Text("Edit") }
                        }
                    }
                }
            }
            Text("Workout history", style = MaterialTheme.typography.titleLarge)
            state.workouts.filter { it.endedAt != null }.forEach { item ->
                val sets = state.sets.filter { it.workoutId == item.id }
                TextButton(onClick = { selectedWorkout = item.id }, modifier = Modifier.testTag("reviewWorkout_${item.id}")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.name)
                        Text("${Instant.ofEpochMilli(item.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))} · ${sets.size} sets · ${displayNumber(sets.sumOf { it.weightKg * it.reps })} kg volume")
                    }
                }
            }
        } else {
            TextButton(onClick = { selectedWorkout = null }) { Text("Back to gym") }
            Text(workout.name, style = MaterialTheme.typography.headlineSmall)
            WorkoutTimers(workout) { action { repository.cancelRest(workout.id) } }
            if (workout.endedAt == null) Row {
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.testTag("workoutAddExercise")) { Text("Add exercise") }
                TextButton(enabled = !busy, onClick = { finishing = true }, modifier = Modifier.testTag("finishWorkout")) { Text("Finish workout") }
            }
            state.workoutExercises.filter { it.workoutId == workout.id }.sortedBy { it.position }.forEach { link ->
                val exercise = state.exercises.find { it.id == link.exerciseId } ?: return@forEach
                val sets = state.sets.filter { it.workoutId == workout.id && it.exerciseId == exercise.id }
                val previousWorkout = state.workouts.firstOrNull { past -> past.id != workout.id && past.startedAt < workout.startedAt && past.endedAt != null && state.sets.any { it.workoutId == past.id && it.exerciseId == exercise.id } }
                val previous = state.sets.filter { it.workoutId == previousWorkout?.id && it.exerciseId == exercise.id }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                        if (previous.isNotEmpty()) Text("Last session: " + previous.joinToString { "${it.reps} × ${displayNumber(it.weightKg)} kg" })
                        sets.forEach { set ->
                            val history = state.sets.filter { it.performedAt < set.performedAt || it.performedAt == set.performedAt && it.id < set.id }
                            val record = personalRecord(set, history)
                            Text("${set.reps} × ${displayNumber(set.weightKg)} kg" + when (record) {
                                RecordKind.FIRST -> " · first logged set"
                                RecordKind.NONE -> ""
                                else -> " · ${record.name.lowercase()} PR"
                            }, modifier = Modifier.testTag("loggedSet_${set.id}"))
                            Row {
                                TextButton(onClick = { setExercise = exercise.id; editingSet = set.id }) { Text("Edit set") }
                                TextButton(onClick = { deletingSet = set.id }) { Text("Delete set") }
                            }
                        }
                        if (workout.endedAt == null) Button(onClick = { setExercise = exercise.id; editingSet = null }, modifier = Modifier.testTag("logSet_${exercise.id}")) { Text("Log set") }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (!state.loading && state.error == null) {
        routineEditor?.let { id -> key("routine", id) { RoutineEditor(state.routines.find { it.id == id }, state.routineExercises.filter { it.routineId == id }.sortedBy { it.position }.map { it.exerciseId }, state, repository) { routineEditor = null } } }
        if (custom) CustomExerciseEditor(repository, onClose = { custom = false }) { custom = false }
        if (picker && workout != null) ExercisePicker(state.exercises, repository, onClose = { picker = false }) { id -> repository.addExercise(workout.id, id); picker = false }
        val exercise = state.exercises.find { it.id == setExercise }
        if (exercise != null && workout != null) key("set", editingSet, exercise.id) {
            val previousWorkout = state.workouts.firstOrNull { it.id != workout.id && it.startedAt < workout.startedAt && it.endedAt != null && state.sets.any { set -> set.workoutId == it.id && set.exerciseId == exercise.id } }
            SetEditor(workout, exercise, state.sets.find { it.id == editingSet }, state.sets.filter { it.workoutId == previousWorkout?.id && it.exerciseId == exercise.id }, repository) { setExercise = null; editingSet = null }
        }
    }
    if (finishing && workout != null) AlertDialog(onDismissRequest = { finishing = false }, title = { Text("Finish workout?") },
        text = { Text("Your sets stay available for review and correction.") }, confirmButton = { TextButton(enabled = !busy, onClick = { action { repository.finish(workout.id); finishing = false } }, modifier = Modifier.testTag("confirmFinishWorkout")) { Text("Finish") } },
        dismissButton = { TextButton(onClick = { finishing = false }) { Text("Cancel") } })
    deletingSet?.let { id -> AlertDialog(onDismissRequest = { deletingSet = null }, title = { Text("Delete this set?") },
        confirmButton = { TextButton(onClick = { action { repository.deleteSet(id); deletingSet = null } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletingSet = null }) { Text("Cancel") } }) }
}

@Composable
private fun WorkoutTimers(workout: Workout, cancelRest: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(workout.id, workout.endedAt) {
        if (workout.endedAt == null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (isActive) { now = System.currentTimeMillis(); delay(1000) } }
    }
    Text("Duration ${timerText((workout.endedAt ?: now) - workout.startedAt)}", modifier = Modifier.testTag("workoutTimer"))
    workout.restUntil?.let { deadline ->
        Text(if (deadline > now) "Rest ${timerText(deadline - now)}" else "Rest complete", modifier = Modifier.testTag("restTimer"))
        TextButton(onClick = cancelRest) { Text("Cancel rest") }
    }
}

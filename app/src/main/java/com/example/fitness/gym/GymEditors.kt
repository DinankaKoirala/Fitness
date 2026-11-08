package com.example.fitness.gym

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.fitness.data.*
import com.example.fitness.food.FoodDialog
import com.example.fitness.food.NumberField
import kotlinx.coroutines.*

@Composable
internal fun ExercisePicker(exercises: List<Exercise>, repository: GymRepository, onClose: () -> Unit, onChoose: suspend (Long) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (custom) CustomExerciseEditor(repository, onClose = { custom = false }) { id -> onChoose(id) }
    else FoodDialog("Choose exercise", busy, onClose) {
        OutlinedTextField(search, onValueChange = { search = it }, label = { Text("Search exercise, muscle or equipment") }, modifier = Modifier.fillMaxWidth().testTag("exerciseSearch"))
        OutlinedButton(onClick = { custom = true }, enabled = !busy) { Text("Create custom exercise") }
        error?.let { Text(it) }
        LazyColumn(Modifier.fillMaxWidth().height(400.dp).testTag("exerciseLibrary")) {
            items(exercises.filter { "${it.name} ${it.muscleGroup} ${it.equipment}".contains(search, true) }, key = { it.id }) { exercise ->
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try { onChoose(exercise.id) }
                        catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not choose this exercise. Try again." }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("chooseExercise_${exercise.id}")) {
                    Column(Modifier.fillMaxWidth()) { Text(exercise.name); Text("${exercise.muscleGroup} · ${exercise.equipment}${if (exercise.custom) " · Custom" else ""}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
internal fun CustomExerciseEditor(repository: GymRepository, onClose: () -> Unit, onCreated: suspend (Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FoodDialog("Custom exercise", busy, onClose) {
        OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Exercise name") }, modifier = Modifier.fillMaxWidth().testTag("exerciseName"))
        OutlinedTextField(muscle, onValueChange = { muscle = it }, label = { Text("Muscle group") }, modifier = Modifier.fillMaxWidth().testTag("exerciseMuscle"))
        OutlinedTextField(equipment, onValueChange = { equipment = it }, label = { Text("Equipment (or body weight)") }, modifier = Modifier.fillMaxWidth().testTag("exerciseEquipment"))
        error?.let { Text(it) }
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { val id = repository.createExercise(name, muscle, equipment); onCreated(id) }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Could not create this exercise." }
                finally { busy = false }
            }
        }, modifier = Modifier.testTag("saveExercise")) { Text("Save exercise") }
    }
}

@Composable
internal fun RoutineEditor(routine: Routine?, initial: List<Long>, state: GymState, repository: GymRepository, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(routine?.name.orEmpty()) }
    var selected by rememberSaveable { mutableStateOf(initial) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (picking) ExercisePicker(state.exercises, repository, onClose = { picking = false }) { id -> selected = (selected + id).distinct(); picking = false }
    else FoodDialog(if (routine == null) "Create routine" else "Edit routine", busy, onClose) {
        OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Routine name") }, modifier = Modifier.fillMaxWidth().testTag("routineName"))
        selected.forEachIndexed { index, id ->
            Text(state.exercises.find { it.id == id }?.name ?: "Exercise", style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(enabled = index > 0, onClick = { selected = selected.toMutableList().apply { add(index - 1, removeAt(index)) } }) { Text("Up") }
                TextButton(enabled = index < selected.lastIndex, onClick = { selected = selected.toMutableList().apply { add(index + 1, removeAt(index)) } }) { Text("Down") }
                TextButton(onClick = { selected = selected - id }) { Text("Remove") }
            }
        }
        OutlinedButton(onClick = { picking = true }, enabled = !busy, modifier = Modifier.testTag("routineAddExercise")) { Text("Add exercise") }
        error?.let { Text(it) }
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { repository.saveRoutine(routine?.id, name, selected); onClose() }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Could not save routine." }
                finally { busy = false }
            }
        }, modifier = Modifier.testTag("saveRoutine")) { Text("Save routine") }
    }
}

@Composable
internal fun SetEditor(workout: Workout, exercise: Exercise, set: SetEntry?, previous: List<SetEntry>, repository: GymRepository, onClose: () -> Unit) {
    var reps by rememberSaveable { mutableStateOf((set?.reps ?: previous.lastOrNull()?.reps)?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf((set?.weightKg ?: previous.lastOrNull()?.weightKg)?.toString().orEmpty()) }
    var rest by rememberSaveable { mutableStateOf("90") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FoodDialog(if (set == null) "Log set" else "Edit set", busy, onClose) {
        Text(exercise.name, style = MaterialTheme.typography.titleLarge)
        if (previous.isNotEmpty()) Text("Last session: " + previous.joinToString { "${it.reps} × ${it.weightKg} kg" })
        NumberField("Reps", reps, "setReps", !busy) { reps = it }
        NumberField("Weight (kg; 0 for unweighted)", weight, "setWeight", !busy) { weight = it }
        if (set == null) NumberField("Rest seconds (0–3600)", rest, "restSeconds", !busy) { rest = it }
        error?.let { Text(it) }
        Button(enabled = !busy, onClick = {
            val count = reps.toIntOrNull()
            val kg = weight.replace(',', '.').toDoubleOrNull()
            val seconds = rest.toIntOrNull()
            if (count == null || kg == null || seconds == null || seconds !in 0..3600) { error = "Enter whole reps, a weight, and rest seconds from 0 to 3,600."; return@Button }
            try { validateSet(count, kg) } catch (invalid: IllegalArgumentException) { error = invalid.message; return@Button }
            busy = true
            scope.launch {
                try { repository.saveSet(workout.id, exercise.id, count, kg, set?.id, seconds); onClose() }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not save this set. Try again." }
                finally { busy = false }
            }
        }, modifier = Modifier.testTag("saveSet")) { Text("Save set") }
    }
}

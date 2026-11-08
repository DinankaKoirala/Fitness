package com.example.fitness.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness.core.FitnessColors
import com.example.fitness.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(goal: Int, estimated: Boolean, repository: FoodRepository) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val selected = LocalDate.parse(date)
    val flow = remember(date, retry, repository) { repository.observeDay(date) }
    val state by flow.collectAsStateWithLifecycle(initialValue = FoodDayState(date))
    var addingMeal by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var chosenFood by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var editingEntry by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var foodEditor by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var loggedEditor by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable(date) { mutableStateOf<Long?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ready = !state.loading && state.date == date && state.error == null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Food", style = MaterialTheme.typography.headlineLarge)
        Text("Daily calorie goal", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$goal kcal / day", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("foodGoal"))
        Text(if (estimated) "Estimated · edit in Profile" else "Manual goal · edit in Profile",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("goalSource"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { date = selected.minusWeeks(1).toString() }, modifier = Modifier.semantics { contentDescription = "Previous week" }) { Text("‹") }
            TextButton(onClick = { calendar = true }, modifier = Modifier.testTag("monthPicker")) {
                Text(selected.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
            }
            IconButton(onClick = { date = selected.plusWeeks(1).toString() }, modifier = Modifier.semantics { contentDescription = "Next week" }) { Text("›") }
            TextButton(onClick = { date = LocalDate.now().toString() }, modifier = Modifier.testTag("today")) { Text("Today") }
        }
        val start = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { offset ->
                val day = start.plusDays(offset.toLong())
                val active = day == selected
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (active) FitnessColors.Accent else FitnessColors.Card)
                    .selectable(selected = active, onClick = { date = day.toString() }).padding(vertical = 12.dp)
                    .testTag("day_$day"), horizontalAlignment = Alignment.CenterHorizontally) {
                    val color = if (active) FitnessColors.Background else MaterialTheme.colorScheme.onSurface
                    Text(day.format(DateTimeFormatter.ofPattern("EE")).take(2), color = color, style = MaterialTheme.typography.labelSmall)
                    Text(day.dayOfMonth.toString(), color = color, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), modifier = Modifier.testTag("selectedDate"))
        if (!ready) {
            if (state.error != null && state.date == date) {
                Text(state.error.orEmpty())
                Button(onClick = { retry++ }) { Text("Try again") }
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            val totals = nutritionTotals(state.entries)
            NutritionSummary(totals, goal, state.entries.any { it.snapshot?.estimated == true })
            MicroCard(totals)
            MealType.entries.forEach { meal ->
                val entries = state.entries.filter { it.mealType == meal }
                val subtotal = nutritionTotals(entries)
                Card(Modifier.fillMaxWidth().testTag("section_${meal.name}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(meal.label(), style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { addingMeal = meal.name }, modifier = Modifier.testTag("add_${meal.name}")) { Text("+ Add") }
                        }
                        Text("${displayNumber(subtotal.calories)} kcal · P ${displayNumber(subtotal.protein)} g · F ${displayNumber(subtotal.fat)} g · C ${displayNumber(subtotal.carbs)} g",
                            modifier = Modifier.testTag("subtotal_${meal.name}"))
                        if (entries.isEmpty()) Text("No entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        entries.forEach { entry ->
                            val snapshot = requireNotNull(entry.snapshot)
                            val nutrition = snapshot.scaled(entry.amount, entry.unit)
                            HorizontalDivider()
                            Text(snapshot.name, style = MaterialTheme.typography.titleMedium)
                            Text("${displayNumber(entry.amount)} ${entry.unit} · ${displayNumber(nutrition.caloriesKcal)} kcal${if (snapshot.estimated) " · estimated" else ""}")
                            Row {
                                TextButton(onClick = { editingEntry = entry.id }, modifier = Modifier.testTag("editEntry_${entry.id}")) { Text("Edit") }
                                TextButton(onClick = { deleting = entry.id; deleteError = null }, modifier = Modifier.testTag("deleteEntry_${entry.id}")) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(selected))
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { date = pickerDate(it).toString() }; calendar = false }) { Text("Choose date") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } }) { DatePicker(state = picker) }
    }
    if (ready) {
        val entry = state.entries.find { it.id == editingEntry }
        val logged = state.entries.find { it.id == loggedEditor }
        when {
            logged != null -> key("logged", logged.id) {
                val snapshot = requireNotNull(logged.snapshot)
                FoodEditor(Food(id = logged.foodId, name = snapshot.name, servingAmount = snapshot.servingAmount,
                    servingUnit = snapshot.servingUnit, nutrients = snapshot.nutrients, source = snapshot.source, estimated = snapshot.estimated),
                    logged = true, onClose = { loggedEditor = null }) { updated ->
                    repository.editLoggedNutrition(logged.id, updated); loggedEditor = null
                }
            }
            foodEditor != null -> key("food", foodEditor) {
                FoodEditor(state.foods.find { it.id == foodEditor }, logged = false, onClose = { foodEditor = null }) { food ->
                    chosenFood = repository.saveFood(food); foodEditor = null
                }
            }
            entry != null -> key("entry", entry.id) {
                QuantityEditor(requireNotNull(entry.snapshot), entry.mealType, entry, onClose = { editingEntry = null },
                    onEditNutrition = { loggedEditor = entry.id }) { meal, amount, unit ->
                    repository.editEntry(entry.id, meal, amount, unit); editingEntry = null
                }
            }
            chosenFood != null && addingMeal != null -> {
                val food = state.foods.find { it.id == chosenFood }
                if (food != null) key("quantity", food.id) {
                    QuantityEditor(food.snapshot(), MealType.valueOf(addingMeal!!), null,
                        onClose = { chosenFood = null }, onEditNutrition = { foodEditor = food.id }) { meal, amount, unit ->
                        repository.addEntry(food.id, date, meal, amount, unit); chosenFood = null; addingMeal = null
                    }
                }
            }
            addingMeal != null -> FoodPicker(state.foods, state.recent, onClose = { addingMeal = null },
                onChoose = { chosenFood = it }, onCreate = { foodEditor = 0L }, onEdit = { foodEditor = it })
        }
    }
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { if (!deleteBusy) deleting = null }, title = { Text("Delete meal entry?") },
            text = { Text(deleteError ?: "This removes only the logged entry. The saved food stays available.") },
            confirmButton = { TextButton(enabled = !deleteBusy, onClick = {
                deleteBusy = true
                scope.launch {
                    try { repository.deleteEntry(id); deleting = null }
                    catch (failure: Exception) { if (failure is CancellationException) throw failure; deleteError = "Could not delete. Try again." }
                    finally { deleteBusy = false }
                }
            }, modifier = Modifier.testTag("confirmDeleteEntry")) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !deleteBusy, onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun NutritionSummary(totals: NutritionTotals, goal: Int, estimated: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Day summary", style = MaterialTheme.typography.titleLarge)
            Text("${displayNumber(totals.calories)} / $goal kcal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("dayCalories"))
            LinearProgressIndicator(progress = { progressFraction(totals.calories, goal.toDouble()) }, modifier = Modifier.fillMaxWidth())
            Text(if (totals.calories > goal) "${displayNumber(totals.calories - goal)} kcal over goal" else "${displayNumber(goal - totals.calories)} kcal remaining")
            listOf(Triple("Protein", totals.protein, FitnessColors.Protein), Triple("Fat", totals.fat, FitnessColors.Fat), Triple("Carbs", totals.carbs, FitnessColors.Carbs)).forEach { (name, grams, color) ->
                Text("$name · ${displayNumber(grams)} g", color = color)
                LinearProgressIndicator(progress = { progressFraction(grams * if (name == "Fat") 9 else 4, goal.toDouble()) },
                    modifier = Modifier.fillMaxWidth(), color = color)
            }
            Text("Bars: estimated macro energy toward your daily calorie goal (4/9/4 kcal per g). Edit entries or your goal to adjust.", style = MaterialTheme.typography.bodySmall)
            if (estimated) Text("Includes estimated entries", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MicroCard(totals: NutritionTotals) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Micronutrients", style = MaterialTheme.typography.titleLarge)
            totals.micros.forEach { (micro, total) ->
                val value = when {
                    total.count > 0 && total.known == 0 -> "Unknown"
                    total.known < total.count -> "${displayNumber(total.amount)} ${micro.unit} + unknown"
                    else -> "${displayNumber(total.amount)} ${micro.unit}"
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(micro.label); Text(value, modifier = Modifier.testTag("micro_${micro.name}"))
                }
            }
            if (totals.micros.values.any { it.known < it.count }) Text("Partial totals show known amounts only. Missing values are not measured zero.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FoodPicker(foods: List<Food>, recent: List<Food>, onClose: () -> Unit, onChoose: (Long) -> Unit, onCreate: () -> Unit, onEdit: (Long) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var recentOnly by rememberSaveable { mutableStateOf(false) }
    FoodDialog("Choose food", onClose = onClose) {
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().testTag("createFood")) { Text("Create manual food") }
        OutlinedTextField(search, onValueChange = { search = it }, label = { Text("Search saved foods") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!recentOnly, onClick = { recentOnly = false }, label = { Text("Saved") })
            FilterChip(recentOnly, onClick = { recentOnly = true }, label = { Text("Recent") }, modifier = Modifier.testTag("recentFoods"))
        }
        val matches = (if (recentOnly) recent else foods).filter { it.name.contains(search, ignoreCase = true) }
        if (matches.isEmpty()) Text(if (recentOnly) "No recently logged foods" else "No matching saved foods")
        matches.forEach { food ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(food.name, style = MaterialTheme.typography.titleMedium)
                    Text("${displayNumber(food.nutrients.caloriesKcal)} kcal per ${displayNumber(food.servingAmount)} ${food.servingUnit}${if (food.estimated) " · estimated" else ""}")
                    Row {
                        TextButton(onClick = { onChoose(food.id) }, modifier = Modifier.testTag("chooseFood_${food.id}")) { Text("Choose") }
                        TextButton(onClick = { onEdit(food.id) }, modifier = Modifier.testTag("editFood_${food.id}")) { Text("Edit saved food") }
                    }
                }
            }
        }
    }
}

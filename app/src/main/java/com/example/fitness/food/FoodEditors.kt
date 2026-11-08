package com.example.fitness.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fitness.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun FoodDialog(title: String, busy: Boolean = false, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = onClose) { Text("Close") }
                }
                content()
            }
        }
    }
}

@Composable
internal fun FoodEditor(food: Food?, logged: Boolean, missing: Set<String> = emptySet(), notice: String? = null, onClose: () -> Unit, onSave: suspend (Food) -> Unit) {
    var name by rememberSaveable { mutableStateOf(food?.name.orEmpty()) }
    var servingAmount by rememberSaveable { mutableStateOf(food?.servingAmount?.toString() ?: "100") }
    var unit by rememberSaveable { mutableStateOf(food?.servingUnit ?: "g") }
    var calories by rememberSaveable { mutableStateOf(if ("caloriesKcal" in missing) "" else food?.nutrients?.caloriesKcal?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(if ("proteinG" in missing) "" else food?.nutrients?.proteinG?.toString().orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(if ("fatG" in missing) "" else food?.nutrients?.fatG?.toString().orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(if ("carbsG" in missing) "" else food?.nutrients?.carbsG?.toString().orEmpty()) }
    var fiber by rememberSaveable { mutableStateOf(food?.nutrients?.fiberG?.toString().orEmpty()) }
    var sodium by rememberSaveable { mutableStateOf(food?.nutrients?.sodiumMg?.toString().orEmpty()) }
    var calcium by rememberSaveable { mutableStateOf(food?.nutrients?.calciumMg?.toString().orEmpty()) }
    var iron by rememberSaveable { mutableStateOf(food?.nutrients?.ironMg?.toString().orEmpty()) }
    var potassium by rememberSaveable { mutableStateOf(food?.nutrients?.potassiumMg?.toString().orEmpty()) }
    var magnesium by rememberSaveable { mutableStateOf(food?.nutrients?.magnesiumMg?.toString().orEmpty()) }
    var vitaminC by rememberSaveable { mutableStateOf(food?.nutrients?.vitaminCMg?.toString().orEmpty()) }
    var vitaminD by rememberSaveable { mutableStateOf(food?.nutrients?.vitaminDMcg?.toString().orEmpty()) }
    var estimated by rememberSaveable { mutableStateOf(food?.estimated ?: false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FoodDialog(if (logged) "Edit logged nutrition" else if (food == null) "Create food" else "Edit saved food", busy, onClose) {
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (missing.isNotEmpty()) Text("Some required values are unknown. Fill the blank macros from the label before saving.")
        Text(if (logged) "Changes apply only to this entry. Past entries and your saved food stay as recorded."
             else "Nutrition is per saved serving. Editing a saved food affects future logs only.")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Food name") },
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("foodName"))
        UnitChips(listOf("g", "ml", "serving"), unit, !busy && !logged) { unit = it }
        if (unit != "serving") NumberField("Serving amount ($unit)", servingAmount, "servingAmount", !busy && !logged) { servingAmount = it }
        Text(if (unit == "serving") "Nutrition per 1 serving" else "Nutrition per $servingAmount $unit", style = MaterialTheme.typography.titleMedium)
        NumberField("Calories (kcal)", calories, "calories", !busy) { calories = it }
        NumberField("Protein (g)", protein, "protein", !busy) { protein = it }
        NumberField("Fat (g)", fat, "fat", !busy) { fat = it }
        NumberField("Carbs (g)", carbs, "carbs", !busy) { carbs = it }
        Text("Micronutrients", style = MaterialTheme.typography.titleLarge)
        Text("Leave unknown values blank. Enter 0 only when you know the amount is zero.")
        NumberField("Fiber (g)", fiber, "fiber", !busy) { fiber = it }
        NumberField("Sodium (mg)", sodium, "sodium", !busy) { sodium = it }
        NumberField("Calcium (mg)", calcium, "calcium", !busy) { calcium = it }
        NumberField("Iron (mg)", iron, "iron", !busy) { iron = it }
        NumberField("Potassium (mg)", potassium, "potassium", !busy) { potassium = it }
        NumberField("Magnesium (mg)", magnesium, "magnesium", !busy) { magnesium = it }
        NumberField("Vitamin C (mg)", vitaminC, "vitaminC", !busy) { vitaminC = it }
        NumberField("Vitamin D (µg)", vitaminD, "vitaminD", !busy) { vitaminD = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Nutrition is estimated", modifier = Modifier.weight(1f))
            Switch(checked = estimated, onCheckedChange = { estimated = it }, enabled = !busy, modifier = Modifier.testTag("foodEstimated"))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("foodError")) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("saveFood"), onClick = {
            fun optional(text: String, label: String) = text.takeIf { it.isNotBlank() }?.let { foodNumber(it, label) }
            val updated = try {
                Food(id = food?.id ?: 0L, name = name.trim(),
                    servingAmount = if (unit == "serving") 1.0 else foodNumber(servingAmount, "Serving amount", true),
                    servingUnit = unit, source = food?.source ?: FoodSource.MANUAL, estimated = estimated,
                    nutrients = Nutrients(foodNumber(calories, "Calories"), foodNumber(protein, "Protein"),
                        foodNumber(fat, "Fat"), foodNumber(carbs, "Carbs"), optional(fiber, "Fiber"),
                        optional(sodium, "Sodium"), optional(calcium, "Calcium"), optional(iron, "Iron"),
                        optional(potassium, "Potassium"), optional(magnesium, "Magnesium"),
                        optional(vitaminC, "Vitamin C"), optional(vitaminD, "Vitamin D")))
                    .also(::validateFood)
            } catch (invalid: IllegalArgumentException) { error = invalid.message; return@Button }
            busy = true; error = null
            scope.launch {
                try { onSave(updated) }
                catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Could not save this food. Your values are still here; try again."
                } finally { busy = false }
            }
        }) { Text(if (busy) "Saving…" else if (logged) "Save logged nutrition" else "Save food") }
    }
}

@Composable
internal fun NumberField(label: String, value: String, tag: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
internal fun UnitChips(units: List<String>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        units.forEach { unit -> FilterChip(selected = unit == selected, onClick = { onSelect(unit) }, enabled = enabled,
            label = { Text(if (unit == "serving") "serving" else unit) }, modifier = Modifier.testTag("unit_$unit")) }
    }
}

@Composable
internal fun QuantityEditor(snapshot: FoodSnapshot, initialMeal: MealType, entry: MealEntry?, onClose: () -> Unit,
    onEditNutrition: () -> Unit, onSave: suspend (MealType, Double, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(entry?.amount?.toString() ?: "1") }
    var unit by rememberSaveable { mutableStateOf(entry?.unit ?: "serving") }
    var meal by rememberSaveable { mutableStateOf(initialMeal.name) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preview = runCatching { snapshot.scaled(foodNumber(amount, "Amount", true), unit) }.getOrNull()
    FoodDialog(if (entry == null) "Add to meal" else "Edit meal entry", busy, onClose) {
        Text(snapshot.name, style = MaterialTheme.typography.headlineSmall)
        Text("1 serving = ${displayNumber(snapshot.servingAmount)} ${snapshot.servingUnit}")
        if (snapshot.estimated) Text("Estimated · editable", color = MaterialTheme.colorScheme.primary)
        NumberField("Amount", amount, "entryAmount", !busy) { amount = it }
        UnitChips(listOf("serving", snapshot.servingUnit).distinct(), unit, !busy) { unit = it }
        Text("Meal", style = MaterialTheme.typography.titleMedium)
        MealType.entries.forEach { option ->
            FilterChip(selected = meal == option.name, onClick = { meal = option.name }, enabled = !busy,
                label = { Text(option.label()) }, modifier = Modifier.testTag("meal_${option.name}"))
        }
        preview?.let { Text("${displayNumber(it.caloriesKcal)} kcal · P ${displayNumber(it.proteinG)} g · F ${displayNumber(it.fatG)} g · C ${displayNumber(it.carbsG)} g",
            modifier = Modifier.testTag("entryPreview")) }
        TextButton(enabled = !busy, onClick = onEditNutrition) { Text(if (entry == null) "Edit saved food" else "Edit logged nutrition") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("entryError")) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("saveEntry"), onClick = {
            val parsed = try { foodNumber(amount, "Amount", true).also { snapshot.factor(it, unit) } }
                catch (invalid: IllegalArgumentException) { error = invalid.message; return@Button }
            busy = true; error = null
            scope.launch {
                try { onSave(MealType.valueOf(meal), parsed, unit) }
                catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Could not save this entry. Your values are still here; try again."
                } finally { busy = false }
            }
        }) { Text(if (busy) "Saving…" else if (entry == null) "Add entry" else "Save entry") }
    }
}

fun MealType.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

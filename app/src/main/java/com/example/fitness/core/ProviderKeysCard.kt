package com.example.fitness.core

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fitness.food.lookup.LookupSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Composable
fun ProviderKeysCard() {
    val context = LocalContext.current
    val secrets = remember { Secrets(context) }
    val settings = remember { LookupSettings(context) }
    var selected by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") } // Keys must never enter a saved Bundle.
    var model by remember { mutableStateOf("gemini-flash-lite-latest") }
    var configured by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val providers = mapOf("usda" to "USDA FoodData Central", "gemini" to "Gemini", "maptiler" to "MapTiler")
    LaunchedEffect(Unit) {
        try { configured = providers.keys.associateWith { secrets.get(it).isNotBlank() }; model = settings.model.first() }
        catch (_: Exception) { error = "Some encrypted keys could not be read. Enter them again if needed." }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Provider settings", style = MaterialTheme.typography.titleLarge)
            Text("Optional keys are encrypted with Android Keystore. Manual food logging, Swiss offline lookup, steps and gym work without them.")
            providers.forEach { (id, label) ->
                OutlinedButton(onClick = { selected = id; draft = ""; error = null }) { Text("$label · ${if (configured[id] == true) "configured" else "not set"}") }
            }
            error?.let { Text(it) }
        }
    }
    selected?.let { id -> AlertDialog(onDismissRequest = { if (!busy) { selected = null; draft = "" } },
        title = { Text("${providers[id]} key") }, text = { Column {
            Text("Enter your own key. Saving an empty field removes it. Keys are not included in exports.")
            OutlinedTextField(draft, onValueChange = { draft = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), enabled = !busy)
            if (id == "gemini") {
                Text("Free-tier Gemini content may be used by Google to improve products. Only explicit foreground requests or your enabled lookup fallback send data.")
                OutlinedTextField(model, onValueChange = { model = it }, label = { Text("Gemini model ID") }, singleLine = true, enabled = !busy)
            }
            error?.let { Text(it) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    if (id == "gemini") settings.model(model)
                    secrets.put(id, draft); configured = configured + (id to draft.isNotBlank()); draft = ""; selected = null; error = null
                } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "Could not save. Check the model ID and try again." }
                finally { busy = false }
            }
        }) { Text("Save") } }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null; draft = "" }) { Text("Cancel") } }) }
}

package com.example.fitness.food.lookup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.fitness.food.*
import java.io.File
import kotlinx.coroutines.*

@Composable
fun LookupDialog(onClose: () -> Unit, onImported: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FitnessApplication
    val controller = remember { LookupController(app, app.database) }
    val vision = remember { OfflineVision(app) }
    val mode by controller.settings.mode.collectAsStateWithLifecycle(initialValue = LookupMode.DATABASE_FIRST)
    val pending by controller.queued.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("All databases") }
    var code by rememberSaveable { mutableStateOf("") }
    var rawLabel by rememberSaveable { mutableStateOf("") }
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FoodCandidate>>(emptyList()) }
    var camera by rememberSaveable { mutableStateOf<String?>(null) }
    var photoName by rememberSaveable { mutableStateOf<String?>(null) }
    var imageAction by rememberSaveable { mutableStateOf("label") }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { block() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message = failure.message ?: "Lookup failed. Manual food entry is always available." }
            finally { busy = false }
        }
    }
    suspend fun process(uri: Uri, kind: String) {
        val file = vision.normalize(uri)
        if (kind == "meal") {
            photoName?.let { File(File(context.cacheDir, "selected_images"), it).delete() }
            photoName = file.name
            message = "Meal photo selected. It stays on this device until you request a Gemini estimate."
        } else try {
            if (kind == "label") {
                rawLabel = vision.text(file)
                draftJson = encodeDraft(parseLabel(rawLabel))
            } else {
                code = vision.barcode(file)
                val found = controller.barcode(code)
                if (found == null) message = "No database product found for $code. Create a manual food instead."
                else draftJson = encodeDraft(found)
            }
        } finally { withContext(Dispatchers.IO) { file.delete() } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { action { process(it, imageAction) } }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) camera = imageAction else message = "Camera permission was declined. Choose an existing image or use manual entry."
    }
    fun take(kind: String) {
        imageAction = kind
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = kind
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    val draft = draftJson?.let(::decodeDraft)
    if (draft != null) key(draftJson) {
        FoodEditor(draft.food, logged = false, missing = draft.missing, notice = draft.attribution,
            onClose = { draftJson = null }) { food ->
            val id = controller.confirm(draft, food)
            draftJson = null
            onImported(id)
        }
    } else if (camera != null) CameraCapture(when (camera) { "barcode" -> "Capture product barcode"; "meal" -> "Capture meal photo"; else -> "Capture nutrition label" },
        onClose = { camera = null }) { file -> process(Uri.fromFile(file), camera ?: "label"); camera = null }
    else FoodDialog("Food lookup and scanning", onClose = onClose) {
        Text("Imported values are editable drafts. Nothing is logged until you confirm the food and amount.")
        LookupMode.entries.forEach { value ->
            FilterChip(mode == value, enabled = !busy, onClick = { scope.launch { controller.settings.mode(value) } }, label = { Text(value.label) }, modifier = Modifier.testTag("lookupMode_${value.name}"))
        }
        Text("Gemini estimates are sent to Google only when you use AI or an enabled fallback. Free-tier content may be used to improve Google's products. Review before saving.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Food or meal description") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("lookupQuery"))
        if (mode != LookupMode.AI_ONLY) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = !busy) { Text(source) }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    listOf("All databases", "Swiss offline", "Swiss API", "Open Food Facts", "USDA").forEach { value ->
                        DropdownMenuItem(text = { Text(value) }, onClick = { source = value; expanded = false })
                    }
                }
            }
        }
        Button(enabled = !busy, onClick = { action {
            results = controller.search(query, source)
            if (results.isEmpty()) message = "No matching food found. Try a different source or create a manual food."
        } }, modifier = Modifier.testTag("searchFoods")) { Text(if (busy) "Working…" else "Search / estimate") }
        results.forEachIndexed { index, result ->
            OutlinedButton(onClick = { draftJson = encodeDraft(result) }, modifier = Modifier.fillMaxWidth().testTag("lookupResult_$index")) {
                Column(Modifier.fillMaxWidth()) { Text(result.food.name); Text(result.attribution, style = MaterialTheme.typography.bodySmall) }
            }
        }
        HorizontalDivider()
        Text("Barcode", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(code, onValueChange = { code = it }, label = { Text("Product barcode") }, enabled = !busy && mode != LookupMode.AI_ONLY, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(enabled = !busy && mode != LookupMode.AI_ONLY, onClick = { action {
                val found = controller.barcode(code); if (found == null) message = "Product not found." else draftJson = encodeDraft(found)
            } }) { Text("Look up code") }
            TextButton(enabled = !busy && mode != LookupMode.AI_ONLY, onClick = { take("barcode") }) { Text("Scan barcode") }
        }
        Text("Nutrition label OCR", style = MaterialTheme.typography.titleLarge)
        Text("Text and barcode recognition are on-device and work without internet. OCR can mix columns: verify amounts and serving basis.")
        Row {
            TextButton(enabled = !busy, onClick = { take("label") }) { Text("Scan label") }
            TextButton(enabled = !busy, onClick = { imageAction = "label"; picker.launch("image/*") }) { Text("Choose label image") }
        }
        OutlinedTextField(rawLabel, onValueChange = { rawLabel = it }, label = { Text("Or paste label text") }, modifier = Modifier.fillMaxWidth().testTag("labelText"))
        TextButton(enabled = !busy, onClick = { draftJson = encodeDraft(parseLabel(rawLabel)) }, modifier = Modifier.testTag("parseLabel")) { Text("Parse and review label") }
        Text("Meal photo estimate", style = MaterialTheme.typography.titleLarge)
        Row {
            TextButton(enabled = !busy && mode != LookupMode.DATABASE_ONLY, onClick = { take("meal") }) { Text("Take meal photo") }
            TextButton(enabled = !busy && mode != LookupMode.DATABASE_ONLY, onClick = { imageAction = "meal"; picker.launch("image/*") }) { Text("Choose meal photo") }
        }
        val photo = photoName?.takeIf { it.matches(Regex("[a-fA-F0-9-]+\\.jpg")) }?.let { File(File(context.cacheDir, "selected_images"), it) }
        if (photo?.isFile == true) {
            Text("Photo ready · on this device")
            Button(enabled = !busy && mode != LookupMode.DATABASE_ONLY, onClick = { action {
                draftJson = encodeDraft(controller.estimate(query.ifBlank { "Estimate this meal" }, photo))
            } }) { Text("Send photo to Gemini for estimate") }
        }
        if (pending.isNotEmpty()) {
            HorizontalDivider(); Text("Saved AI requests", style = MaterialTheme.typography.titleLarge)
            Text("No background uploads. Retry here on Wi-Fi or while charging; completed drafts can be reviewed offline.")
            pending.forEach { request ->
                Text(request.prompt.take(120))
                Row {
                    TextButton(enabled = !busy && (request.response != null || mode != LookupMode.DATABASE_ONLY), onClick = { action {
                        if (request.response != null || controller.canRetry()) draftJson = encodeDraft(controller.perform(request.id))
                        else message = "Connect to Wi-Fi or charge the device before retrying."
                    } }) { Text(if (request.response != null) "Review estimate" else "Retry") }
                    TextButton(enabled = !busy, onClick = { action { controller.discard(request.id) } }) { Text("Discard") }
                }
            }
        }
        message?.let { Text(it, modifier = Modifier.testTag("lookupMessage")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

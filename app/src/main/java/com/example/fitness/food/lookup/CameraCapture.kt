package com.example.fitness.food.lookup

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

@Composable
fun CameraCapture(title: String, onClose: () -> Unit, onImage: suspend (File) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val preview = remember { Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) } }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        val disposed = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            if (!disposed.get()) {
                try {
                    check(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    provider = providerFuture.get()
                    provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    ready = true
                } catch (_: Exception) { error = "Camera unavailable. Choose a photo or enter values manually." }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed.set(true); provider?.unbind(preview, capture) }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f))
                error?.let { Text(it) }
                Row {
                    Button(enabled = ready && !busy, onClick = {
                        busy = true
                        val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
                        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exception: ImageCaptureException) { busy = false; error = "Could not capture the image. Try again."; file.delete() }
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        try { onImage(file) }
                                        catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Could not process the image." }
                                        finally { file.delete(); busy = false }
                                    }
                                }
                            })
                    }) { Text(if (busy) "Processing…" else "Capture") }
                    TextButton(enabled = !busy, onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}

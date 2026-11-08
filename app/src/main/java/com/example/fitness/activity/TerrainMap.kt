package com.example.fitness.activity

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitness.core.ForegroundNetwork
import com.example.fitness.data.ActivityRangeState
import java.io.ByteArrayInputStream
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled") // Bundled trusted renderer only; no JS bridge or remote scripts.
@Composable
internal fun TerrainMap(state: ActivityRangeState, key: String, cursor: MapCoordinate?, follow: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = remember { ForegroundNetwork.client(context) }
    val config = remember(state.points, state.segments, key) {
        val first = state.segments.firstOrNull()?.let { segmentCoordinates(state.points, it).firstOrNull() }
        JSONObject().put("key", key).put("center", JSONArray(listOf(first?.lon ?: 0.0, first?.lat ?: 0.0)))
            .put("style", JSONObject(mapStyle(true, key, state.points, state.segments))).toString()
    }
    var pageReady by remember { mutableStateOf(false) }
    val view = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = "Fitness/0.1 (Android; com.example.fitness)"
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (url?.startsWith("file:///android_asset/terrain/") == true) {
                        pageReady = true
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.scheme == "file" && request.url.path?.startsWith("/android_asset/terrain/") == true) return null
                    if (request.url.scheme != "https" || request.url.host != "api.maptiler.com" || !ForegroundNetwork.visible)
                        return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    return try {
                        val response = client.newCall(Request.Builder().url(request.url.toString()).build()).execute()
                        val bytes = response.use { it.body?.bytes() ?: ByteArray(0) }
                        // The local renderer fetches directly from MapTiler; never persist
                        // authenticated URLs in WebView's cache or expose key-bearing errors.
                        WebResourceResponse(response.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream", null,
                            response.code, response.message.ifBlank { "Response" }, mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"), ByteArrayInputStream(bytes))
                    } catch (_: Exception) { WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))) }
                }
            }
        }
    }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { view.settings.blockNetworkLoads = false; view.loadUrl("file:///android_asset/terrain/terrain.html") }
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> { pageReady = false; view.stopLoading(); view.settings.blockNetworkLoads = true; view.loadUrl("about:blank") }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.stopLoading(); view.loadUrl("about:blank"); view.destroy() }
    }
    LaunchedEffect(config, pageReady) { if (pageReady) view.evaluateJavascript("window.initTerrain($config)", null) }
    LaunchedEffect(cursor, follow, pageReady) {
        if (pageReady) {
            val json = cursor?.let { JSONObject().put("lat", it.lat).put("lon", it.lon).put("follow", follow).toString() } ?: "null"
            view.evaluateJavascript("window.terrainCursor($json)", null)
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

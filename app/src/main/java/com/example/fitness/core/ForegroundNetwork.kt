package com.example.fitness.core

import android.content.Context
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.*

/** Shared HTTP gate: background components cannot start requests. */
object ForegroundNetwork {
    private val windows = AtomicInteger(0)
    @Volatile private var http: OkHttpClient? = null
    val visible: Boolean get() = windows.get() > 0
    fun enter() { windows.incrementAndGet() }
    fun leave() {
        windows.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (!visible) http?.dispatcher?.cancelAll()
    }
    @Synchronized fun client(context: Context): OkHttpClient {
        http?.let { return it }
        return OkHttpClient.Builder().cache(Cache(java.io.File(context.cacheDir, "viewed_tiles"), 32L * 1024 * 1024))
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                if (!visible) throw IOException("Network access requires a visible app window.")
                val request = chain.request()
                if (!request.url.isHttps) throw IOException("Only HTTPS is supported.")
                val authenticated = request.url.queryParameterNames.any { it.lowercase() in setOf("key", "api_key", "apikey", "access_token") }
                val builder = request.newBuilder().header("User-Agent", "Fitness/0.1 (Android; com.example.fitness)")
                if (authenticated || request.method != "GET") builder.cacheControl(CacheControl.Builder().noStore().build())
                val response = chain.proceed(builder.build())
                // Never retain authenticated URLs or request payloads in the disk cache.
                if (authenticated || request.method != "GET") response.newBuilder().header("Cache-Control", "no-store").build()
                else response
            }.build().also { http = it }
    }
}

package com.example.fitness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitness.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapSecurityTest {
    @Test fun providerKeysAreEncryptedAndBoundToTheirNames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secrets = Secrets(context)
        val preferences = context.getSharedPreferences("encrypted_provider_keys", 0)
        try {
            secrets.put("test_provider", "not-a-real-api-key")
            val stored = preferences.getString("test_provider", null)!!
            assertFalse(stored.contains("not-a-real-api-key"))
            assertEquals("not-a-real-api-key", secrets.get("test_provider"))
            preferences.edit().putString("test_wrong_name", stored).commit()
            var failed = false
            try { secrets.get("test_wrong_name") } catch (_: Exception) { failed = true }
            assertTrue(failed)
        } finally { secrets.put("test_provider", ""); secrets.put("test_wrong_name", "") }
    }
    @Test fun backgroundHttpIsRejectedBeforeAnyConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(ForegroundNetwork.visible)
        val client = ForegroundNetwork.client(context)
        val error = assertThrows(java.io.IOException::class.java) {
            client.newCall(Request.Builder().url("https://example.invalid/").build()).execute().close()
        }
        assertTrue(error.message.orEmpty().contains("visible"))
    }
}

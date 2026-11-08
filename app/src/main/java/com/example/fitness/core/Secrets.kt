package com.example.fitness.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Secrets(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("encrypted_provider_keys", Context.MODE_PRIVATE)
    private fun key(): SecretKey = synchronized(Secrets::class.java) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("fitness_provider_keys_v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("fitness_provider_keys_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    suspend fun get(name: String): String = withContext(Dispatchers.IO) {
        val stored = preferences.getString(name, null) ?: return@withContext ""
        val parts = stored.split(':')
        check(parts.size == 2) { "Invalid encrypted key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    suspend fun put(name: String, value: String) = withContext(Dispatchers.IO) {
        if (value.isBlank()) { check(preferences.edit().remove(name).commit()); return@withContext }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(preferences.edit().putString(name, encrypted).commit())
    }
}

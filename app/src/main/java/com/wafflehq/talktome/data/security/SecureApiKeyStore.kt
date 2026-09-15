package com.wafflehq.talktome.data.security

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wafflehq.talktome.di.GeminiApiKeyDataStore
import com.wafflehq.uikit.security.AesGcmBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureApiKeyStore @Inject constructor(
    @param:GeminiApiKeyDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: AesGcmBox,
) {
    private val ivKey = stringPreferencesKey("gemini_api_key_iv")
    private val ciphertextKey = stringPreferencesKey("gemini_api_key_ciphertext")

    val hasApiKey: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[ciphertextKey].isNullOrEmpty()
    }

    suspend fun getApiKey(): String? {
        val prefs = dataStore.data.first()
        val ivBase64 = prefs[ivKey] ?: run {
            Log.d(TAG, "getApiKey: no key stored")
            return null
        }
        val ciphertextBase64 = prefs[ciphertextKey] ?: run {
            Log.d(TAG, "getApiKey: no key stored")
            return null
        }
        val iv = Base64.getDecoder().decode(ivBase64)
        val ciphertext = Base64.getDecoder().decode(ciphertextBase64)
        return try {
            cipher.decrypt(iv, ciphertext).toString(Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "getApiKey: decrypt failed with ${e.javaClass.simpleName}, clearing corrupted entry", e)
            clearApiKey()
            null
        }
    }

    suspend fun setApiKey(apiKey: String) {
        require(apiKey.isNotBlank()) { "API key must not be blank" }
        val payload = cipher.encrypt(apiKey.trim().toByteArray(Charsets.UTF_8))
        dataStore.edit { prefs ->
            prefs[ivKey] = Base64.getEncoder().encodeToString(payload.iv)
            prefs[ciphertextKey] = Base64.getEncoder().encodeToString(payload.ciphertext)
        }
    }

    suspend fun clearApiKey() {
        dataStore.edit { prefs ->
            prefs.remove(ivKey)
            prefs.remove(ciphertextKey)
        }
    }

    private companion object {
        const val TAG = "SecureApiKeyStore"
    }
}

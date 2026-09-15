package com.wafflehq.talktome.data.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wafflehq.talktome.di.DeviceIdentityDataStore
import com.wafflehq.talktome.di.DeviceTokenCipher
import com.wafflehq.uikit.security.AesGcmBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceIdentity(val deviceId: String, val token: String, val serverBaseUrl: String)

@Singleton
class DeviceIdentityStore @Inject constructor(
    @param:DeviceIdentityDataStore private val dataStore: DataStore<Preferences>,
    @param:DeviceTokenCipher private val cipher: AesGcmBox,
) {
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val tokenIvKey = stringPreferencesKey("device_token_iv")
    private val tokenCiphertextKey = stringPreferencesKey("device_token_ciphertext")
    private val serverBaseUrlKey = stringPreferencesKey("server_base_url")

    val hasIdentity: Flow<Boolean> = dataStore.data.map { prefs -> !prefs[deviceIdKey].isNullOrEmpty() }

    suspend fun getIdentity(): DeviceIdentity? {
        val prefs = dataStore.data.first()
        val deviceId = prefs[deviceIdKey] ?: return null
        val ivBase64 = prefs[tokenIvKey] ?: return null
        val ciphertextBase64 = prefs[tokenCiphertextKey] ?: return null
        val serverBaseUrl = prefs[serverBaseUrlKey] ?: return null
        val iv = Base64.getDecoder().decode(ivBase64)
        val ciphertext = Base64.getDecoder().decode(ciphertextBase64)
        val token = cipher.decrypt(iv, ciphertext).toString(Charsets.UTF_8)
        return DeviceIdentity(deviceId, token, serverBaseUrl)
    }

    suspend fun saveIdentity(identity: DeviceIdentity) {
        val payload = cipher.encrypt(identity.token.toByteArray(Charsets.UTF_8))
        dataStore.edit { prefs ->
            prefs[deviceIdKey] = identity.deviceId
            prefs[tokenIvKey] = Base64.getEncoder().encodeToString(payload.iv)
            prefs[tokenCiphertextKey] = Base64.getEncoder().encodeToString(payload.ciphertext)
            prefs[serverBaseUrlKey] = identity.serverBaseUrl
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

package com.wafflehq.talktome.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wafflehq.talktome.data.gemini.GeminiModel
import com.wafflehq.talktome.di.SettingsDataStore
import com.wafflehq.uikit.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val geminiModelKey = stringPreferencesKey("gemini_model")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromName(prefs[themeModeKey])
    }

    val geminiModel: Flow<GeminiModel> = dataStore.data.map { prefs ->
        GeminiModel.fromWireId(prefs[geminiModelKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setGeminiModel(model: GeminiModel) {
        dataStore.edit { prefs ->
            prefs[geminiModelKey] = model.wireId
        }
    }
}

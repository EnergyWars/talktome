package com.wafflehq.talktome.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.wafflehq.talktome.data.gemini.GeminiModel
import com.wafflehq.uikit.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newRepository(): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("settings_${System.nanoTime()}.preferences_pb") },
        )
        return SettingsRepository(dataStore)
    }

    @Test
    fun `theme mode defaults to system when nothing stored`() = runTest {
        val repository = newRepository()

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `setThemeMode persists and is reflected by the flow`() = runTest {
        val repository = newRepository()

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun `setThemeMode overwrites a previous value`() = runTest {
        val repository = newRepository()

        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun `gemini model defaults to FLASH when nothing stored`() = runTest {
        val repository = newRepository()

        assertEquals(GeminiModel.DEFAULT, repository.geminiModel.first())
    }

    @Test
    fun `setGeminiModel persists and is reflected by the flow`() = runTest {
        val repository = newRepository()

        repository.setGeminiModel(GeminiModel.PRO)

        assertEquals(GeminiModel.PRO, repository.geminiModel.first())
    }

    @Test
    fun `setGeminiModel overwrites a previous value`() = runTest {
        val repository = newRepository()

        repository.setGeminiModel(GeminiModel.PRO)
        repository.setGeminiModel(GeminiModel.FLASH_LITE)

        assertEquals(GeminiModel.FLASH_LITE, repository.geminiModel.first())
    }
}

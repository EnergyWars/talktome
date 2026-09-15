package com.wafflehq.talktome.ui.settings

import com.wafflehq.talktome.data.settings.SettingsRepository
import com.wafflehq.uikit.theme.ThemeMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `themeMode reflects the repository value`() = runTest(dispatcher) {
        every { repository.themeMode } returns flowOf(ThemeMode.DARK)

        val viewModel = SettingsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `themeMode defaults to SYSTEM before the repository emits`() = runTest(dispatcher) {
        every { repository.themeMode } returns flowOf(ThemeMode.LIGHT)

        val viewModel = SettingsViewModel(repository)

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }

    @Test
    fun `onThemeModeSelected persists the chosen mode`() = runTest(dispatcher) {
        every { repository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        val viewModel = SettingsViewModel(repository)

        viewModel.onThemeModeSelected(ThemeMode.DARK)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setThemeMode(ThemeMode.DARK) }
    }
}

package com.wafflehq.talktome.ui.gemini

import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiConnectionResult
import com.wafflehq.talktome.data.gemini.GeminiModel
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import com.wafflehq.talktome.data.settings.SettingsRepository
import io.mockk.coEvery
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

class GeminiSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var geminiClient: GeminiClient
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        secureApiKeyStore = mockk(relaxed = true)
        geminiClient = mockk()
        settingsRepository = mockk(relaxed = true)
        every { secureApiKeyStore.hasApiKey } returns flowOf(false)
        every { settingsRepository.geminiModel } returns flowOf(GeminiModel.DEFAULT)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GeminiSettingsViewModel(secureApiKeyStore, geminiClient, settingsRepository)

    @Test
    fun `saving a blank key fails without calling the client`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onApiKeyInputChanged("   ")
        vm.onSaveAndTest()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            GeminiConnectionUiState.Failed(GeminiConnectionResult.InvalidApiKey),
            vm.uiState.value.connectionState,
        )
        coVerify(exactly = 0) { geminiClient.verifyApiKey(any()) }
    }

    @Test
    fun `successful verification stores the key and clears the input`() = runTest(dispatcher) {
        coEvery { geminiClient.verifyApiKey("valid-key") } returns GeminiConnectionResult.Success
        val vm = viewModel()

        vm.onApiKeyInputChanged("valid-key")
        vm.onSaveAndTest()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(GeminiConnectionUiState.Success, vm.uiState.value.connectionState)
        assertEquals("", vm.uiState.value.apiKeyInput)
        coVerify { secureApiKeyStore.setApiKey("valid-key") }
    }

    @Test
    fun `failed verification does not store the key`() = runTest(dispatcher) {
        coEvery { geminiClient.verifyApiKey("bad-key") } returns GeminiConnectionResult.InvalidApiKey
        val vm = viewModel()

        vm.onApiKeyInputChanged("bad-key")
        vm.onSaveAndTest()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            GeminiConnectionUiState.Failed(GeminiConnectionResult.InvalidApiKey),
            vm.uiState.value.connectionState,
        )
        coVerify(exactly = 0) { secureApiKeyStore.setApiKey(any()) }
    }

    @Test
    fun `changing the input resets a previous failure state`() = runTest(dispatcher) {
        coEvery { geminiClient.verifyApiKey("bad-key") } returns GeminiConnectionResult.RateLimited
        val vm = viewModel()
        vm.onApiKeyInputChanged("bad-key")
        vm.onSaveAndTest()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onApiKeyInputChanged("bad-key2")

        assertEquals(GeminiConnectionUiState.Idle, vm.uiState.value.connectionState)
    }

    @Test
    fun `onClearApiKey clears the stored key`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onClearApiKey()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { secureApiKeyStore.clearApiKey() }
    }

    @Test
    fun `onToggleVisibility flips the visibility flag`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onToggleVisibility()

        assertEquals(true, vm.uiState.value.isKeyVisible)
    }

    @Test
    fun `selectedModel reflects the repository value`() = runTest(dispatcher) {
        every { settingsRepository.geminiModel } returns flowOf(GeminiModel.PRO)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(GeminiModel.PRO, vm.selectedModel.value)
    }

    @Test
    fun `onModelSelected persists the chosen model`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onModelSelected(GeminiModel.FLASH_LITE)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setGeminiModel(GeminiModel.FLASH_LITE) }
    }
}

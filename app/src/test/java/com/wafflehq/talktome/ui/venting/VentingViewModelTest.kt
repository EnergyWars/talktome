package com.wafflehq.talktome.ui.venting

import com.wafflehq.talktome.data.db.MediatorAgentRole
import com.wafflehq.talktome.data.db.MediatorNoteDao
import com.wafflehq.talktome.data.db.MediatorNoteEntity
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiErrorReason
import com.wafflehq.talktome.data.gemini.GeminiGenerateContentResult
import com.wafflehq.talktome.data.security.SecureApiKeyStore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VentingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var geminiClient: GeminiClient
    private lateinit var mediatorNoteDao: MediatorNoteDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        secureApiKeyStore = mockk()
        geminiClient = mockk()
        mediatorNoteDao = mockk(relaxed = true)
        every { mediatorNoteDao.observeByRole(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = VentingViewModel(secureApiKeyStore, geminiClient, mediatorNoteDao)

    @Test
    fun `sending blank input does nothing`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onInputChanged("   ")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.turns.isEmpty())
    }

    @Test
    fun `sending a message adds the user turn immediately and the reply once it arrives`() = runTest(dispatcher) {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns GeminiGenerateContentResult.Success("Das klingt frustrierend.")
        val vm = viewModel()

        vm.onInputChanged("Ich bin so wütend")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        val turns = vm.uiState.value.turns
        assertEquals(2, turns.size)
        assertTrue(turns[0].fromUser)
        assertEquals("Ich bin so wütend", turns[0].text)
        assertTrue(!turns[1].fromUser)
        assertEquals("Das klingt frustrierend.", turns[1].text)
        assertEquals("", vm.uiState.value.input)
    }

    @Test
    fun `missing api key surfaces an error and keeps the user turn`() = runTest(dispatcher) {
        coEvery { secureApiKeyStore.getApiKey() } returns null
        val vm = viewModel()

        vm.onInputChanged("Text")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(VentingErrorReason.NO_API_KEY, vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.turns.size)
    }

    @Test
    fun `a gemini error surfaces a network error`() = runTest(dispatcher) {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Error(GeminiErrorReason.NETWORK)
        val vm = viewModel()

        vm.onInputChanged("Text")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(VentingErrorReason.NETWORK, vm.uiState.value.errorMessage)
    }

    @Test
    fun `sending a second message includes the first exchange as history`() = runTest(dispatcher) {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns GeminiGenerateContentResult.Success("Antwort")
        val vm = viewModel()
        vm.onInputChanged("erste Nachricht")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onInputChanged("zweite Nachricht")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            geminiClient.generateContent(
                "key",
                any(),
                any(),
                any(),
                match { history -> history.size == 2 },
            )
        }
    }

    @Test
    fun `ending an empty session invokes the callback without writing a note`() = runTest(dispatcher) {
        val vm = viewModel()
        var ended = false

        vm.onEndSession { ended = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(ended)
        coVerify(exactly = 0) { mediatorNoteDao.insert(any()) }
    }

    @Test
    fun `ending a session writes a private note and resets the state`() = runTest(dispatcher) {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns GeminiGenerateContentResult.Success("Antwort")
        val vm = viewModel()
        vm.onInputChanged("Text")
        vm.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        var ended = false
        vm.onEndSession { ended = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(ended)
        assertTrue(vm.uiState.value.turns.isEmpty())
        coVerify { mediatorNoteDao.insert(match { it.role == MediatorAgentRole.VENTING_COMPANION }) }
    }
}

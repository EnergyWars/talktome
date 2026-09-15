package com.wafflehq.talktome.ui.compose

import com.wafflehq.talktome.data.db.NegotiationTurnEntity
import com.wafflehq.talktome.data.db.NegotiationTurnSender
import com.wafflehq.talktome.data.db.OutgoingMessageEntity
import com.wafflehq.talktome.data.db.OutgoingMessageStatus
import com.wafflehq.talktome.data.negotiation.OutgoingActionErrorReason
import com.wafflehq.talktome.data.negotiation.OutgoingActionResult
import com.wafflehq.talktome.data.negotiation.OutgoingMessageRepository
import com.wafflehq.talktome.data.prompts.FriendOpinion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComposeMessageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: OutgoingMessageRepository
    private lateinit var activeMessage: MutableStateFlow<OutgoingMessageEntity?>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        activeMessage = MutableStateFlow(null)
        every { repository.activeMessage } returns activeMessage
        every { repository.negotiationTurns(any()) } returns flowOf(emptyList())
        every { repository.parseFriendOpinions(any()) } returns emptyList()
        every { repository.isEscalating(any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ComposeMessageViewModel(repository)

    @Test
    fun `starting negotiation with a blank draft does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onDraftInputChanged("   ")
        vm.onStartNegotiation()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.startDraft(any()) }
    }

    @Test
    fun `starting negotiation creates a draft and clears the input on success`() = runTest(dispatcher) {
        coEvery { repository.startDraft("Mein Text") } returns 1L
        coEvery { repository.startNegotiation(1L) } returns OutgoingActionResult.Success
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onDraftInputChanged("Mein Text")
        vm.onStartNegotiation()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", vm.uiState.value.draftInput)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `a failed negotiation start surfaces the error`() = runTest(dispatcher) {
        coEvery { repository.startDraft(any()) } returns 1L
        coEvery { repository.startNegotiation(1L) } returns OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_API_KEY)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onDraftInputChanged("Mein Text")
        vm.onStartNegotiation()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OutgoingActionErrorReason.NO_API_KEY, vm.uiState.value.errorMessage)
    }

    @Test
    fun `the ui state reflects the active message, its opinions and turns`() = runTest(dispatcher) {
        val message = OutgoingMessageEntity(id = 5L, draftText = "Text", status = OutgoingMessageStatus.NEGOTIATING, createdAt = 1L, updatedAt = 1L)
        val turns = listOf(NegotiationTurnEntity(outgoingMessageId = 5L, sender = NegotiationTurnSender.MEDIATOR, text = "Feedback", createdAt = 1L))
        every { repository.negotiationTurns(5L) } returns flowOf(turns)
        every { repository.parseFriendOpinions(message) } returns listOf(FriendOpinion("Label", "Meinung"))
        val vm = viewModel()

        activeMessage.value = message
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(message, vm.uiState.value.activeMessage)
        assertEquals(turns, vm.uiState.value.turns)
        assertEquals(1, vm.uiState.value.friendOpinions.size)
    }

    @Test
    fun `escalation is surfaced from the repository`() = runTest(dispatcher) {
        val message = OutgoingMessageEntity(id = 5L, draftText = "Text", status = OutgoingMessageStatus.NEGOTIATING, createdAt = 1L, updatedAt = 1L, rejectionCount = 3)
        every { repository.isEscalating(message) } returns true
        val vm = viewModel()

        activeMessage.value = message
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isEscalating)
    }

    @Test
    fun `sending a reply clears the reply input on success`() = runTest(dispatcher) {
        val message = OutgoingMessageEntity(id = 5L, draftText = "Text", status = OutgoingMessageStatus.NEGOTIATING, createdAt = 1L, updatedAt = 1L)
        coEvery { repository.sendReply(5L, "Neue Version") } returns OutgoingActionResult.Success
        val vm = viewModel()
        activeMessage.value = message
        dispatcher.scheduler.advanceUntilIdle()

        vm.onReplyInputChanged("Neue Version")
        vm.onSendReply()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", vm.uiState.value.replyInput)
    }

    @Test
    fun `sending now without an active message does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onSendNow()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.send(any()) }
    }

    @Test
    fun `sending now shows a confirmation on success`() = runTest(dispatcher) {
        val message = OutgoingMessageEntity(id = 5L, draftText = "Text", status = OutgoingMessageStatus.NEGOTIATING, createdAt = 1L, updatedAt = 1L)
        coEvery { repository.send(5L) } returns OutgoingActionResult.Success
        val vm = viewModel()
        activeMessage.value = message
        dispatcher.scheduler.advanceUntilIdle()

        vm.onSendNow()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.sentConfirmation)

        vm.dismissSentConfirmation()
        assertFalse(vm.uiState.value.sentConfirmation)
    }

    @Test
    fun `sending now surfaces a failure without a partner`() = runTest(dispatcher) {
        val message = OutgoingMessageEntity(id = 5L, draftText = "Text", status = OutgoingMessageStatus.NEGOTIATING, createdAt = 1L, updatedAt = 1L)
        coEvery { repository.send(5L) } returns OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_PARTNER)
        val vm = viewModel()
        activeMessage.value = message
        dispatcher.scheduler.advanceUntilIdle()

        vm.onSendNow()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OutgoingActionErrorReason.NO_PARTNER, vm.uiState.value.errorMessage)
    }
}

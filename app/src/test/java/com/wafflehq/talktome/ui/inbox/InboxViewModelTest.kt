package com.wafflehq.talktome.ui.inbox

import com.wafflehq.talktome.data.db.InboxMessageEntity
import com.wafflehq.talktome.data.db.InboxMessageStatus
import com.wafflehq.talktome.data.inbox.InboxRepository
import com.wafflehq.talktome.data.sync.MailboxSyncRepository
import com.wafflehq.talktome.data.sync.MailboxSyncResult
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class InboxViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var inboxRepository: InboxRepository
    private lateinit var mailboxSyncRepository: MailboxSyncRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        inboxRepository = mockk()
        mailboxSyncRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `messages reflects the approved inbox messages`() = runTest(dispatcher) {
        val message = InboxMessageEntity(content = "Hallo", status = InboxMessageStatus.APPROVED, receivedAt = 1L)
        every { inboxRepository.approvedMessages } returns flowOf(listOf(message))
        val vm = InboxViewModel(inboxRepository, mailboxSyncRepository)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(message), vm.messages.value)
    }

    @Test
    fun `onRefresh triggers a sync and clears the syncing flag afterwards`() = runTest(dispatcher) {
        every { inboxRepository.approvedMessages } returns flowOf(emptyList())
        coEvery { mailboxSyncRepository.sync() } returns MailboxSyncResult.Synced(1)
        val vm = InboxViewModel(inboxRepository, mailboxSyncRepository)

        vm.onRefresh()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { mailboxSyncRepository.sync() }
        assertFalse(vm.isSyncing.value)
    }

    @Test
    fun `onRefresh is a no-op while already syncing`() = runTest(dispatcher) {
        every { inboxRepository.approvedMessages } returns flowOf(emptyList())
        coEvery { mailboxSyncRepository.sync() } coAnswers {
            kotlinx.coroutines.delay(1_000)
            MailboxSyncResult.Synced(0)
        }
        val vm = InboxViewModel(inboxRepository, mailboxSyncRepository)

        vm.onRefresh()
        vm.onRefresh()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mailboxSyncRepository.sync() }
    }
}

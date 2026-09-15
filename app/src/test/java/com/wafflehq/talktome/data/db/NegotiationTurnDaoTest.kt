package com.wafflehq.talktome.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NegotiationTurnDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: NegotiationTurnDao
    private lateinit var outgoingMessageDao: OutgoingMessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.negotiationTurnDao()
        outgoingMessageDao = database.outgoingMessageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createOutgoingMessage(): Long =
        outgoingMessageDao.upsert(
            OutgoingMessageEntity(draftText = "Text", status = OutgoingMessageStatus.NEGOTIATING, createdAt = 1L, updatedAt = 1L),
        )

    @Test
    fun `getForMessage returns empty list when no turns exist`() = runTest {
        val messageId = createOutgoingMessage()

        assertTrue(dao.getForMessage(messageId).isEmpty())
    }

    @Test
    fun `insert then observeForMessage returns turns ordered by creation time`() = runTest {
        val messageId = createOutgoingMessage()

        dao.insert(NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.USER, text = "Hallo", createdAt = 2L))
        dao.insert(NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.MEDIATOR, text = "Guten Tag", createdAt = 1L))

        val turns = dao.observeForMessage(messageId).first()

        assertEquals(2, turns.size)
        assertEquals("Guten Tag", turns.first().text)
        assertEquals(NegotiationTurnSender.MEDIATOR, turns.first().sender)
    }

    @Test
    fun `turns for a different message are not returned`() = runTest {
        val messageId = createOutgoingMessage()
        val otherMessageId = createOutgoingMessage()
        dao.insert(NegotiationTurnEntity(outgoingMessageId = otherMessageId, sender = NegotiationTurnSender.USER, text = "Anderer Text", createdAt = 1L))

        assertTrue(dao.getForMessage(messageId).isEmpty())
    }

    @Test
    fun `deleteForMessage removes only turns of that message`() = runTest {
        val messageId = createOutgoingMessage()
        val otherMessageId = createOutgoingMessage()
        dao.insert(NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.USER, text = "a", createdAt = 1L))
        dao.insert(NegotiationTurnEntity(outgoingMessageId = otherMessageId, sender = NegotiationTurnSender.USER, text = "b", createdAt = 1L))

        dao.deleteForMessage(messageId)

        assertTrue(dao.getForMessage(messageId).isEmpty())
        assertEquals(1, dao.getForMessage(otherMessageId).size)
    }
}

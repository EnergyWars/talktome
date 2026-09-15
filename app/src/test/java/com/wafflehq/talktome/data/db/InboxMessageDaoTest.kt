package com.wafflehq.talktome.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InboxMessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: InboxMessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.inboxMessageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeByStatus is empty when nothing matches`() = runTest {
        assertTrue(dao.observeByStatus(InboxMessageStatus.APPROVED).first().isEmpty())
    }

    @Test
    fun `rejected messages are never returned as approved`() = runTest {
        dao.upsert(
            InboxMessageEntity(
                content = "abgelehnter Inhalt",
                status = InboxMessageStatus.REJECTED,
                receivedAt = 1L,
            ),
        )

        val approved = dao.observeByStatus(InboxMessageStatus.APPROVED).first()

        assertTrue(approved.isEmpty())
    }

    @Test
    fun `upsert then observeByStatus finds approved message with feedback`() = runTest {
        dao.upsert(
            InboxMessageEntity(
                content = "Ich vermisse dich",
                status = InboxMessageStatus.APPROVED,
                mediatorFeedback = "Er fühlt sich einsam.",
                receivedAt = 5L,
            ),
        )

        val approved = dao.observeByStatus(InboxMessageStatus.APPROVED).first()

        assertEquals(1, approved.size)
        assertEquals("Er fühlt sich einsam.", approved.first().mediatorFeedback)
    }

    @Test
    fun `pending message without feedback stores null feedback`() = runTest {
        val id = dao.upsert(
            InboxMessageEntity(content = "Text", status = InboxMessageStatus.PENDING_REVIEW, receivedAt = 1L),
        )

        val stored = dao.observeAll().first().first { it.id == id }

        assertNull(stored.mediatorFeedback)
    }

    @Test
    fun `delete removes the message`() = runTest {
        val id = dao.upsert(
            InboxMessageEntity(content = "Text", status = InboxMessageStatus.PENDING_REVIEW, receivedAt = 1L),
        )
        val stored = dao.observeAll().first().first { it.id == id }

        dao.delete(stored)

        assertTrue(dao.observeAll().first().isEmpty())
    }
}

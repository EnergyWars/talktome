package com.wafflehq.talktome.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutgoingMessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: OutgoingMessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.outgoingMessageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun `upsert then getById returns stored message`() = runTest {
        val message = OutgoingMessageEntity(
            draftText = "Ich wünsche mir mehr Zeit zu zweit",
            status = OutgoingMessageStatus.DRAFT,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val id = dao.upsert(message)
        val loaded = dao.getById(id)

        assertEquals(message.draftText, loaded?.draftText)
        assertEquals(OutgoingMessageStatus.DRAFT, loaded?.status)
    }

    @Test
    fun `upsert with same id overwrites status`() = runTest {
        val id = dao.upsert(
            OutgoingMessageEntity(draftText = "Text", status = OutgoingMessageStatus.DRAFT, createdAt = 1L, updatedAt = 1L),
        )

        dao.upsert(
            OutgoingMessageEntity(id = id, draftText = "Text", status = OutgoingMessageStatus.SENT, createdAt = 1L, updatedAt = 2L),
        )

        assertEquals(OutgoingMessageStatus.SENT, dao.getById(id)?.status)
    }

    @Test
    fun `observeAll orders by most recently updated first`() = runTest {
        dao.upsert(OutgoingMessageEntity(draftText = "a", status = OutgoingMessageStatus.DRAFT, createdAt = 1L, updatedAt = 1L))
        dao.upsert(OutgoingMessageEntity(draftText = "b", status = OutgoingMessageStatus.DRAFT, createdAt = 2L, updatedAt = 5L))

        val all = dao.observeAll().first()

        assertEquals("b", all.first().draftText)
    }

    @Test
    fun `delete removes the message`() = runTest {
        val message = OutgoingMessageEntity(draftText = "a", status = OutgoingMessageStatus.DRAFT, createdAt = 1L, updatedAt = 1L)
        val id = dao.upsert(message)
        val stored = dao.getById(id)!!

        dao.delete(stored)

        assertNull(dao.getById(id))
    }
}

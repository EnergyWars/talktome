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
class MediatorNoteDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MediatorNoteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.mediatorNoteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeAll is empty when no notes exist`() = runTest {
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `insert then observeByRole returns only notes of that role`() = runTest {
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.NEUTRAL_MEDIATOR, noteText = "h notiz", createdAt = 1L))
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.RECEIVER_GATEKEEPER, noteText = "i notiz", createdAt = 2L))

        val hNotes = dao.observeByRole(MediatorAgentRole.NEUTRAL_MEDIATOR).first()

        assertEquals(1, hNotes.size)
        assertEquals("h notiz", hNotes.first().noteText)
    }

    @Test
    fun `observeByRole orders notes newest first`() = runTest {
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.SENDER_COACH, noteText = "alt", createdAt = 1L))
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.SENDER_COACH, noteText = "neu", createdAt = 2L))

        val notes = dao.observeByRole(MediatorAgentRole.SENDER_COACH).first()

        assertEquals(listOf("neu", "alt"), notes.map { it.noteText })
    }

    @Test
    fun `deleteByRole only removes notes of that role`() = runTest {
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.NEUTRAL_MEDIATOR, noteText = "h", createdAt = 1L))
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.VENTING_COMPANION, noteText = "vent", createdAt = 2L))

        dao.deleteByRole(MediatorAgentRole.NEUTRAL_MEDIATOR)

        assertTrue(dao.observeByRole(MediatorAgentRole.NEUTRAL_MEDIATOR).first().isEmpty())
        assertEquals(1, dao.observeByRole(MediatorAgentRole.VENTING_COMPANION).first().size)
    }

    @Test
    fun `deleteAll removes notes of every role`() = runTest {
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.NEUTRAL_MEDIATOR, noteText = "h", createdAt = 1L))
        dao.insert(MediatorNoteEntity(role = MediatorAgentRole.VENTING_COMPANION, noteText = "vent", createdAt = 2L))

        dao.deleteAll()

        assertTrue(dao.observeAll().first().isEmpty())
    }
}

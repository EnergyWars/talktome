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
class PartnerDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PartnerDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.partnerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `get returns null when no partner exists`() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun `upsert then get returns stored partner`() = runTest {
        val partner = PartnerEntity(displayName = "Alex", publicKey = null, pairedAt = null)

        val id = dao.upsert(partner)
        val loaded = dao.get()

        assertEquals("Alex", loaded?.displayName)
        assertEquals(id, loaded?.id)
    }

    @Test
    fun `unpaired partner has null publicKey and pairedAt`() = runTest {
        dao.upsert(PartnerEntity(displayName = "Alex"))

        val loaded = dao.get()

        assertNull(loaded?.publicKey)
        assertNull(loaded?.pairedAt)
    }

    @Test
    fun `deleteAll removes stored partner`() = runTest {
        dao.upsert(PartnerEntity(displayName = "Alex"))

        dao.deleteAll()

        assertNull(dao.get())
    }

    @Test
    fun `observe emits current partner state`() = runTest {
        dao.upsert(PartnerEntity(displayName = "Sam", pairedAt = 42L))

        val emitted = dao.observe().first()

        assertEquals("Sam", emitted?.displayName)
        assertEquals(42L, emitted?.pairedAt)
    }
}

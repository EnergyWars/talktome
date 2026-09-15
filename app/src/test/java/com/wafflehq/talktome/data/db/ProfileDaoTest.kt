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
class ProfileDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ProfileDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.profileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `get returns null when no profile stored`() = runTest {
        assertNull(dao.get(ProfileEntity.SINGLETON_ID))
    }

    @Test
    fun `upsert then get returns stored profile`() = runTest {
        val profile = ProfileEntity(
            filterText = "keine Vorwürfe",
            selfDescription = "ruhig, direkt",
            partnerDescription = "sensibel, braucht Zeit",
            updatedAt = 123L,
        )

        dao.upsert(profile)
        val loaded = dao.get(ProfileEntity.SINGLETON_ID)

        assertEquals(profile, loaded)
    }

    @Test
    fun `upsert replaces existing singleton row instead of inserting a second one`() = runTest {
        dao.upsert(ProfileEntity(filterText = "erste Version"))
        dao.upsert(ProfileEntity(filterText = "zweite Version"))

        val loaded = dao.get(ProfileEntity.SINGLETON_ID)

        assertEquals("zweite Version", loaded?.filterText)
    }

    @Test
    fun `observe emits updated profile after upsert`() = runTest {
        dao.upsert(ProfileEntity(selfDescription = "erste Version"))

        val emitted = dao.observe(ProfileEntity.SINGLETON_ID).first()

        assertEquals("erste Version", emitted?.selfDescription)
    }

    @Test
    fun `empty strings are stored and read back correctly`() = runTest {
        dao.upsert(ProfileEntity())

        val loaded = dao.get(ProfileEntity.SINGLETON_ID)

        assertEquals("", loaded?.filterText)
        assertEquals("", loaded?.selfDescription)
        assertEquals("", loaded?.partnerDescription)
    }
}

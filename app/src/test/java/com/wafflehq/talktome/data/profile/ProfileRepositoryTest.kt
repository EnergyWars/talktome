package com.wafflehq.talktome.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wafflehq.talktome.data.db.AppDatabase
import com.wafflehq.talktome.data.db.ProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ProfileRepository
    private var fakeNow = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProfileRepository(database.profileDao(), clock = { fakeNow })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `profile is empty before anything is saved`() = runTest {
        val profile = repository.profile.first()

        assertEquals(Profile.Empty, profile)
    }

    @Test
    fun `setSelfDescription persists and is reflected in profile flow`() = runTest {
        repository.setSelfDescription("ruhig und direkt")

        val profile = repository.profile.first()

        assertEquals("ruhig und direkt", profile.selfDescription)
    }

    @Test
    fun `updates to different fields do not overwrite each other`() = runTest {
        repository.setSelfDescription("über mich")
        repository.setPartnerDescription("über ihn")
        repository.setFilterText("kein Schreien")

        val profile = repository.profile.first()

        assertEquals("über mich", profile.selfDescription)
        assertEquals("über ihn", profile.partnerDescription)
        assertEquals("kein Schreien", profile.filterText)
    }

    @Test
    fun `updatedAt timestamp advances on every write`() = runTest {
        fakeNow = 1_000L
        repository.setFilterText("erste Version")
        val afterFirst = database.profileDao().get(ProfileEntity.SINGLETON_ID)?.updatedAt

        fakeNow = 2_000L
        repository.setFilterText("zweite Version")
        val afterSecond = database.profileDao().get(ProfileEntity.SINGLETON_ID)?.updatedAt

        assertEquals(1_000L, afterFirst)
        assertEquals(2_000L, afterSecond)
    }

    @Test
    fun `very long description text round-trips without truncation`() = runTest {
        val longText = "x".repeat(20_000)

        repository.setSelfDescription(longText)

        assertEquals(longText, repository.profile.first().selfDescription)
    }

    @Test
    fun `setting empty string clears a previously stored value`() = runTest {
        repository.setFilterText("etwas")
        repository.setFilterText("")

        assertEquals("", repository.profile.first().filterText)
    }
}

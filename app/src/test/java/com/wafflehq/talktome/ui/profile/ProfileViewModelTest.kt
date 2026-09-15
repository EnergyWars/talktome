package com.wafflehq.talktome.ui.profile

import com.wafflehq.talktome.data.profile.Profile
import com.wafflehq.talktome.data.profile.ProfileRepository
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
import org.junit.Before
import org.junit.Test

class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads the stored profile`() = runTest(dispatcher) {
        val stored = Profile(filterText = "f", selfDescription = "s", partnerDescription = "p")
        every { repository.profile } returns flowOf(stored)

        val viewModel = ProfileViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(stored, viewModel.profile.value)
    }

    @Test
    fun `onFilterTextChanged updates local state immediately`() = runTest(dispatcher) {
        every { repository.profile } returns flowOf(Profile.Empty)
        val viewModel = ProfileViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterTextChanged("neuer Filter")

        assertEquals("neuer Filter", viewModel.profile.value.filterText)
    }

    @Test
    fun `onSelfDescriptionChanged persists to the repository`() = runTest(dispatcher) {
        every { repository.profile } returns flowOf(Profile.Empty)
        val viewModel = ProfileViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSelfDescriptionChanged("neue Beschreibung")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setSelfDescription("neue Beschreibung") }
    }

    @Test
    fun `onPartnerDescriptionChanged does not affect other fields`() = runTest(dispatcher) {
        val stored = Profile(filterText = "f", selfDescription = "s", partnerDescription = "p")
        every { repository.profile } returns flowOf(stored)
        val viewModel = ProfileViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPartnerDescriptionChanged("neuer Partnertext")

        assertEquals("f", viewModel.profile.value.filterText)
        assertEquals("s", viewModel.profile.value.selfDescription)
        assertEquals("neuer Partnertext", viewModel.profile.value.partnerDescription)
    }
}

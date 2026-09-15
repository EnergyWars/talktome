package com.wafflehq.talktome.ui.notes

import com.wafflehq.talktome.data.db.MediatorNoteDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var mediatorNoteDao: MediatorNoteDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mediatorNoteDao = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onResetNotes deletes all mediator notes`() = runTest(dispatcher) {
        val vm = NotesViewModel(mediatorNoteDao)

        vm.onResetNotes()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { mediatorNoteDao.deleteAll() }
    }
}

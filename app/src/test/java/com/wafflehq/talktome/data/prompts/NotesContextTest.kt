package com.wafflehq.talktome.data.prompts

import com.wafflehq.talktome.data.db.MediatorAgentRole
import com.wafflehq.talktome.data.db.MediatorNoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesContextTest {

    @Test
    fun `returns the base instruction unchanged when there are no notes`() {
        val result = NotesContext.append("base instruction", emptyList())

        assertEquals("base instruction", result)
    }

    @Test
    fun `appends every note when notes exist`() {
        val notes = listOf(
            MediatorNoteEntity(role = MediatorAgentRole.NEUTRAL_MEDIATOR, noteText = "Erste Notiz", createdAt = 1L),
            MediatorNoteEntity(role = MediatorAgentRole.NEUTRAL_MEDIATOR, noteText = "Zweite Notiz", createdAt = 2L),
        )

        val result = NotesContext.append("base instruction", notes)

        assertTrue(result.contains("base instruction"))
        assertTrue(result.contains("Erste Notiz"))
        assertTrue(result.contains("Zweite Notiz"))
    }
}

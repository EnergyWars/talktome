package com.wafflehq.talktome.data.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatekeeperVerdictTest {

    @Test
    fun `parses an approval and keeps the remaining feedback`() {
        val verdict = GatekeeperVerdict.parse("ENTSCHEIDUNG: ZULASSEN\nDein Partner möchte dir sagen, dass er dich vermisst.")

        assertTrue(verdict.approved)
        assertEquals("Dein Partner möchte dir sagen, dass er dich vermisst.", verdict.feedback)
    }

    @Test
    fun `parses a rejection and keeps the remaining feedback`() {
        val verdict = GatekeeperVerdict.parse("ENTSCHEIDUNG: ABLEHNEN\nDie Nachricht passt gerade nicht.")

        assertFalse(verdict.approved)
        assertEquals("Die Nachricht passt gerade nicht.", verdict.feedback)
    }

    @Test
    fun `is case insensitive on the marker line`() {
        val verdict = GatekeeperVerdict.parse("entscheidung: zulassen\nText")

        assertTrue(verdict.approved)
    }

    @Test
    fun `falls back to a safe rejection when the format is not followed`() {
        val verdict = GatekeeperVerdict.parse("Ich lasse die Nachricht durch, klingt gut.")

        assertFalse(verdict.approved)
        assertEquals("Ich lasse die Nachricht durch, klingt gut.", verdict.feedback)
    }

    @Test
    fun `handles an empty response as a safe rejection`() {
        val verdict = GatekeeperVerdict.parse("")

        assertFalse(verdict.approved)
        assertEquals("", verdict.feedback)
    }
}

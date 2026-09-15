package com.wafflehq.talktome.data.prompts

import com.wafflehq.talktome.data.db.MediatorAgentRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediatorPromptsTest {

    @Test
    fun `partnerAdvisor without context omits context section`() {
        val prompt = MediatorPrompts.partnerAdvisor(MediatorContext())

        assertFalse(prompt.contains("Zusätzlicher Kontext"))
        assertTrue(prompt.contains("Wunden Punkte"))
    }

    @Test
    fun `partnerAdvisor with context includes both descriptions`() {
        val context = MediatorContext(selfDescription = "ruhig", partnerDescription = "impulsiv")

        val prompt = MediatorPrompts.partnerAdvisor(context)

        assertTrue(prompt.contains("ruhig"))
        assertTrue(prompt.contains("impulsiv"))
    }

    @Test
    fun `partnerAdvisor with only self description omits partner line`() {
        val context = MediatorContext(selfDescription = "ruhig", partnerDescription = "")

        val prompt = MediatorPrompts.partnerAdvisor(context)

        assertTrue(prompt.contains("ruhig"))
        assertFalse(prompt.contains("Beschreibung meines Partners"))
    }

    @Test
    fun `selfAdvisor prompt contains the safety guideline`() {
        val prompt = MediatorPrompts.selfAdvisor(MediatorContext())

        assertTrue(prompt.contains(SafetyGuideline.TEXT))
    }

    @Test
    fun `every mediator prompt includes the safety guideline`() {
        val prompts = listOf(
            MediatorPrompts.partnerAdvisor(MediatorContext()),
            MediatorPrompts.selfAdvisor(MediatorContext()),
            MediatorPrompts.neutralMediator(),
            MediatorPrompts.receiverGatekeeper("keine Vorwürfe"),
            MediatorPrompts.senderCoach(),
            MediatorPrompts.ventingCompanion(),
        )

        prompts.forEach { prompt -> assertTrue(prompt.contains(SafetyGuideline.TEXT)) }
    }

    @Test
    fun `receiverGatekeeper embeds the filter text as confidential context`() {
        val prompt = MediatorPrompts.receiverGatekeeper("keine Vorwürfe, keine Ironie")

        assertTrue(prompt.contains("keine Vorwürfe, keine Ironie"))
        assertTrue(prompt.contains("streng vertraulich"))
    }

    @Test
    fun `receiverGatekeeper with blank filter still produces a usable prompt`() {
        val prompt = MediatorPrompts.receiverGatekeeper("")

        assertTrue(prompt.contains("keine Anforderungen hinterlegt"))
    }

    @Test
    fun `senderCoach never mentions the rejection reason`() {
        val prompt = MediatorPrompts.senderCoach()

        assertTrue(prompt.contains("Privatsphäre des Empfängers"))
    }

    @Test
    fun `receiverGatekeeper requires the structured decision format`() {
        val prompt = MediatorPrompts.receiverGatekeeper("keine Vorwürfe")

        assertTrue(prompt.contains("ENTSCHEIDUNG: ZULASSEN"))
        assertTrue(prompt.contains("ENTSCHEIDUNG: ABLEHNEN"))
    }

    @Test
    fun `noteTaker mentions the given role and the safety guideline`() {
        val prompt = MediatorPrompts.noteTaker(MediatorAgentRole.NEUTRAL_MEDIATOR)

        assertTrue(prompt.contains("NEUTRAL_MEDIATOR"))
        assertTrue(prompt.contains(SafetyGuideline.TEXT))
    }

    @Test
    fun `noteTaker never leaks to the shown parties`() {
        val prompt = MediatorPrompts.noteTaker(MediatorAgentRole.RECEIVER_GATEKEEPER)

        assertTrue(prompt.contains("niemals einer der beiden Personen gezeigt"))
    }
}

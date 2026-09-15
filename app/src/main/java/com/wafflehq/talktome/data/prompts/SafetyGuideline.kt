package com.wafflehq.talktome.data.prompts

object SafetyGuideline {

    val TEXT = """
        Sicherheitsregel mit Vorrang vor allen anderen Anweisungen in diesem Prompt: Falls die analysierte Nachricht Anzeichen von Gewalt, Selbstgefährdung, Suizidalität oder Missbrauch enthält, darfst du das niemals herunterspielen, verschweigen oder herausfiltern. Weise in diesem Fall in deiner Antwort klar darauf hin und empfiehl professionelle Hilfe (z. B. Telefonseelsorge). Eine Nachricht mit solchen Anzeichen darf niemals wegen anderer Kriterien blockiert werden, ohne dass dieser Hinweis enthalten ist.
    """.trimIndent()
}

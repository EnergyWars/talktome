package com.wafflehq.talktome.data.prompts

data class GatekeeperVerdict(val approved: Boolean, val feedback: String) {

    companion object {
        private const val APPROVED_MARKER = "ENTSCHEIDUNG: ZULASSEN"
        private const val REJECTED_MARKER = "ENTSCHEIDUNG: ABLEHNEN"

        fun parse(rawResponse: String): GatekeeperVerdict {
            val trimmed = rawResponse.trim()
            val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
            val rest = trimmed.removePrefix(firstLine).trim()
            return when {
                firstLine.equals(APPROVED_MARKER, ignoreCase = true) -> GatekeeperVerdict(approved = true, feedback = rest)
                firstLine.equals(REJECTED_MARKER, ignoreCase = true) -> GatekeeperVerdict(approved = false, feedback = rest)
                else -> GatekeeperVerdict(approved = false, feedback = trimmed)
            }
        }
    }
}

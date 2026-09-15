package com.wafflehq.talktome.data.gemini

enum class GeminiModel(val wireId: String) {
    PRO("gemini-3.1-pro-preview"),
    FLASH("gemini-3.8-flash"),
    FLASH_LITE("gemini-3.5-flash-lite");

    companion object {
        val DEFAULT = FLASH

        fun fromWireId(wireId: String?): GeminiModel = entries.firstOrNull { it.wireId == wireId } ?: DEFAULT
    }
}

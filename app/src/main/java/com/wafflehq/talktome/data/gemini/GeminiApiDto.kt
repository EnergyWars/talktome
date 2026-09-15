package com.wafflehq.talktome.data.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiModelInfo(val name: String)

@Serializable
data class GeminiModelsListResponse(
    val models: List<GeminiModelInfo> = emptyList(),
)

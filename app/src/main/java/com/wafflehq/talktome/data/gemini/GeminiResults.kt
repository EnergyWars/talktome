package com.wafflehq.talktome.data.gemini

enum class GeminiErrorReason {
    INVALID_API_KEY,
    RATE_LIMITED,
    NETWORK,
    INPUT_TOO_LONG,
    EMPTY_RESPONSE,
    BLOCKED_BY_SAFETY_FILTER,
    SERVICE_UNAVAILABLE,
    UNKNOWN,
}

sealed interface GeminiConnectionResult {
    data object Success : GeminiConnectionResult
    data object InvalidApiKey : GeminiConnectionResult
    data object RateLimited : GeminiConnectionResult
    data object NetworkError : GeminiConnectionResult
    data object ServiceUnavailable : GeminiConnectionResult
    data class UnknownError(val statusCode: Int) : GeminiConnectionResult
}

sealed interface GeminiGenerateContentResult {
    data class Success(val text: String) : GeminiGenerateContentResult
    data class Error(val reason: GeminiErrorReason) : GeminiGenerateContentResult
}

package com.wafflehq.talktome.ui.venting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.db.MediatorAgentRole
import com.wafflehq.talktome.data.db.MediatorNoteDao
import com.wafflehq.talktome.data.db.MediatorNoteEntity
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiGenerateContentResult
import com.wafflehq.talktome.data.gemini.GeminiRole
import com.wafflehq.talktome.data.gemini.GeminiTurn
import com.wafflehq.talktome.data.prompts.MediatorPrompts
import com.wafflehq.talktome.data.prompts.NotesContext
import com.wafflehq.talktome.data.prompts.PromptBuilder
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VentingTurn(val fromUser: Boolean, val text: String)

enum class VentingErrorReason {
    NO_API_KEY,
    NETWORK,
}

data class VentingUiState(
    val turns: List<VentingTurn> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val errorMessage: VentingErrorReason? = null,
)

@HiltViewModel
class VentingViewModel @Inject constructor(
    private val secureApiKeyStore: SecureApiKeyStore,
    private val geminiClient: GeminiClient,
    private val mediatorNoteDao: MediatorNoteDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VentingUiState())
    val uiState: StateFlow<VentingUiState> = _uiState.asStateFlow()

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = null) }
    }

    fun onSend() {
        val text = _uiState.value.input.trim()
        if (text.isBlank() || _uiState.value.isSending) return
        _uiState.update { it.copy(turns = it.turns + VentingTurn(fromUser = true, text = text), input = "", isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val apiKey = secureApiKeyStore.getApiKey()
            if (apiKey == null) {
                _uiState.update { it.copy(isSending = false, errorMessage = VentingErrorReason.NO_API_KEY) }
                return@launch
            }
            val notes = mediatorNoteDao.observeByRole(MediatorAgentRole.VENTING_COMPANION).first()
            val history = _uiState.value.turns.dropLast(1).map {
                GeminiTurn(if (it.fromUser) GeminiRole.USER else GeminiRole.MODEL, it.text)
            }
            val systemInstruction = NotesContext.append(MediatorPrompts.ventingCompanion(), notes)
            when (val result = geminiClient.generateContent(apiKey, systemInstruction, PromptBuilder.wrapUserMessage(text), history = history)) {
                is GeminiGenerateContentResult.Success ->
                    _uiState.update { it.copy(turns = it.turns + VentingTurn(fromUser = false, text = result.text), isSending = false) }
                is GeminiGenerateContentResult.Error ->
                    _uiState.update { it.copy(isSending = false, errorMessage = VentingErrorReason.NETWORK) }
            }
        }
    }

    fun onEndSession(onEnded: () -> Unit) {
        val turns = _uiState.value.turns
        if (turns.isEmpty()) {
            onEnded()
            return
        }
        viewModelScope.launch {
            val apiKey = secureApiKeyStore.getApiKey()
            if (apiKey != null) {
                val transcript = turns.joinToString("\n") { turn -> "${if (turn.fromUser) "Ich" else "Begleiter"}: ${turn.text}" }
                val result = geminiClient.generateContent(apiKey, MediatorPrompts.noteTaker(MediatorAgentRole.VENTING_COMPANION), PromptBuilder.wrapUserMessage(transcript))
                if (result is GeminiGenerateContentResult.Success) {
                    mediatorNoteDao.insert(MediatorNoteEntity(role = MediatorAgentRole.VENTING_COMPANION, noteText = result.text, createdAt = System.currentTimeMillis()))
                }
            }
            _uiState.update { VentingUiState() }
            onEnded()
        }
    }
}

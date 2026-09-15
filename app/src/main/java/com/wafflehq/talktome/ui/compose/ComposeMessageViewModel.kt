package com.wafflehq.talktome.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.db.NegotiationTurnEntity
import com.wafflehq.talktome.data.db.OutgoingMessageEntity
import com.wafflehq.talktome.data.negotiation.OutgoingActionErrorReason
import com.wafflehq.talktome.data.negotiation.OutgoingActionResult
import com.wafflehq.talktome.data.negotiation.OutgoingMessageRepository
import com.wafflehq.talktome.data.prompts.FriendOpinion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComposeUiState(
    val draftInput: String = "",
    val activeMessage: OutgoingMessageEntity? = null,
    val friendOpinions: List<FriendOpinion> = emptyList(),
    val turns: List<NegotiationTurnEntity> = emptyList(),
    val replyInput: String = "",
    val isBusy: Boolean = false,
    val isEscalating: Boolean = false,
    val errorMessage: OutgoingActionErrorReason? = null,
    val sentConfirmation: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComposeMessageViewModel @Inject constructor(
    private val repository: OutgoingMessageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    init {
        val turnsFlow = repository.activeMessage.flatMapLatest { message ->
            if (message == null) flowOf(emptyList()) else repository.negotiationTurns(message.id)
        }
        viewModelScope.launch {
            combine(repository.activeMessage, turnsFlow) { message, turns -> message to turns }
                .collect { (message, turns) ->
                    _uiState.update {
                        it.copy(
                            activeMessage = message,
                            friendOpinions = message?.let(repository::parseFriendOpinions) ?: emptyList(),
                            turns = turns,
                            isEscalating = message?.let(repository::isEscalating) ?: false,
                        )
                    }
                }
        }
    }

    fun onDraftInputChanged(value: String) {
        _uiState.update { it.copy(draftInput = value) }
    }

    fun onStartNegotiation() {
        val text = _uiState.value.draftInput.trim()
        if (text.isBlank() || _uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val id = repository.startDraft(text)
            applyResult(repository.startNegotiation(id)) { it.copy(draftInput = "") }
        }
    }

    fun onReplyInputChanged(value: String) {
        _uiState.update { it.copy(replyInput = value) }
    }

    fun onSendReply() {
        val messageId = _uiState.value.activeMessage?.id ?: return
        val text = _uiState.value.replyInput.trim()
        if (text.isBlank() || _uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            applyResult(repository.sendReply(messageId, text)) { it.copy(replyInput = "") }
        }
    }

    fun onSendNow() {
        val messageId = _uiState.value.activeMessage?.id ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            applyResult(repository.send(messageId)) { it.copy(sentConfirmation = true) }
        }
    }

    fun dismissSentConfirmation() {
        _uiState.update { it.copy(sentConfirmation = false) }
    }

    private fun applyResult(result: OutgoingActionResult, onSuccess: (ComposeUiState) -> ComposeUiState) {
        when (result) {
            OutgoingActionResult.Success -> _uiState.update { onSuccess(it.copy(isBusy = false)) }
            is OutgoingActionResult.Failure -> _uiState.update { it.copy(isBusy = false, errorMessage = result.reason) }
        }
    }
}

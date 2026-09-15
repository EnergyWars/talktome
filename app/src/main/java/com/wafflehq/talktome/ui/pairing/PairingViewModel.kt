package com.wafflehq.talktome.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.db.PartnerEntity
import com.wafflehq.talktome.data.pairing.PairingErrorReason
import com.wafflehq.talktome.data.pairing.PairingRepository
import com.wafflehq.talktome.data.pairing.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val displayName: String = "",
    val serverBaseUrl: String = DEFAULT_SERVER_BASE_URL,
    val enteredCode: String = "",
    val inviteCode: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: PairingErrorReason? = null,
) {
    companion object {
        const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:8080"
    }
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    val partner: StateFlow<PartnerEntity?> = pairingRepository.partner.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onDisplayNameChanged(value: String) {
        _uiState.update { it.copy(displayName = value) }
    }

    fun onServerBaseUrlChanged(value: String) {
        _uiState.update { it.copy(serverBaseUrl = value) }
    }

    fun onEnteredCodeChanged(value: String) {
        _uiState.update { it.copy(enteredCode = value, errorMessage = null) }
    }

    fun onCreateInvite() {
        val state = _uiState.value
        if (state.displayName.isBlank() || state.serverBaseUrl.isBlank() || state.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = pairingRepository.createInvite(state.serverBaseUrl.trim(), state.displayName.trim())) {
                is PairingResult.Success -> _uiState.update { it.copy(isBusy = false, inviteCode = result.value) }
                is PairingResult.Failure -> _uiState.update { it.copy(isBusy = false, errorMessage = result.reason) }
            }
        }
    }

    fun onAcceptInvite() {
        val state = _uiState.value
        if (state.enteredCode.isBlank() || state.displayName.isBlank() || state.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = pairingRepository.acceptInvite(state.enteredCode.trim(), state.displayName.trim())) {
                is PairingResult.Success -> _uiState.update { it.copy(isBusy = false, enteredCode = "") }
                is PairingResult.Failure -> _uiState.update { it.copy(isBusy = false, errorMessage = result.reason) }
            }
        }
    }

    fun onUnpair() {
        viewModelScope.launch { pairingRepository.unpair() }
    }
}

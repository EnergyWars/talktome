package com.wafflehq.talktome.ui.gemini

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiConnectionResult
import com.wafflehq.talktome.data.gemini.GeminiModel
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import com.wafflehq.talktome.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GeminiConnectionUiState {
    data object Idle : GeminiConnectionUiState
    data object Testing : GeminiConnectionUiState
    data object Success : GeminiConnectionUiState
    data class Failed(val result: GeminiConnectionResult) : GeminiConnectionUiState
}

data class GeminiSettingsUiState(
    val apiKeyInput: String = "",
    val isKeyVisible: Boolean = false,
    val connectionState: GeminiConnectionUiState = GeminiConnectionUiState.Idle,
)

@HiltViewModel
class GeminiSettingsViewModel @Inject constructor(
    private val secureApiKeyStore: SecureApiKeyStore,
    private val geminiClient: GeminiClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val hasApiKey: StateFlow<Boolean> = secureApiKeyStore.hasApiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val selectedModel: StateFlow<GeminiModel> = settingsRepository.geminiModel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GeminiModel.DEFAULT,
    )

    private val _uiState = MutableStateFlow(GeminiSettingsUiState())
    val uiState: StateFlow<GeminiSettingsUiState> = _uiState.asStateFlow()

    fun onApiKeyInputChanged(value: String) {
        _uiState.update { it.copy(apiKeyInput = value, connectionState = GeminiConnectionUiState.Idle) }
    }

    fun onToggleVisibility() {
        _uiState.update { it.copy(isKeyVisible = !it.isKeyVisible) }
    }

    fun onSaveAndTest() {
        val key = _uiState.value.apiKeyInput.trim()
        if (key.isBlank()) {
            _uiState.update {
                it.copy(connectionState = GeminiConnectionUiState.Failed(GeminiConnectionResult.InvalidApiKey))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = GeminiConnectionUiState.Testing) }
            when (val result = geminiClient.verifyApiKey(key)) {
                GeminiConnectionResult.Success -> {
                    secureApiKeyStore.setApiKey(key)
                    _uiState.update {
                        it.copy(apiKeyInput = "", connectionState = GeminiConnectionUiState.Success)
                    }
                }
                else -> _uiState.update { it.copy(connectionState = GeminiConnectionUiState.Failed(result)) }
            }
        }
    }

    fun onModelSelected(model: GeminiModel) {
        viewModelScope.launch {
            settingsRepository.setGeminiModel(model)
        }
    }

    fun onClearApiKey() {
        viewModelScope.launch {
            secureApiKeyStore.clearApiKey()
            _uiState.update { it.copy(connectionState = GeminiConnectionUiState.Idle) }
        }
    }
}

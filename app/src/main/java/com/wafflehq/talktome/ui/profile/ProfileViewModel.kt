package com.wafflehq.talktome.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.profile.Profile
import com.wafflehq.talktome.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _profile = MutableStateFlow(Profile.Empty)
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    init {
        viewModelScope.launch {
            _profile.value = profileRepository.profile.first()
        }
    }

    fun onFilterTextChanged(text: String) {
        _profile.update { it.copy(filterText = text) }
        viewModelScope.launch { profileRepository.setFilterText(text) }
    }

    fun onSelfDescriptionChanged(text: String) {
        _profile.update { it.copy(selfDescription = text) }
        viewModelScope.launch { profileRepository.setSelfDescription(text) }
    }

    fun onPartnerDescriptionChanged(text: String) {
        _profile.update { it.copy(partnerDescription = text) }
        viewModelScope.launch { profileRepository.setPartnerDescription(text) }
    }
}

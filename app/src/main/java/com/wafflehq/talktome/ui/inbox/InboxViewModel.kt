package com.wafflehq.talktome.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.db.InboxMessageEntity
import com.wafflehq.talktome.data.inbox.InboxRepository
import com.wafflehq.talktome.data.sync.MailboxSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inboxRepository: InboxRepository,
    private val mailboxSyncRepository: MailboxSyncRepository,
) : ViewModel() {

    val messages: StateFlow<List<InboxMessageEntity>> = inboxRepository.approvedMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun onRefresh() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            mailboxSyncRepository.sync()
            _isSyncing.value = false
        }
    }
}

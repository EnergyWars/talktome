package com.wafflehq.talktome.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wafflehq.talktome.data.db.MediatorNoteDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val mediatorNoteDao: MediatorNoteDao,
) : ViewModel() {

    fun onResetNotes() {
        viewModelScope.launch {
            mediatorNoteDao.deleteAll()
        }
    }
}

package com.pulsemessenger.android.feature.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class InvitationsViewModelFactory(
    private val repository: InvitationsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InvitationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InvitationsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

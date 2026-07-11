package com.pulsemessenger.android.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DmChatViewModelFactory(
    private val repository: DmChatRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DmChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DmChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

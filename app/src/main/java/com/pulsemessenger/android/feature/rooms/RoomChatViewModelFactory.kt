package com.pulsemessenger.android.feature.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RoomChatViewModelFactory(
    private val repository: RoomChatRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoomChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RoomChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

package com.pulsemessenger.android.feature.createroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CreateRoomViewModelFactory(
    private val repository: CreateRoomRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateRoomViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateRoomViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

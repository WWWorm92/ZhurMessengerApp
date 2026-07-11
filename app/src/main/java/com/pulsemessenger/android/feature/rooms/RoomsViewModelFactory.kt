package com.pulsemessenger.android.feature.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RoomsViewModelFactory(
    private val repository: RoomsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoomsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RoomsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

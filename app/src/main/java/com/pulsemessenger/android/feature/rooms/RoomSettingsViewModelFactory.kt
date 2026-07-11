package com.pulsemessenger.android.feature.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RoomSettingsViewModelFactory(
    private val repository: RoomSettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RoomSettingsViewModel(repository) as T
    }
}

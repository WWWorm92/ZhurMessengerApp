package com.pulsemessenger.android.feature.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DialogsViewModelFactory(
    private val repository: DialogsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DialogsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DialogsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

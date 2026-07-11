package com.pulsemessenger.android.feature.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SearchUsersViewModelFactory(
    private val repository: DialogsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchUsersViewModel(repository) as T
    }
}

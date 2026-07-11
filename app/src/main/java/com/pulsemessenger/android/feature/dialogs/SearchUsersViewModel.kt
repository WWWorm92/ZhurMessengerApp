package com.pulsemessenger.android.feature.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.DialogUserDto
import kotlinx.coroutines.launch

class SearchUsersViewModel(
    private val repository: DialogsRepository,
) : ViewModel() {
    var users by mutableStateOf<List<DialogUserDto>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun load() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.loadUsers()
                .onSuccess { users = it }
                .onFailure { error = it.message ?: "Failed to load users" }
            isLoading = false
        }
    }
}

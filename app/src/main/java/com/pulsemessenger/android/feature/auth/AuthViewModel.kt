package com.pulsemessenger.android.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.MeUserDto
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var currentUser by mutableStateOf<MeUserDto?>(null)
    var isAuthorized by mutableStateOf(false)
    var isCheckingSession by mutableStateOf(repository.hasSession())
    var registerDisplayName by mutableStateOf("")
    var isRegisterMode by mutableStateOf(false)

    init {
        if (repository.hasSession()) {
            restoreSession()
        }
    }

    fun submitLogin() {
        if (username.isBlank() || password.isBlank()) {
            error = "Enter username and password"
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.login(username.trim(), password)
                .onSuccess { user ->
                    currentUser = user
                    isAuthorized = true
                    isCheckingSession = false
                }
                .onFailure { throwable ->
                    error = throwable.message ?: "Login failed"
                }
            isLoading = false
        }
    }

    fun submitRegister() {
        if (username.isBlank() || password.isBlank() || registerDisplayName.isBlank()) {
            error = "Заполните все поля"
            return
        }
        if (password.length < 6) {
            error = "Пароль должен быть минимум 6 символов"
            return
        }
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.register(username.trim(), password, registerDisplayName.trim())
                .onSuccess { user ->
                    currentUser = user
                    isAuthorized = true
                }
                .onFailure { throwable ->
                    error = throwable.message ?: "Ошибка регистрации"
                }
            isLoading = false
        }
    }

    fun toggleRegisterMode() {
        isRegisterMode = !isRegisterMode
        error = null
        username = ""
        password = ""
        registerDisplayName = ""
    }

    fun logout() {
        repository.logout()
        currentUser = null
        isAuthorized = false
        password = ""
        isCheckingSession = false
    }

    fun updateCurrentUser(user: MeUserDto) {
        currentUser = user
    }

    private fun restoreSession() {
        isCheckingSession = true
        isLoading = true
        error = null

        viewModelScope.launch {
            while (repository.hasSession()) {
                repository.restoreSession()
                    .onSuccess { user ->
                        currentUser = user
                        isAuthorized = true
                        isCheckingSession = false
                        isLoading = false
                        return@launch
                    }
                    .onFailure {
                        currentUser = null
                        isAuthorized = false

                        if (!repository.hasSession()) {
                            isCheckingSession = false
                            isLoading = false
                            return@launch
                        }

                        error = null
                        delay(3000)
                    }
            }

            isCheckingSession = false
            isLoading = false
        }
    }
}

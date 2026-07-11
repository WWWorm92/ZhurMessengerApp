package com.pulsemessenger.android.feature.profile

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.MeUserDto
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {
    var currentUser by mutableStateOf<MeUserDto?>(null)
    var editDisplayName by mutableStateOf("")
    var isEditing by mutableStateOf(false)
    var isUploading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isChangingPassword by mutableStateOf(false)
    var passwordChangeSuccess by mutableStateOf(false)
    var passwordError by mutableStateOf<String?>(null)

    fun init(user: MeUserDto) {
        currentUser = user
        editDisplayName = user.displayName
    }

    fun beginEdit() {
        editDisplayName = currentUser?.displayName ?: ""
        isEditing = true
        error = null
    }

    fun cancelEdit() {
        isEditing = false
        editDisplayName = currentUser?.displayName ?: ""
        error = null
    }

    fun saveDisplayName() {
        val name = editDisplayName.trim()
        if (name.length < 2 || name.length > 40) {
            error = "Имя должно быть от 2 до 40 символов"
            return
        }
        if (name == currentUser?.displayName) {
            isEditing = false
            return
        }
        error = null
        viewModelScope.launch {
            repository.updateDisplayName(name)
                .onSuccess { user ->
                    currentUser = user
                    isEditing = false
                }
                .onFailure { error = it.message ?: "Ошибка сохранения" }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        isUploading = true
        error = null
        viewModelScope.launch {
            repository.uploadAvatar(context, uri)
                .onSuccess { user ->
                    currentUser = user
                    isUploading = false
                }
                .onFailure {
                    error = it.message ?: "Ошибка загрузки аватара"
                    isUploading = false
                }
        }
    }

    fun changePassword() {
        val current = currentPassword.trim()
        val new = newPassword.trim()
        val confirm = confirmPassword.trim()
        passwordError = null
        passwordChangeSuccess = false
        if (current.isEmpty()) { passwordError = "Введите текущий пароль"; return }
        if (new.isEmpty()) { passwordError = "Введите новый пароль"; return }
        if (new.length < 6) { passwordError = "Новый пароль должен быть не менее 6 символов"; return }
        if (new != confirm) { passwordError = "Пароли не совпадают"; return }
        isChangingPassword = true
        viewModelScope.launch {
            repository.changePassword(current, new)
                .onSuccess {
                    isChangingPassword = false
                    passwordChangeSuccess = true
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }
                .onFailure {
                    passwordError = it.message ?: "Ошибка смены пароля"
                    isChangingPassword = false
                }
        }
    }
}

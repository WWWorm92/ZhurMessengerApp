package com.pulsemessenger.android.feature.createroom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.RoomDto
import kotlinx.coroutines.launch

class CreateRoomViewModel(
    private val repository: CreateRoomRepository,
) : ViewModel() {
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var slug by mutableStateOf("")
    var accessType by mutableStateOf("public")
    var isCreating by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var createdRoom by mutableStateOf<RoomDto?>(null)

    fun create() {
        val roomName = name.trim()
        if (roomName.length < 2 || roomName.length > 40) {
            error = "Название должно быть от 2 до 40 символов"
            return
        }
        error = null
        isCreating = true
        val slugVal = slug.trim().take(64)
        viewModelScope.launch {
            repository.createRoom(
                name = roomName,
                accessType = accessType,
                description = description.trim().take(300),
                slug = slugVal,
            )
                .onSuccess { room ->
                    createdRoom = room
                    isCreating = false
                }
                .onFailure {
                    error = it.message ?: "Ошибка создания комнаты"
                    isCreating = false
                }
        }
    }

    fun reset() {
        name = ""
        description = ""
        slug = ""
        accessType = "public"
        error = null
        createdRoom = null
        isCreating = false
    }
}

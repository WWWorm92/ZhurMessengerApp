package com.pulsemessenger.android.feature.rooms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.network.RoomMessageDto
import kotlinx.coroutines.launch

class RoomsViewModel(
    private val repository: RoomsRepository,
) : ViewModel() {
    var rooms by mutableStateOf<List<RoomDto>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var joiningRoomId by mutableStateOf<Long?>(null)
    var joinError by mutableStateOf<String?>(null)

    fun load() {
        if (isLoading) return

        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                repository.loadRooms()
                    .onSuccess { loaded ->
                        rooms = loaded.sortedWith(
                            compareByDescending<RoomDto> { it.pinned }
                                .thenByDescending { it.lastMessageAt ?: "" }
                        )
                    }
                    .onFailure { throwable ->
                        error = throwable.message ?: "Не удалось загрузить комнаты"
                    }
            } catch (_: Throwable) {
                error = "Нет соединения с сервером"
            } finally {
                isLoading = false
            }
        }
    }

    fun joinRoom(roomId: Long) {
        joiningRoomId = roomId
        joinError = null
        viewModelScope.launch {
            repository.joinRoom(roomId)
                .onSuccess {
                    rooms = rooms.map { if (it.id == roomId) it.copy(joined = true, hasJoinRequest = false) else it }
                    load()
                }
                .onFailure { joinError = it.message ?: "Failed to join" }
            joiningRoomId = null
        }
    }

    fun togglePin(roomId: Long) {
        val room = rooms.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch {
            repository.updateRoomChatPrefs(roomId, pinned = !room.pinned)
                .onSuccess {
                    rooms = rooms.map { if (it.id == roomId) it.copy(pinned = !room.pinned) else it }
                }
        }
    }

    fun toggleMute(roomId: Long) {
        val room = rooms.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch {
            repository.updateRoomChatPrefs(roomId, muted = !room.muted)
                .onSuccess {
                    rooms = rooms.map { if (it.id == roomId) it.copy(muted = !room.muted) else it }
                }
        }
    }

    fun toggleArchive(roomId: Long) {
        val room = rooms.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch {
            repository.updateRoomChatPrefs(roomId, archived = !room.archived)
                .onSuccess {
                    rooms = rooms.map { if (it.id == roomId) it.copy(archived = !room.archived) else it }
                }
        }
    }

    fun requestJoinRoom(roomId: Long) {
        joiningRoomId = roomId
        joinError = null
        viewModelScope.launch {
            repository.requestJoinRoom(roomId)
                .onSuccess {
                    rooms = rooms.map { if (it.id == roomId) it.copy(hasJoinRequest = true) else it }
                }
                .onFailure { joinError = it.message ?: "Failed to request join" }
            joiningRoomId = null
        }
    }

    fun onRoomMessage(message: RoomMessageDto, currentUserId: Long?, activeRoomId: Long?) {
        applyRoomEvent(message, currentUserId, activeRoomId, incrementUnread = true)
    }

    fun onRoomMessageUpdate(message: RoomMessageDto) {
        applyRoomEvent(message, currentUserId = null, activeRoomId = null, incrementUnread = false)
    }

    private fun applyRoomEvent(message: RoomMessageDto, currentUserId: Long?, activeRoomId: Long?, incrementUnread: Boolean) {
        val existing = rooms.firstOrNull { it.id == message.roomId } ?: return
        val myUserId = currentUserId
        val isIncoming = myUserId != null && message.senderId != myUserId
        val nextUnread = when {
            !incrementUnread -> existing.unreadCount
            !isIncoming -> existing.unreadCount
            activeRoomId == message.roomId -> 0
            else -> existing.unreadCount + 1
        }
        val updated = existing.copy(
            lastMessage = previewText(message.content, message.type, message.fileName),
            lastMessageType = message.type,
            lastMessageAt = message.createdAt,
            unreadCount = nextUnread,
        )
        rooms = if (incrementUnread) {
            listOf(updated) + rooms.filterNot { it.id == message.roomId }
        } else {
            rooms.map { if (it.id == message.roomId) updated else it }
        }
    }

    fun markRoomOpened(roomId: Long) {
        rooms = rooms.map { room ->
            if (room.id == roomId && room.unreadCount > 0) {
                room.copy(unreadCount = 0)
            } else {
                room
            }
        }
    }

    fun removeRoom(roomId: Long) {
        rooms = rooms.filterNot { it.id == roomId }
    }

    private fun previewText(content: String, type: String, fileName: String): String {
        return when {
            content.isBlank() && type == "text" -> "Сообщение удалено"
            content.isNotBlank() -> content
            type == "file" && fileName.isNotBlank() -> "Файл: $fileName"
            type == "image" -> "Изображение"
            type == "poll" -> "Опрос"
            else -> "[$type]"
        }
    }
}

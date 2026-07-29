package com.pulsemessenger.android.feature.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.ChatPreferencesDto
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.sync.ChatPreferencesBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DialogsViewModel(
    private val repository: DialogsRepository,
) : ViewModel() {
    var users by mutableStateOf<List<DialogUserDto>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            ChatPreferencesBus.updates.collect { preferences ->
                if (preferences.scope == "dm") {
                    applyPreferences(preferences)
                }
            }
        }
    }

    private fun sortedUsers(items: List<DialogUserDto>): List<DialogUserDto> {
        return items.sortedWith(
            compareByDescending<DialogUserDto> { it.pinned }
                .thenByDescending { it.draftUpdatedAt ?: it.lastMessageAt ?: "" }
        )
    }

    private fun applyPreferences(preferences: ChatPreferencesDto) {
        users = sortedUsers(
            users.map { user ->
                if (user.id != preferences.targetId) {
                    user
                } else {
                    user.copy(
                        pinned = preferences.pinned,
                        muted = preferences.muted,
                        archived = preferences.archived,
                        muteUntil = preferences.muteUntil,
                        notificationPreview = preferences.notificationPreview,
                        wallpaper = preferences.wallpaper,
                        bubbleColor = preferences.bubbleColor,
                        saveMedia = preferences.saveMedia,
                    )
                }
            }
        )
    }

    fun togglePin(userId: Long) {
        val user = users.firstOrNull { it.id == userId } ?: return
        viewModelScope.launch {
            repository.updateDmChatPrefs(userId, pinned = !user.pinned)
                .onSuccess {
                    users = sortedUsers(users.map { if (it.id == userId) it.copy(pinned = !user.pinned) else it })
                }
        }
    }

    fun toggleMute(userId: Long) {
        val user = users.firstOrNull { it.id == userId } ?: return
        viewModelScope.launch {
            repository.updateDmChatPrefs(userId, muted = !user.muted)
                .onSuccess {
                    users = users.map { if (it.id == userId) it.copy(muted = !user.muted) else it }
                }
        }
    }

    fun clearDialog(userId: Long) {
        viewModelScope.launch {
            repository.clearDialog(userId)
                .onSuccess {
                    onDialogCleared(userId)
                }
        }
    }

    fun toggleArchive(userId: Long) {
        val user = users.firstOrNull { it.id == userId } ?: return
        viewModelScope.launch {
            repository.updateDmChatPrefs(userId, archived = !user.archived)
                .onSuccess {
                    users = users.map { if (it.id == userId) it.copy(archived = !user.archived) else it }
                }
        }
    }

    fun load() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.loadUsers() }
                .onSuccess { loaded -> users = sortedUsers(loaded) }
                .onFailure { error = it.message ?: "Failed to load dialogs" }
            isLoading = false
        }
    }

    fun onPresenceUpdate(onlineUserIds: Set<Long>) {
        if (users.isEmpty()) return
        users = users.map { user ->
            if (user.online == onlineUserIds.contains(user.id)) user
            else user.copy(online = onlineUserIds.contains(user.id))
        }
    }

    fun onDmMessage(message: DmMessageDto, currentUserId: Long?, activePeerId: Long?) {
        applyDmEvent(message, currentUserId, activePeerId, incrementUnread = true)
    }

    fun onDmMessageUpdate(message: DmMessageDto, currentUserId: Long?) {
        applyDmEvent(message, currentUserId, activePeerId = null, incrementUnread = false)
    }

    private fun applyDmEvent(
        message: DmMessageDto,
        currentUserId: Long?,
        activePeerId: Long?,
        incrementUnread: Boolean,
    ) {
        val myUserId = currentUserId ?: return
        val peerId = when (myUserId) {
            message.senderId -> message.receiverId
            message.receiverId -> message.senderId
            else -> return
        }
        val existing = users.firstOrNull { it.id == peerId } ?: return
        val isIncoming = message.senderId != myUserId
        val nextUnread = when {
            !incrementUnread -> existing.unreadCount
            !isIncoming -> existing.unreadCount
            activePeerId == peerId -> 0
            else -> existing.unreadCount + 1
        }
        val updated = existing.copy(
            lastMessage = previewText(message.content, message.type, message.fileName),
            lastFileName = message.fileName,
            lastMessageType = message.type,
            lastMessageAt = message.createdAt,
            unreadCount = nextUnread,
            draftText = if (message.senderId == myUserId) "" else existing.draftText,
            draftReplyToMessageId = if (message.senderId == myUserId) null else existing.draftReplyToMessageId,
            draftUpdatedAt = if (message.senderId == myUserId) null else existing.draftUpdatedAt,
        )
        users = if (incrementUnread) {
            sortedUsers(listOf(updated) + users.filterNot { it.id == peerId })
        } else {
            sortedUsers(users.map { if (it.id == peerId) updated else it })
        }
    }

    fun markDialogOpened(peerId: Long) {
        users = users.map { user ->
            if (user.id == peerId && user.unreadCount > 0) user.copy(unreadCount = 0) else user
        }
    }

    fun onDialogCleared(peerUserId: Long) {
        users = users.map { user ->
            if (user.id == peerUserId) {
                user.copy(
                    lastMessage = "",
                    lastFileName = "",
                    lastMessageType = "text",
                    lastMessageAt = null,
                    unreadCount = 0,
                )
            } else user
        }
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

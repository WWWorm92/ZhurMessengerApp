package com.pulsemessenger.android.feature.rooms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.JoinRequestDto
import com.pulsemessenger.android.core.network.RoomDetailResponse
import com.pulsemessenger.android.core.network.RoomMemberDto
import com.pulsemessenger.android.core.network.UpdateRoomRequest
import kotlinx.coroutines.launch

class RoomSettingsViewModel(
    private val repository: RoomSettingsRepository,
) : ViewModel() {
    var roomDetail by mutableStateOf<RoomDetailResponse?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var editName by mutableStateOf("")
    var editDescription by mutableStateOf("")
    var editAccessType by mutableStateOf("public")
    var editSlug by mutableStateOf("")
    var isSaving by mutableStateOf(false)
    var saveSuccess by mutableStateOf(false)

    var inviteCandidates by mutableStateOf<List<DialogUserDto>>(emptyList())
    var showInviteSheet by mutableStateOf(false)
    var isInviting by mutableStateOf(false)

    var removingMemberId by mutableStateOf<Long?>(null)
    var updatingMemberId by mutableStateOf<Long?>(null)

    var isLeaving by mutableStateOf(false)
    var isDeleting by mutableStateOf(false)

    var isUploadingAvatar by mutableStateOf(false)

    var joinRequests by mutableStateOf<List<JoinRequestDto>>(emptyList())
    var isLoadingRequests by mutableStateOf(false)

    fun load(roomId: Long) {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.loadRoomDetail(roomId)
                .onSuccess { detail ->
                    roomDetail = detail
                    editName = detail.name
                    editDescription = detail.description
                    editAccessType = detail.accessType
                    editSlug = detail.slug
                }
                .onFailure { error = it.message ?: "Failed to load room" }
            isLoading = false
        }
    }

    fun loadJoinRequests(roomId: Long) {
        if (isLoadingRequests) return
        isLoadingRequests = true
        viewModelScope.launch {
            repository.loadJoinRequests(roomId)
                .onSuccess { joinRequests = it }
                .onFailure { error = it.message }
            isLoadingRequests = false
        }
    }

    fun approveRequest(roomId: Long, userId: Long) {
        viewModelScope.launch {
            repository.approveJoinRequest(roomId, userId)
                .onSuccess {
                    joinRequests = joinRequests.filterNot { it.userId == userId }
                    load(roomId)
                }
                .onFailure { error = it.message }
        }
    }

    fun declineRequest(roomId: Long, userId: Long) {
        viewModelScope.launch {
            repository.declineJoinRequest(roomId, userId)
                .onSuccess {
                    joinRequests = joinRequests.filterNot { it.userId == userId }
                }
                .onFailure { error = it.message }
        }
    }

    fun uploadAvatar(context: android.content.Context, uri: android.net.Uri) {
        isUploadingAvatar = true
        error = null
        viewModelScope.launch {
            repository.uploadRoomAvatar(context, uri)
                .onSuccess { avatarUrl ->
                    roomDetail = roomDetail?.copy(avatarUrl = avatarUrl)
                }
                .onFailure { error = it.message ?: "Failed to upload avatar" }
            isUploadingAvatar = false
        }
    }

    fun saveSettings(roomId: Long) {
        val name = editName.trim()
        if (name.length < 2 || name.length > 40) { error = "Название должно быть от 2 до 40 символов"; return }
        saveSuccess = false
        error = null
        isSaving = true
        viewModelScope.launch {
            repository.updateRoom(roomId, UpdateRoomRequest(
                name = name,
                description = editDescription.trim(),
                avatarUrl = roomDetail?.avatarUrl,
                accessType = editAccessType,
                slug = editSlug.trim().ifBlank { null },
            )).onSuccess {
                saveSuccess = true
                roomDetail = roomDetail?.copy(name = name, description = editDescription.trim(), accessType = editAccessType, slug = editSlug.trim())
            }.onFailure { error = it.message ?: "Failed to save" }
            isSaving = false
        }
    }

    fun loadInviteCandidates(roomId: Long) {
        viewModelScope.launch {
            repository.loadInviteCandidates(roomId)
                .onSuccess { inviteCandidates = it }
        }
    }

    fun inviteUser(roomId: Long, userId: Long) {
        isInviting = true
        viewModelScope.launch {
            repository.inviteUser(roomId, userId)
                .onSuccess {
                    showInviteSheet = false
                    load(roomId)
                }
                .onFailure { error = it.message }
            isInviting = false
        }
    }

    fun removeMember(roomId: Long, userId: Long) {
        removingMemberId = userId
        viewModelScope.launch {
            repository.removeMember(roomId, userId)
                .onSuccess { roomDetail = roomDetail?.copy(members = roomDetail?.members?.filter { it.id != userId }.orEmpty()) }
                .onFailure { error = it.message }
            removingMemberId = null
        }
    }

    fun toggleMemberRole(roomId: Long, member: RoomMemberDto) {
        val nextRole = if (member.role == "admin") "member" else "admin"
        updatingMemberId = member.id
        viewModelScope.launch {
            repository.updateMember(roomId, member.id, nextRole)
                .onSuccess {
                    roomDetail = roomDetail?.copy(members = roomDetail?.members?.map {
                        if (it.id == member.id) it.copy(role = nextRole) else it
                    }.orEmpty())
                }
                .onFailure { error = it.message }
            updatingMemberId = null
        }
    }

    fun leaveRoom(roomId: Long, onLeft: () -> Unit) {
        isLeaving = true
        viewModelScope.launch {
            repository.leaveRoom(roomId)
                .onSuccess { onLeft() }
                .onFailure { error = it.message }
            isLeaving = false
        }
    }

    fun deleteRoom(roomId: Long, onDeleted: () -> Unit) {
        isDeleting = true
        viewModelScope.launch {
            repository.deleteRoom(roomId)
                .onSuccess { onDeleted() }
                .onFailure { error = it.message }
            isDeleting = false
        }
    }
}
